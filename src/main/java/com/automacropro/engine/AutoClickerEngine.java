package com.automacropro.engine;

import com.automacropro.model.AutoClickerSettings;
import com.automacropro.model.ClickLimitMode;
import com.automacropro.util.AppLogger;

import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Runs the Autoclicker module on a single dedicated background thread, so
 * the EDT is never blocked - even at a 1ms interval. Per the confirmed UI
 * design, the click counter is intentionally NOT pushed to the UI on every
 * click (that would flood the EDT at high speed); it is accumulated as a
 * plain local variable on this worker thread and reported back exactly once,
 * when the run finishes, via {@link Listener#onFinished}.
 */
public class AutoClickerEngine {

    public enum StopReason { USER_STOP, LIMIT_REACHED, FAILSAFE, ERROR }

    public interface Listener {
        void onStarted();
        void onFinished(long totalClicks, StopReason reason);
    }

    private final RobotExecutor executor;
    private final Listener listener;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private volatile boolean failsafeTriggered = false;
    private Thread worker;

    public AutoClickerEngine(Listener listener) throws AWTException {
        this.executor = new RobotExecutor();
        this.listener = listener;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    /** Starts a fresh run. No-op if already running. Safe to call from the EDT (button/hotkey handler). */
    public void start(AutoClickerSettings settings) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        paused.set(false);
        stopRequested.set(false);
        failsafeTriggered = false;
        FailsafeMonitor.noteEngineStarted();
        worker = new Thread(() -> runLoop(settings), "AutoClickerEngine-Worker");
        worker.setDaemon(true);
        worker.start();
        notifyStarted();
    }

    /** Requests an immediate stop. Safe to call from the EDT or a hotkey callback. */
    public void stop() {
        stopRequested.set(true);
        paused.set(false); // wake up a paused loop so it can observe the stop
        if (worker != null) {
            worker.interrupt();
        }
    }

    /** Pause/resume an already running loop. No-op if not running. */
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

    private void runLoop(AutoClickerSettings settings) {
        long clicks = 0;
        StopReason reason = StopReason.USER_STOP;
        BooleanSupplier abort = abortCondition();
        try {
            while (!abort.getAsBoolean()) {
                waitWhilePaused(abort);
                if (abort.getAsBoolean()) {
                    break;
                }

                try {
                    executor.performMouseAction(settings.getMouseConfig(), abort);
                } catch (RuntimeException ex) {
                    AppLogger.error("Autoclicker: aksi klik gagal dieksekusi, menghentikan run", ex);
                    reason = StopReason.ERROR;
                    break;
                }
                clicks++;

                if (settings.getLimitMode() == ClickLimitMode.FIXED && clicks >= settings.getFixedClickCount()) {
                    reason = StopReason.LIMIT_REACHED;
                    break;
                }

                boolean completedWait = PreciseTimer.sleep(settings.getIntervalMs(), abort);
                if (!completedWait) {
                    break; // reason resolved below from failsafeTriggered
                }
            }
        } finally {
            if (reason == StopReason.USER_STOP && failsafeTriggered) {
                reason = StopReason.FAILSAFE;
            }
            running.set(false);
            paused.set(false);
            final long finalClicks = clicks;
            final StopReason finalReason = reason;
            SwingUtilities.invokeLater(() -> {
                if (listener != null) {
                    listener.onFinished(finalClicks, finalReason);
                }
            });
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
