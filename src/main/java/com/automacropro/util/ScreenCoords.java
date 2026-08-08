package com.automacropro.util;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * Translates between the two screen coordinate spaces this app unavoidably
 * straddles on Windows.
 *
 * <h3>The problem, as measured on a mixed-DPI desktop</h3>
 * With a 2560x1440 primary at 100% and a 1920x1080 secondary at 125%, the
 * sources of coordinates disagree:
 *
 * <table><caption>measured</caption>
 *   <tr><th>Source</th><th>Space</th></tr>
 *   <tr><td>JNativeHook ({@code PositionPicker})</td><td><b>physical</b> device pixels</td></tr>
 *   <tr><td>{@code MouseInfo.getPointerInfo()}</td><td><b>logical</b> (DPI-scaled)</td></tr>
 *   <tr><td>{@code Robot.mouseMove}</td><td><b>logical</b> (DPI-scaled)</td></tr>
 * </table>
 *
 * So a position captured by Pick Location and replayed through Robot landed
 * 240x135 px away from the intended target on the 125% monitor, while being
 * pixel-exact on the 100% primary - which is exactly the "works on one monitor,
 * not the other" symptom.
 *
 * <h3>The mapping</h3>
 * {@code GraphicsConfiguration.getBounds()} is itself a hybrid: the origin is
 * in physical desktop pixels but the width/height are logical. On the secondary
 * above it reports {@code [x=-1920 y=0 w=1536 h=864]} while Win32 reports the
 * true rect {@code [-1920,0 .. 0,1080]}. Multiplying the logical size by the
 * device scale recovers the physical rect, which makes both conversions exact:
 *
 * <pre>logical = origin + (physical - origin) / scale</pre>
 *
 * <h3>Convention</h3>
 * Every coordinate stored, displayed, or captured in this app is
 * <b>physical</b> - that matches JNativeHook and every external Windows tool a
 * user might read a pixel position from. Conversion to logical happens at
 * exactly one boundary: immediately before handing a point to
 * {@code Robot.mouseMove} (see {@code RobotExecutor}).
 *
 * On a single-monitor 100% desktop every method here is the identity, so this
 * costs nothing and changes nothing for the common case.
 */
public final class ScreenCoords {

    private ScreenCoords() {
    }

    /**
     * Converts a physical (stored/captured) point into the logical space
     * {@code Robot.mouseMove} expects. Returns the point unchanged when no
     * screen claims it, which is the correct no-op on an unscaled desktop.
     */
    public static Point toRobotSpace(int physicalX, int physicalY) {
        for (GraphicsDevice device : screens()) {
            GraphicsConfiguration gc = device.getDefaultConfiguration();
            Rectangle b = gc.getBounds();
            double sx = gc.getDefaultTransform().getScaleX();
            double sy = gc.getDefaultTransform().getScaleY();
            Rectangle physical = physicalBounds(b, sx, sy);
            if (physical.contains(physicalX, physicalY)) {
                return new Point(
                        (int) Math.round(b.x + (physicalX - b.x) / sx),
                        (int) Math.round(b.y + (physicalY - b.y) / sy));
            }
        }
        return new Point(physicalX, physicalY);
    }

    /**
     * Converts a logical point - anything read from {@code MouseInfo} - into
     * the physical space the rest of the app stores.
     */
    public static Point toStoredSpace(Point logical) {
        if (logical == null) {
            return null;
        }
        for (GraphicsDevice device : screens()) {
            GraphicsConfiguration gc = device.getDefaultConfiguration();
            Rectangle b = gc.getBounds();
            if (b.contains(logical)) {
                double sx = gc.getDefaultTransform().getScaleX();
                double sy = gc.getDefaultTransform().getScaleY();
                return new Point(
                        (int) Math.round(b.x + (logical.x - b.x) * sx),
                        (int) Math.round(b.y + (logical.y - b.y) * sy));
            }
        }
        return new Point(logical.x, logical.y);
    }

    /**
     * The logical bounds of the screen a logical point sits on, or null if no
     * screen claims it.
     *
     * Callers must use this instead of testing a point against every screen in
     * turn: the screens' logical rects are laid out with physical origins, so
     * an unqualified "is this near screen N's edge?" test matches points that
     * are nowhere near screen N - see {@code FailsafeMonitor}.
     */
    public static Rectangle logicalBoundsContaining(Point logical) {
        if (logical == null) {
            return null;
        }
        for (GraphicsDevice device : screens()) {
            Rectangle b = device.getDefaultConfiguration().getBounds();
            if (b.contains(logical)) {
                return b;
            }
        }
        return null;
    }

    private static Rectangle physicalBounds(Rectangle logicalBounds, double sx, double sy) {
        return new Rectangle(logicalBounds.x, logicalBounds.y,
                (int) Math.round(logicalBounds.width * sx),
                (int) Math.round(logicalBounds.height * sy));
    }

    private static GraphicsDevice[] screens() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    }
}
