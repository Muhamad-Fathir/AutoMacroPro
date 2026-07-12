package com.automacropro.util;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

import java.awt.event.KeyEvent;

/**
 * Small helper to keep the two key-code namespaces used in this app straight:
 * <ul>
 *   <li>{@code java.awt.event.KeyEvent.VK_*} - used wherever {@code java.awt.Robot}
 *       simulates a keystroke (macro keyboard steps).</li>
 *   <li>{@code com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_*} - used
 *       wherever JNativeHook listens for a key globally (custom hotkeys,
 *       Pick Location is mouse-only so it doesn't need this).</li>
 * </ul>
 * They are different integer spaces; never mix them.
 */
public final class KeyCodeUtil {

    private KeyCodeUtil() {
    }

    public static String vkToDisplayName(int vkCode) {
        try {
            return KeyEvent.getKeyText(vkCode);
        } catch (Exception e) {
            return "VK(" + vkCode + ")";
        }
    }

    public static String vcToDisplayName(int vcCode) {
        try {
            return NativeKeyEvent.getKeyText(vcCode);
        } catch (Exception e) {
            return "VC(" + vcCode + ")";
        }
    }

    /** True for the AWT codes of Ctrl/Shift/Alt/Meta themselves (either side). */
    public static boolean isAwtModifier(int vkCode) {
        return vkCode == KeyEvent.VK_CONTROL || vkCode == KeyEvent.VK_SHIFT
                || vkCode == KeyEvent.VK_ALT || vkCode == KeyEvent.VK_META
                || vkCode == KeyEvent.VK_ALT_GRAPH;
    }

    /** True for the JNativeHook native codes of Ctrl/Shift/Alt/Meta themselves. */
    public static boolean isNativeModifier(int vcCode) {
        return vcCode == NativeKeyEvent.VC_CONTROL || vcCode == NativeKeyEvent.VC_SHIFT
                || vcCode == NativeKeyEvent.VC_ALT || vcCode == NativeKeyEvent.VC_META;
    }
}
