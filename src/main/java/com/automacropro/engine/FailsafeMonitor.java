package com.automacropro.engine;

import com.automacropro.util.ScreenCoords;

import javax.swing.SwingUtilities;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Corner-of-screen killswitch check, polled from inside the engines' own
 * loops/waits (see {@code PreciseTimer}). Three things were added after a
 * real bug report that the app appeared to "lock up immediately" on Start:
 *
 * <ol>
 *   <li><b>Startup grace period</b> ({@link #noteEngineStarted()}): the
 *       cursor may simply be resting near a corner at the exact moment
 *       Start is pressed (e.g. right after clicking a window control
 *       button) - this is almost certainly the real root cause of the
 *       "always triggers instantly" symptom, not a flaw in the concept of
 *       a failsafe itself. No checks happen for a short window after
 *       start, giving the user a moment to move the mouse if needed.</li>
 *   <li><b>Dwell time</b>: the cursor must sit in the corner continuously
 *       for {@link #DWELL_MS} before it counts, so a single transient
 *       sample (cursor merely passing through a corner pixel while moving
 *       across the screen) cannot kill a run.</li>
 *   <li><b>Explicit enable/disable</b> ({@link #setEnabled(boolean)}): the
 *       user has full control via a UI checkbox instead of the feature
 *       being unconditionally forced on - or removed outright and lost as
 *       a capability altogether.</li>
 *   <li><b>User feedback</b> ({@link #setAlertListener}): a beep plus an
 *       optional UI flash, so a trigger is never silent. Without it a
 *       failsafe stop is indistinguishable from a crash or a missed hotkey,
 *       which is exactly how "it just stopped for no reason" bug reports
 *       get filed.</li>
 * </ol>
 */
public final class FailsafeMonitor {

    private static final int THRESHOLD_PX = 5;
    private static final long DWELL_MS = 200;
    private static final long GRACE_PERIOD_MS = 800;

    private static volatile boolean enabled = true;
    private static volatile long cornerSinceMs = -1;
    private static volatile long graceUntilMs = 0;

    /**
     * One-shot latch for the alert.
     *
     * {@link #isCursorInCorner()} is polled from inside {@code PreciseTimer}'s
     * spin-wait - potentially thousands of times per second - and it keeps
     * returning true for as long as the cursor stays in the corner. Beeping on
     * every true would fire a machine-gun burst of alerts for a single trigger.
     * This latch is set exactly once per triggering, by whichever engine thread
     * observes it first, and rearmed in {@link #noteEngineStarted()}.
     */
    private static final AtomicBoolean alertFired = new AtomicBoolean(false);

    /**
     * A list, not a single slot: every module panel registers its own flash,
     * and with one slot whichever panel was constructed last would silently
     * disable the other one's feedback. Copy-on-write because it is read from
     * the engine threads and written from the EDT at construction time.
     */
    private static final List<Runnable> alertListeners = new CopyOnWriteArrayList<>();

    private FailsafeMonitor() {
    }

    /**
     * Registers a UI-side reaction to a failsafe trigger (e.g. flashing a
     * status line). Invoked on the EDT, never on the engine thread that
     * detected the trigger.
     */
    public static void addAlertListener(Runnable listener) {
        if (listener != null) {
            alertListeners.add(listener);
        }
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        cornerSinceMs = -1;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Call exactly once when an engine run starts; arms the startup grace period. */
    public static void noteEngineStarted() {
        graceUntilMs = System.currentTimeMillis() + GRACE_PERIOD_MS;
        cornerSinceMs = -1;
        alertFired.set(false); // rearm so the next trigger alerts again
    }

    public static boolean isCursorInCorner() {
        if (!enabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < graceUntilMs) {
            return false; // still inside the startup grace period
        }
        // Read the pointer only after the cheap early-outs above: this runs on
        // PreciseTimer's spin path, and getPointerInfo() is a native call.
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) {
            return false; // headless / no pointer available - never block on this
        }
        return evaluate(info.getLocation(), now);
    }

    /**
     * The same decision as {@link #isCursorInCorner()} for an explicitly given
     * cursor position.
     *
     * Exists so the dwell-and-latch state machine can be exercised
     * deterministically. The alternative - parking the real cursor in a corner
     * with {@code Robot} - both hijacks the user's mouse for seconds at a time
     * and is inherently flaky: any physical hand movement during the dwell
     * window moves the cursor out of the corner and the observation is lost.
     */
    public static boolean isCursorInCornerAt(Point cursor) {
        if (!enabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < graceUntilMs) {
            return false;
        }
        return evaluate(cursor, now);
    }

    /** Dwell timing + one-shot alert, shared by both entry points above. */
    private static boolean evaluate(Point cursor, long now) {
        if (!isInScreenCorner(cursor)) {
            cornerSinceMs = -1;
            return false;
        }
        if (cornerSinceMs < 0) {
            cornerSinceMs = now; // just entered - start the dwell-time clock
            return false;
        }
        if ((now - cornerSinceMs) < DWELL_MS) {
            return false;
        }
        fireAlertOnce();
        return true;
    }

    /**
     * Beeps and notifies the UI, at most once per run. Deliberately cheap and
     * fully non-blocking: this runs on the engine's worker thread in the middle
     * of a timing-critical loop, so it must never sleep, take a lock, or paint.
     * {@code beep()} is asynchronous, and the listener is handed to the EDT.
     */
    private static void fireAlertOnce() {
        if (!alertFired.compareAndSet(false, true)) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (Throwable ignored) {
            // Headless or no audio device - never let feedback break the stop itself.
        }
        for (Runnable listener : alertListeners) {
            SwingUtilities.invokeLater(listener);
        }
    }

    /**
     * True only when {@code p} is in a corner <b>of the screen it is actually
     * on</b>.
     *
     * The containment lookup is the whole fix for the false-positive bug. This
     * used to test the cursor against every screen's edges in turn, which is
     * wrong on any multi-monitor desktop and badly wrong on a mixed-DPI one:
     * {@code getBounds()} reports a physical origin with a logical size, so on
     * the measured setup the 125% secondary claimed the rect
     * {@code [-1920,0 .. -384,864]} - and its "bottom-right corner" test
     * therefore matched any point with {@code x >= -390 && y >= 858}, i.e. the
     * entire bottom third of the 2560x1440 primary. A grid scan found 71 of 242
     * sampled points triggering the failsafe while nowhere near a real corner.
     *
     * Resolving the screen first makes each edge test meaningful, and keeps the
     * comparison inside one space: {@code MouseInfo} and {@code getBounds()}
     * are both logical and mutually consistent, so no DPI conversion is needed
     * here (that belongs at the Robot boundary - see {@code ScreenCoords}).
     */
    private static boolean isInScreenCorner(Point p) {
        if (p == null) {
            return false;
        }
        Rectangle bounds = ScreenCoords.logicalBoundsContaining(p);
        if (bounds == null) {
            // No screen claims this point - a transient during a resolution or
            // monitor change. Never guess a corner from it.
            return false;
        }
        boolean nearLeft = p.x <= bounds.x + THRESHOLD_PX;
        boolean nearRight = p.x >= bounds.x + bounds.width - 1 - THRESHOLD_PX;
        boolean nearTop = p.y <= bounds.y + THRESHOLD_PX;
        boolean nearBottom = p.y >= bounds.y + bounds.height - 1 - THRESHOLD_PX;
        return (nearLeft || nearRight) && (nearTop || nearBottom);
    }
}
