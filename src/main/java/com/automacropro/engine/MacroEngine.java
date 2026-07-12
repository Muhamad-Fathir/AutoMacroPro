package com.automacropro.engine;

import com.automacropro.model.LoopMode;
import com.automacropro.model.MacroProject;
import com.automacropro.model.MacroStep;
import com.automacropro.util.AppLogger;

import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * The Macro Sequencer's Execution Loop: walks {@link MacroProject#getSteps()}
 * in order on a dedicated background thread, repeating per {@link LoopMode}.
 *
 * Note on the new Mouse "Hold Click" feature: this class needed <b>no</b>
 * special-case branch for it. HOLD is just another {@code ClickMode} inside
 * {@code MouseActionConfig}, so a MOUSE step already routes to it through
 * {@code RobotExecutor.performMouseAction()} below exactly like SINGLE /
 * DOUBLE / DRAG do. That is the payoff of modelling Hold as a click mode
 * instead of a brand new action type.
 */
public class MacroEngine {

    public enum StopReason { USER_STOP, COMPLETED_ONCE, FAILSAFE, ERROR, EMPTY_PROJECT }

    public interface Listener {
        void onStarted();
        void onFinished(long stepsExecuted, int loopsCompleted, StopReason reason);
    }

    private final RobotExecutor executor;
    private final Listener listener;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private volatile boolean failsafeTriggered = false;
    private Thread worker;

    public MacroEngine(Listener listener) throws AWTException {
        this.executor = new RobotExecutor();
        this.listener = listener;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    /** Starts a fresh run of {@code project}. No-op if already running or the sequence is empty. */
    public void start(MacroProject project) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        if (project == null || project.getSteps().isEmpty()) {
            running.set(false);
            AppLogger.warn("Macro tidak dijalankan: sequence kosong", null);
            final Listener l = listener;
            SwingUtilities.invokeLater(() -> {
                if (l != null) {
                    l.onFinished(0, 0, StopReason.EMPTY_PROJECT);
                }
            });
            return;
        }
        paused.set(false);
        stopRequested.set(false);
        failsafeTriggered = false;
        FailsafeMonitor.noteEngineStarted();
        worker = new Thread(() -> runLoop(project), "MacroEngine-Worker");
        worker.setDaemon(true);
        worker.start();
        notifyStarted();
    }

    public void stop() {
        stopRequested.set(true);
        paused.set(false);
        if (worker != null) {
            worker.interrupt();
        }
    }

    public void togglePause() {
        if (running.get()) {
            paused.set(!paused.get());
        }
    }

    private BooleanSupplier abortCondition() {
        return () -> {
            if (stopRequested.get()) {
                return true;
            }
            if (FailsafeMonitor.isCursorInCorner()) {
                failsafeTriggered = true;
                stopRequested.set(true);
                return true;
            }
            return false;
        };
    }

    private void runLoop(MacroProject project) {
        long stepsExecuted = 0;
        int loopsCompleted = 0;
        StopReason reason = StopReason.USER_STOP;
        BooleanSupplier abort = abortCondition();
        try {
            outer:
            do {
                for (MacroStep step : project.getSteps()) {
                    waitWhilePaused(abort);
                    if (abort.getAsBoolean()) {
                        break outer;
                    }

                    try {
                        executeStep(step, abort);
                    } catch (RuntimeException ex) {
                        AppLogger.error("Macro: step gagal dieksekusi (" + step.describe() + ")", ex);
                        reason = StopReason.ERROR;
                        break outer;
                    }
                    stepsExecuted++;

                    if (abort.getAsBoolean()) {
                        break outer;
                    }

                    // Tiny CPU-friendly yield between steps. Also matters for sequences
                    // made only of instant actions: without any wait at all the failsafe
                    // would only ever be re-checked once per full lap of the list.
                    PreciseTimer.sleep(1, abort);
                }
                loopsCompleted++;
                if (project.getLoopMode() == LoopMode.ONCE) {
                    reason = StopReason.COMPLETED_ONCE;
                    break;
                }
            } while (!abort.getAsBoolean());
        } finally {
            if (reason == StopReason.USER_STOP && failsafeTriggered) {
                reason = StopReason.FAILSAFE;
            }
            running.set(false);
            paused.set(false);
            final long finalSteps = stepsExecuted;
            final int finalLoops = loopsCompleted;
            final StopReason finalReason = reason;
            SwingUtilities.invokeLater(() -> {
                if (listener != null) {
                    listener.onFinished(finalSteps, finalLoops, finalReason);
                }
            });
        }
    }

    /**
     * Routes a single step to the executor. MOUSE covers SINGLE / DOUBLE /
     * DRAG / HOLD uniformly (see class javadoc); KEYBOARD presses a combo
     * via {@code RobotExecutor.pressKeyCombo}; DELAY just waits.
     */
    private void executeStep(MacroStep step, BooleanSupplier abort) {
        switch (step.getType()) {
            case MOUSE:
                executor.performMouseAction(step.getMouseConfig(), abort);
                break;
            case KEYBOARD:
                executor.pressKeyCombo(step.getKeyConfig().getVkCodes(), step.getKeyConfig().getHoldMs(), abort);
                break;
            case DELAY:
                PreciseTimer.sleep(step.getDelayMs(), abort);
                break;
            default:
                AppLogger.warn("ActionType tidak dikenal: " + step.getType(), null);
        }
    }

    private void waitWhilePaused(BooleanSupplier abort) {
        while (paused.get() && !abort.getAsBoolean()) {
            PreciseTimer.sleep(50, abort);
        }
    }

    private void notifyStarted() {
        SwingUtilities.invokeLater(() -> {
            if (listener != null) {
                listener.onStarted();
            }
        });
    }
}
