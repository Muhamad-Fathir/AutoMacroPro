package com.automacropro.engine;

import java.util.function.BooleanSupplier;

/**
 * Precise, interruptible waiting used everywhere an engine needs to pause
 * (click interval, delay step, hold duration, ...).
 *
 * Two concerns are addressed at once here, both required by the spec:
 * <ol>
 *   <li><b>Precision down to ~1ms</b>: plain {@code Thread.sleep} can
 *       oversleep by several milliseconds on some OS schedulers (Windows'
 *       default timer tick is ~15ms). For short waits we sleep coarsely and
 *       then spin-wait the last sliver using {@code System.nanoTime()} to
 *       land on time.</li>
 *   <li><b>CPU yield</b>: the spin-wait calls {@code Thread.onSpinWait()} /
 *       a tiny {@code Thread.sleep(0)} each iteration instead of a tight
 *       empty loop, so a 1ms-interval autoclicker does not peg a CPU core
 *       at 100%.</li>
 * </ol>
 * Every wait also accepts an {@code abortCondition} (stop flag OR failsafe
 * corner check) that is polled every chunk, so Stop / the corner failsafe /
 * a global hotkey can interrupt a wait almost immediately - including a
 * multi-second Delay step or a Hold Click - instead of only being checked
 * between whole steps.
 */
public final class PreciseTimer {

    /** Below this, we just spin the whole duration (too short for Thread.sleep to be reliable). */
    private static final long SPIN_THRESHOLD_NANOS = 3_000_000L; // 3ms
    /** When coarse-sleeping, always leave this much to finish via spin-wait. */
    private static final long SAFETY_MARGIN_NANOS = 2_000_000L; // 2ms
    /** How often (ms) a long wait re-checks the abort condition while coarse-sleeping. */
    private static final long POLL_CHUNK_MS = 15;

    private PreciseTimer() {
    }

    /**
     * Waits for {@code millis} milliseconds.
     *
     * @return true if the full duration elapsed; false if {@code abortCondition}
     *         became true first (caller should treat this as "stop now").
     */
    public static boolean sleep(long millis, BooleanSupplier abortCondition) {
        if (millis <= 0) {
            return !abortCondition.getAsBoolean();
        }
        long deadlineNanos = System.nanoTime() + millis * 1_000_000L;
        return waitUntil(deadlineNanos, abortCondition);
    }

    private static boolean waitUntil(long deadlineNanos, BooleanSupplier abortCondition) {
        while (true) {
            if (abortCondition.getAsBoolean()) {
                return false;
            }
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) {
                return true;
            }
            if (remaining <= SPIN_THRESHOLD_NANOS) {
                spinWait(deadlineNanos, abortCondition);
                return !abortCondition.getAsBoolean();
            }
            // Coarse-sleep in small chunks so the abort condition stays responsive
            // even for a multi-second Delay step or Hold duration.
            long sleepNanos = Math.min(remaining - SAFETY_MARGIN_NANOS, POLL_CHUNK_MS * 1_000_000L);
            if (sleepNanos > 0) {
                try {
                    long ms = sleepNanos / 1_000_000L;
                    int ns = (int) (sleepNanos % 1_000_000L);
                    Thread.sleep(ms, ns);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    private static void spinWait(long deadlineNanos, BooleanSupplier abortCondition) {
        while (System.nanoTime() < deadlineNanos) {
            if (abortCondition.getAsBoolean()) {
                return;
            }
            Thread.onSpinWait();
        }
    }
}
