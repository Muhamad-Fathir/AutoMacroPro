package com.automacropro.util;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Maps a JNativeHook {@code VC_*} code to the AWT {@code VK_*} code that
     * {@code Robot} needs.
     *
     * Required by the Macro Recorder, which observes keys through the hook (VC
     * space) but must emit steps that {@code Robot} can replay (VK space). The
     * two namespaces are unrelated integers, so a cast would produce keystrokes
     * that look plausible and are wrong.
     *
     * Built by reflecting over both classes' constant names and pairing them by
     * suffix ({@code VC_A} to {@code VK_A}, {@code VC_F5} to {@code VK_F5}, ...)
     * rather than hand-listing ~100 pairs, because a hand-written table is where
     * a silent typo would live - one wrong entry means one key that records as a
     * different key, which is nearly invisible in testing. The handful of names
     * that genuinely differ between the two are fixed up explicitly below.
     *
     * @return the AWT code, or -1 when this key has no Robot equivalent.
     */
    public static int vcToVk(int vcCode) {
        return VC_TO_VK.getOrDefault(vcCode, -1);
    }

    private static final Map<Integer, Integer> VC_TO_VK = buildVcToVkMap();

    private static Map<Integer, Integer> buildVcToVkMap() {
        Map<String, Integer> awtByName = new HashMap<>();
        for (Field f : KeyEvent.class.getFields()) {
            if (f.getName().startsWith("VK_") && f.getType() == int.class) {
                try {
                    awtByName.put(f.getName().substring(3), f.getInt(null));
                } catch (IllegalAccessException ignored) {
                    // public static final int on a public class - cannot happen
                }
            }
        }

        // Names that do not line up by suffix between the two namespaces.
        Map<String, String> aliases = new HashMap<>();
        aliases.put("MINUS", "MINUS");
        aliases.put("EQUALS", "EQUALS");
        aliases.put("BACKSPACE", "BACK_SPACE");
        aliases.put("OPEN_BRACKET", "OPEN_BRACKET");
        aliases.put("CLOSE_BRACKET", "CLOSE_BRACKET");
        aliases.put("BACK_SLASH", "BACK_SLASH");
        aliases.put("SEMICOLON", "SEMICOLON");
        aliases.put("QUOTE", "QUOTE");
        aliases.put("COMMA", "COMMA");
        aliases.put("PERIOD", "PERIOD");
        aliases.put("SLASH", "SLASH");
        aliases.put("SPACE", "SPACE");
        aliases.put("PRINTSCREEN", "PRINTSCREEN");
        aliases.put("SCROLL_LOCK", "SCROLL_LOCK");
        aliases.put("NUM_LOCK", "NUM_LOCK");
        aliases.put("CAPS_LOCK", "CAPS_LOCK");
        aliases.put("CONTROL", "CONTROL");
        aliases.put("META", "META");
        aliases.put("CONTEXT_MENU", "CONTEXT_MENU");

        Map<Integer, Integer> mapping = new HashMap<>();
        for (Field f : NativeKeyEvent.class.getFields()) {
            if (!f.getName().startsWith("VC_") || f.getType() != int.class) {
                continue;
            }
            String suffix = f.getName().substring(3);
            Integer awt = awtByName.get(aliases.getOrDefault(suffix, suffix));
            if (awt == null) {
                continue;
            }
            try {
                int vc = f.getInt(null);
                // VC_UNDEFINED and friends collide on 0; never map those.
                if (vc != 0) {
                    mapping.putIfAbsent(vc, awt);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return Collections.unmodifiableMap(mapping);
    }
}
