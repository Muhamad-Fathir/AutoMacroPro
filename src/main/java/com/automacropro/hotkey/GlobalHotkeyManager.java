package com.automacropro.hotkey;

import com.automacropro.model.HotkeyBinding;
import com.automacropro.util.AppLogger;
import com.automacropro.util.KeyCodeUtil;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javax.swing.SwingUtilities;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the single JNativeHook hook for the whole app and turns matching
 * key combinations into Runnable callbacks delivered on the EDT - so a
 * hotkey-triggered Start/Stop/Toggle behaves exactly like a button click
 * from the caller's point of view, with no extra thread-safety burden on
 * each panel.
 *
 * Modifier state is tracked manually via a press/release Set instead of
 * relying on {@code NativeInputEvent.getModifiers()}, which keeps this
 * class's behaviour simple, predictable and independent of JNativeHook
 * version quirks. Native key-repeat (which fires nativeKeyPressed
 * repeatedly while a key is held) is filtered out: a binding only fires on
 * the transition from "not pressed" to "pressed".
 */
public final class GlobalHotkeyManager {

    private static GlobalHotkeyManager instance;

    public static synchronized GlobalHotkeyManager getInstance() {
        if (instance == null) {
            instance = new GlobalHotkeyManager();
        }
        return instance;
    }

    private final Set<Integer> pressedKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, HotkeyBinding> bindings = new ConcurrentHashMap<>();
    private final Map<String, Runnable> actions = new ConcurrentHashMap<>();
    private boolean hookRegistered = false;

    private GlobalHotkeyManager() {
    }

    /**
     * Registers the OS-level hook. Call once at application startup.
     *
     * Catches more than the checked {@code NativeHookException} on purpose:
     * loading JNativeHook's native library can also fail with an
     * {@code UnsatisfiedLinkError} (an {@code Error}, not an {@code Exception} -
     * found via an actual runtime smoke test, not just code review) on an
     * unsupported platform/architecture or a corrupted install. Either way,
     * hotkeys simply stay disabled; the rest of the app (buttons, Robot
     * execution) must still start up normally instead of the whole window
     * failing to appear.
     */
    public synchronized void initialize() {
        if (hookRegistered) {
            return;
        }
        quietenJNativeHookLogging();
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
                @Override
                public void nativeKeyPressed(NativeKeyEvent e) {
                    onPressed(e.getKeyCode());
                }

                @Override
                public void nativeKeyReleased(NativeKeyEvent e) {
                    onReleased(e.getKeyCode());
                }

                @Override
                public void nativeKeyTyped(NativeKeyEvent e) {
                    // not needed for hotkeys
                }
            });
            hookRegistered = true;
            AppLogger.info("Global hotkey hook berhasil didaftarkan.");
        } catch (NativeHookException ex) {
            AppLogger.error("Gagal mendaftarkan global hotkey hook - hotkey tidak akan berfungsi. "
                    + "Coba jalankan aplikasi sebagai Administrator.", ex);
        } catch (Throwable t) {
            // e.g. UnsatisfiedLinkError if the native library can't be loaded at all
            // on this platform/architecture. The app must still come up without hotkeys.
            AppLogger.error("Gagal memuat native library JNativeHook - hotkey tidak akan berfungsi, "
                    + "tapi modul lain tetap berjalan normal.", t);
        }
    }

    /** Unregisters the OS-level hook. Call once when the application window closes. */
    public synchronized void shutdown() {
        if (!hookRegistered) {
            return;
        }
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException ex) {
            AppLogger.warn("Gagal melepas global hotkey hook saat shutdown", ex);
        } finally {
            hookRegistered = false;
        }
    }

    public boolean isHookActive() {
        return hookRegistered;
    }

    /** Registers/replaces the action fired when {@code binding} is pressed. */
    public void setBinding(HotkeyBinding binding, Runnable action) {
        bindings.put(binding.getId(), binding);
        actions.put(binding.getId(), action);
    }

    public void clearBinding(String id) {
        bindings.remove(id);
        actions.remove(id);
    }

    private void onPressed(int vcCode) {
        boolean isNewPress = pressedKeys.add(vcCode);
        if (!isNewPress) {
            return; // native key-repeat while held - ignore
        }
        if (KeyCodeUtil.isNativeModifier(vcCode)) {
            return; // a modifier alone never triggers a binding
        }
        fireMatchingBindings(vcCode);
    }

    private void onReleased(int vcCode) {
        pressedKeys.remove(vcCode);
    }

    private void fireMatchingBindings(int triggerVcCode) {
        boolean ctrl = pressedKeys.contains(NativeKeyEvent.VC_CONTROL);
        boolean shift = pressedKeys.contains(NativeKeyEvent.VC_SHIFT);
        boolean alt = pressedKeys.contains(NativeKeyEvent.VC_ALT);
        for (HotkeyBinding b : bindings.values()) {
            if (b.isUnbound() || b.getTriggerVcCode() != triggerVcCode) {
                continue;
            }
            if (b.isCtrl() == ctrl && b.isShift() == shift && b.isAlt() == alt) {
                Runnable action = actions.get(b.getId());
                if (action != null) {
                    SwingUtilities.invokeLater(action);
                }
            }
        }
    }

    /** JNativeHook logs a lot at INFO by default; keep the console clean. */
    private void quietenJNativeHookLogging() {
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(false);
    }
}
