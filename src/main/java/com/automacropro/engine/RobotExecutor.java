package com.automacropro.engine;

import com.automacropro.model.MouseActionConfig;
import com.automacropro.model.MouseButtonType;
import com.automacropro.model.PositionMode;
import com.automacropro.util.AppLogger;

import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The only class in this app that touches {@code java.awt.Robot} directly.
 * Every method here runs on a background worker thread (never the EDT - see
 * {@code AutoClickerEngine}/{@code MacroEngine}), and every gesture that has
 * a "press ... wait ... release" shape (DRAG, HOLD, keyboard combos) is
 * wrapped in try/finally so the button or key is <b>always</b> released,
 * even if the failsafe or Stop aborts the wait early. Leaving a button or
 * modifier key physically "stuck down" at the OS level is exactly the kind
 * of glitch this guarantee exists to prevent.
 */
public class RobotExecutor {

    private static final long DOUBLE_CLICK_GAP_MS = 50;

    private final Robot robot;

    public RobotExecutor() throws AWTException {
        robot = new Robot();
        // We manage all timing ourselves via PreciseTimer, so Robot should not
        // add its own implicit delay on top of every generated event.
        robot.setAutoDelay(0);
        robot.setAutoWaitForIdle(false);
    }

    public Point getCurrentCursor() {
        return MouseInfo.getPointerInfo().getLocation();
    }

    private static int buttonMask(MouseButtonType button) {
        switch (button) {
            case RIGHT:
                return InputEvent.BUTTON3_DOWN_MASK;
            case MIDDLE:
                return InputEvent.BUTTON2_DOWN_MASK;
            default:
                return InputEvent.BUTTON1_DOWN_MASK;
        }
    }

    private Point resolveStartPoint(MouseActionConfig cfg) {
        if (cfg.getPositionMode() == PositionMode.CURRENT_CURSOR) {
            return getCurrentCursor();
        }
        return new Point(cfg.getX(), cfg.getY());
    }

    /**
     * Executes one mouse action: SINGLE / DOUBLE / DRAG / HOLD, as configured.
     *
     * @param abortCondition polled continuously during DRAG(smooth) and HOLD
     *                       so the corner-failsafe or a Stop hotkey can cut
     *                       the gesture short instantly; the button is
     *                       guaranteed to be released regardless.
     */
    public void performMouseAction(MouseActionConfig cfg, BooleanSupplier abortCondition) {
        Point start = resolveStartPoint(cfg);
        int mask = buttonMask(cfg.getButton());
        switch (cfg.getClickMode()) {
            case SINGLE:
                click(start, mask);
                break;
            case DOUBLE:
                click(start, mask);
                PreciseTimer.sleep(DOUBLE_CLICK_GAP_MS, abortCondition);
                click(start, mask);
                break;
            case DRAG:
                drag(start, new Point(cfg.getDragToX(), cfg.getDragToY()), cfg, mask, abortCondition);
                break;
            case HOLD:
                hold(start, mask, cfg.getHoldDurationMs(), abortCondition);
                break;
            default:
                AppLogger.warn("ClickMode tidak dikenal: " + cfg.getClickMode(), null);
        }
    }

    private void click(Point p, int mask) {
        robot.mouseMove(p.x, p.y);
        robot.mousePress(mask);
        robot.mouseRelease(mask);
    }

    /**
     * Mouse Click - HOLD: press, hold for {@code holdMs}, release.
     * The release is in a finally block so the button is never left "stuck"
     * pressed at the OS level if the failsafe/Stop fires mid-hold.
     */
    private void hold(Point p, int mask, long holdMs, BooleanSupplier abortCondition) {
        robot.mouseMove(p.x, p.y);
        robot.mousePress(mask);
        try {
            PreciseTimer.sleep(holdMs, abortCondition);
        } finally {
            robot.mouseRelease(mask);
        }
    }

    private void drag(Point from, Point to, MouseActionConfig cfg, int mask, BooleanSupplier abortCondition) {
        robot.mouseMove(from.x, from.y);
        robot.mousePress(mask);
        try {
            switch (cfg.getDragStyle()) {
                case INSTANT:
                    robot.mouseMove(to.x, to.y);
                    break;
                case SMOOTH:
                default: {
                    int steps = Math.max(2, cfg.getDragSteps());
                    long stepDelay = Math.max(1, cfg.getDragDurationMs() / steps);
                    for (int i = 1; i <= steps; i++) {
                        if (abortCondition.getAsBoolean()) {
                            break; // stop moving early; release still happens in finally
                        }
                        int ix = from.x + (to.x - from.x) * i / steps;
                        int iy = from.y + (to.y - from.y) * i / steps;
                        robot.mouseMove(ix, iy);
                        PreciseTimer.sleep(stepDelay, abortCondition);
                    }
                    break;
                }
            }
        } finally {
            robot.mouseRelease(mask);
        }
    }

    /**
     * Presses every code in {@code vkCodes} (AWT VK_* codes) in order, holds
     * for {@code holdMs}, then releases all of them in reverse order. Wrapped
     * so that an abort mid-hold still releases every key that was actually
     * pressed - a "stuck" Ctrl/Shift/Alt is just as undesirable as a stuck
     * mouse button.
     */
    public void pressKeyCombo(List<Integer> vkCodes, long holdMs, BooleanSupplier abortCondition) {
        if (vkCodes == null || vkCodes.isEmpty()) {
            return;
        }
        int pressedCount = 0;
        try {
            for (int code : vkCodes) {
                robot.keyPress(code);
                pressedCount++;
            }
            PreciseTimer.sleep(holdMs, abortCondition);
        } finally {
            for (int i = pressedCount - 1; i >= 0; i--) {
                try {
                    robot.keyRelease(vkCodes.get(i));
                } catch (RuntimeException ex) {
                    AppLogger.warn("Gagal melepas tombol VK=" + vkCodes.get(i), ex);
                }
            }
        }
    }
}
