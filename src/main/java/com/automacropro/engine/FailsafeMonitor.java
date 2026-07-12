package com.automacropro.engine;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;

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
 * </ol>
 */
public final class FailsafeMonitor {

    private static final int THRESHOLD_PX = 5;
    private static final long DWELL_MS = 200;
    private static final long GRACE_PERIOD_MS = 800;

    private static volatile boolean enabled = true;
    private static volatile long cornerSinceMs = -1;
    private static volatile long graceUntilMs = 0;

    private FailsafeMonitor() {
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
    }

    public static boolean isCursorInCorner() {
        if (!enabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < graceUntilMs) {
            return false; // still inside the startup grace period
        }
        boolean inCornerNow = checkRawCornerPosition();
        if (!inCornerNow) {
            cornerSinceMs = -1;
            return false;
        }
        if (cornerSinceMs < 0) {
            cornerSinceMs = now; // just entered - start the dwell-time clock
            return false;
        }
        return (now - cornerSinceMs) >= DWELL_MS;
    }

    private static boolean checkRawCornerPosition() {
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) {
            return false; // headless / no pointer available - never block on this
        }
        Point p = info.getLocation();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            boolean nearLeft = p.x <= bounds.x + THRESHOLD_PX;
            boolean nearRight = p.x >= bounds.x + bounds.width - 1 - THRESHOLD_PX;
            boolean nearTop = p.y <= bounds.y + THRESHOLD_PX;
            boolean nearBottom = p.y >= bounds.y + bounds.height - 1 - THRESHOLD_PX;
            if ((nearLeft || nearRight) && (nearTop || nearBottom)) {
                return true;
            }
        }
        return false;
    }
}
