package com.automacropro.hotkey;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseListener;

import javax.swing.SwingUtilities;
import java.awt.Point;
import java.util.function.Consumer;

/**
 * Implements the confirmed "click-to-capture" behaviour for Pick Location:
 * arms a one-shot global mouse listener, and the very next physical mouse
 * press anywhere on screen (including inside a game window) is reported as
 * the picked coordinate.
 *
 * Note this is a deliberate trade-off the user confirmed: because
 * JNativeHook only observes events (it cannot swallow/consume them), that
 * same physical click still goes through normally to whatever window is
 * under the cursor.
 */
public final class PositionPicker {

    private PositionPicker() {
    }

    public static NativeMouseListener captureNextClick(Consumer<Point> onCaptured) {
        final NativeMouseListener[] holder = new NativeMouseListener[1];
        holder[0] = new NativeMouseListener() {
            @Override
            public void nativeMousePressed(NativeMouseEvent e) {
                GlobalScreen.removeNativeMouseListener(holder[0]);
                final Point p = new Point(e.getX(), e.getY());
                SwingUtilities.invokeLater(() -> onCaptured.accept(p));
            }

            @Override
            public void nativeMouseReleased(NativeMouseEvent e) {
            }

            @Override
            public void nativeMouseClicked(NativeMouseEvent e) {
            }
        };
        GlobalScreen.addNativeMouseListener(holder[0]);
        return holder[0];
    }

    /** Cancels an in-progress capture (e.g. if the user closes the dialog without clicking). */
    public static void cancel(NativeMouseListener listener) {
        if (listener != null) {
            GlobalScreen.removeNativeMouseListener(listener);
        }
    }
}
