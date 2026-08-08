package com.automacropro.engine;

import com.automacropro.model.MouseActionConfig;
import com.automacropro.model.MouseButtonType;
import com.automacropro.model.PositionMode;
import com.automacropro.util.AppLogger;
import com.automacropro.util.ScreenCoords;

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

    /**
     * Humanizer jitter source. One instance per executor and only ever touched
     * by that executor's single worker thread, so the unsynchronized
     * {@code java.util.Random} is safe here and avoids ThreadLocalRandom's
     * shared state. Not seeded deliberately - unpredictability is the feature.
     */
    private final java.util.Random random = new java.util.Random();

    public RobotExecutor() throws AWTException {
        robot = new Robot();
        // We manage all timing ourselves via PreciseTimer, so Robot should not
        // add its own implicit delay on top of every generated event.
        robot.setAutoDelay(0);
        robot.setAutoWaitForIdle(false);
    }

    /**
     * The cursor position in the app's storage space (physical pixels), so it
     * round-trips correctly with coordinates captured by Pick Location.
     * {@code MouseInfo} itself reports logical, DPI-scaled coordinates.
     */
    public Point getCurrentCursor() {
        return ScreenCoords.toStoredSpace(MouseInfo.getPointerInfo().getLocation());
    }

    /**
     * The single place a coordinate leaves the app and reaches the OS.
     *
     * Every stored coordinate is physical (see {@link ScreenCoords}), but
     * {@code Robot} steers in logical, DPI-scaled space - feeding it physical
     * coordinates landed clicks 240x135 px off target on a 125% monitor while
     * being exact on the 100% primary. Routing all six move sites through here
     * means the conversion cannot be forgotten at one of them.
     */
    private void moveTo(int physicalX, int physicalY) {
        Point logical = ScreenCoords.toRobotSpace(physicalX, physicalY);
        robot.mouseMove(logical.x, logical.y);
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
        Point base = cfg.getPositionMode() == PositionMode.CURRENT_CURSOR
                ? getCurrentCursor()
                : new Point(cfg.getX(), cfg.getY());
        return scatter(base, cfg.getPositionJitterPx());
    }

    /**
     * Humanizer: displaces a target uniformly within a disc of {@code radius}
     * px. Returns the point untouched when the radius is 0 (the default), so
     * exact targeting stays exact.
     *
     * Samples with {@code sqrt(u)} rather than a plain uniform radius: picking
     * the radius uniformly would concentrate points near the centre, since the
     * area of an annulus grows with r. That produces a visibly tighter cluster
     * than a human hand and defeats the point of the feature.
     *
     * Runs in physical (stored) coordinate space, i.e. before {@code moveTo}
     * converts to Robot's logical space, so a 5px radius is 5 real pixels on
     * every monitor regardless of its DPI scale.
     */
    private Point scatter(Point base, int radius) {
        if (radius <= 0) {
            return base;
        }
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = radius * Math.sqrt(random.nextDouble());
        return new Point(
                base.x + (int) Math.round(Math.cos(angle) * distance),
                base.y + (int) Math.round(Math.sin(angle) * distance));
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
                // Scatter the destination independently of the start, so a
                // jittered drag varies at both ends rather than sliding a
                // rigid vector around.
                Point end = scatter(new Point(cfg.getDragToX(), cfg.getDragToY()), cfg.getPositionJitterPx());
                drag(start, end, cfg, mask, abortCondition);
                break;
            case HOLD:
                hold(start, mask, cfg.getHoldDurationMs(), abortCondition);
                break;
            default:
                AppLogger.warn("ClickMode tidak dikenal: " + cfg.getClickMode(), null);
        }
    }

    /**
     * Rotates the mouse wheel. {@code amount} follows Robot's convention:
     * negative scrolls up (away from the user), positive scrolls down. There is
     * no press/release pair to leak, so no try/finally is needed here.
     */
    public void scroll(int amount) {
        if (amount != 0) {
            robot.mouseWheel(amount);
        }
    }

    private void click(Point p, int mask) {
        moveTo(p.x, p.y);
        robot.mousePress(mask);
        robot.mouseRelease(mask);
    }

    /**
     * Mouse Click - HOLD: press, hold for {@code holdMs}, release.
     * The release is in a finally block so the button is never left "stuck"
     * pressed at the OS level if the failsafe/Stop fires mid-hold.
     */
    private void hold(Point p, int mask, long holdMs, BooleanSupplier abortCondition) {
        moveTo(p.x, p.y);
        robot.mousePress(mask);
        try {
            PreciseTimer.sleep(holdMs, abortCondition);
        } finally {
            robot.mouseRelease(mask);
        }
    }

    private void drag(Point from, Point to, MouseActionConfig cfg, int mask, BooleanSupplier abortCondition) {
        moveTo(from.x, from.y);
        robot.mousePress(mask);
        try {
            switch (cfg.getDragStyle()) {
                case INSTANT:
                    moveTo(to.x, to.y);
                    break;
                case SMOOTH:
                default: {
                    int steps = Math.max(2, cfg.getDragSteps());
                    long stepDelay = Math.max(1, cfg.getDragDurationMs() / steps);
                    for (int i = 1; i <= steps; i++) {
                        if (abortCondition.getAsBoolean()) {
                            break; // stop moving early; release still happens in finally
                        }
                        // Interpolate in physical space (both endpoints are stored
                        // physical), then convert each waypoint - so a drag that
                        // crosses between monitors of different scale stays straight.
                        int ix = from.x + (to.x - from.x) * i / steps;
                        int iy = from.y + (to.y - from.y) * i / steps;
                        moveTo(ix, iy);
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
