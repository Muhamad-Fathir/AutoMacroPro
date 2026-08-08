package com.automacropro.engine;

import com.automacropro.model.ActionType;
import com.automacropro.model.ClickMode;
import com.automacropro.model.KeyActionConfig;
import com.automacropro.model.MacroStep;
import com.automacropro.model.MouseActionConfig;
import com.automacropro.model.MouseButtonType;
import com.automacropro.model.PositionMode;
import com.automacropro.util.AppLogger;
import com.automacropro.util.KeyCodeUtil;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseListener;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records real mouse clicks and keystrokes into {@link MacroStep}s, ready to
 * drop straight into the sequencer.
 *
 * <h3>Threading</h3>
 * Every callback here arrives on JNativeHook's own dispatch thread, which is
 * the thread feeding the OS-level hook for the entire desktop. Two rules follow,
 * and both matter more than they look:
 * <ul>
 *   <li><b>Never block it.</b> Windows tears down a low-level hook that stops
 *       servicing events (the LowLevelHooksTimeout), which would silently kill
 *       this app's global hotkeys - and every other hook-based app's too. So
 *       these methods only append to a list and return; no I/O, no dialogs, no
 *       waiting.</li>
 *   <li><b>Never touch Swing from it.</b> Recorded steps are handed to the EDT
 *       via {@code invokeLater} exactly once, when recording stops.</li>
 * </ul>
 * The step list is a {@link CopyOnWriteArrayList} because the hook thread
 * appends while the EDT may read the running count for the status line.
 *
 * <h3>What it records</h3>
 * Mouse presses become SINGLE-click steps at fixed coordinates, key presses
 * become KEYBOARD steps, and the real idle time between them becomes DELAY
 * steps - a recording that replays at the original speed is the whole point.
 * Modifier keys are folded into the following key's combo rather than emitted
 * as steps of their own, so Ctrl+C records as one step, not three.
 */
public final class MacroRecorder {

    /** Gaps below this are noise, not intent - dropped to keep the list readable. */
    private static final long MIN_DELAY_MS = 15;

    /** Hard cap so a recording left running overnight cannot exhaust memory. */
    private static final int MAX_STEPS = 5_000;

    public interface Listener {
        /** Fired on the EDT as steps accumulate, for a live count in the UI. */
        void onProgress(int stepCount);

        /** Fired on the EDT when recording ends, with everything captured. */
        void onFinished(List<MacroStep> steps, boolean hitLimit);
    }

    private final List<MacroStep> recorded = new CopyOnWriteArrayList<>();
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final List<Integer> heldModifiers = new CopyOnWriteArrayList<>();

    private volatile long lastEventMs;
    private volatile boolean hitLimit;
    private Listener listener;

    private NativeKeyListener keyListener;
    private NativeMouseListener mouseListener;

    public boolean isRecording() {
        return recording.get();
    }

    public int getStepCount() {
        return recorded.size();
    }

    /**
     * Starts capturing. Returns false if the global hook is unavailable, in
     * which case nothing was registered and the UI should say so rather than
     * appear to record into a void.
     */
    public boolean start(Listener listener) {
        if (!recording.compareAndSet(false, true)) {
            return true; // already recording
        }
        if (!com.automacropro.hotkey.GlobalHotkeyManager.getInstance().isHookActive()) {
            recording.set(false);
            AppLogger.warn("Macro Recorder: hook global tidak aktif, perekaman dibatalkan", null);
            return false;
        }
        this.listener = listener;
        recorded.clear();
        heldModifiers.clear();
        hitLimit = false;
        lastEventMs = System.currentTimeMillis();

        keyListener = new NativeKeyListener() {
            @Override
            public void nativeKeyPressed(NativeKeyEvent e) {
                onKeyPressed(e.getKeyCode());
            }

            @Override
            public void nativeKeyReleased(NativeKeyEvent e) {
                heldModifiers.remove(Integer.valueOf(e.getKeyCode()));
            }

            @Override
            public void nativeKeyTyped(NativeKeyEvent e) {
            }
        };
        mouseListener = new NativeMouseListener() {
            @Override
            public void nativeMousePressed(NativeMouseEvent e) {
                onMousePressed(e);
            }

            @Override
            public void nativeMouseReleased(NativeMouseEvent e) {
            }

            @Override
            public void nativeMouseClicked(NativeMouseEvent e) {
            }
        };
        GlobalScreen.addNativeKeyListener(keyListener);
        GlobalScreen.addNativeMouseListener(mouseListener);
        AppLogger.info("Macro Recorder: mulai merekam");
        return true;
    }

    /** Stops capturing and delivers the result on the EDT. Safe to call twice. */
    public void stop() {
        if (!recording.compareAndSet(true, false)) {
            return;
        }
        if (keyListener != null) {
            GlobalScreen.removeNativeKeyListener(keyListener);
            keyListener = null;
        }
        if (mouseListener != null) {
            GlobalScreen.removeNativeMouseListener(mouseListener);
            mouseListener = null;
        }
        List<MacroStep> snapshot = new ArrayList<>(recorded);
        // Drop the click that pressed "Stop Recording": the hook is global, so it
        // sees that click too, and every recording would otherwise end with a
        // stray click on this app's own button - which on replay would land
        // wherever that button happened to be. Also drops the DELAY step in front
        // of it, since that gap is just the user reaching for the mouse.
        int lastIndex = snapshot.size() - 1;
        if (!hitLimit && lastIndex >= 0 && snapshot.get(lastIndex).getType() == ActionType.MOUSE) {
            snapshot.remove(lastIndex);
            if (!snapshot.isEmpty() && snapshot.get(snapshot.size() - 1).getType() == ActionType.DELAY) {
                snapshot.remove(snapshot.size() - 1);
            }
        }
        boolean limited = hitLimit;
        AppLogger.info("Macro Recorder: selesai, " + snapshot.size() + " step terekam");
        Listener l = listener;
        if (l != null) {
            SwingUtilities.invokeLater(() -> l.onFinished(snapshot, limited));
        }
    }

    // ---- hook-thread callbacks: append and return, nothing else ----

    private void onKeyPressed(int vcCode) {
        if (!recording.get()) {
            return;
        }
        if (KeyCodeUtil.isNativeModifier(vcCode)) {
            // Held so the next real key records as a combo (Ctrl+C = one step).
            if (!heldModifiers.contains(vcCode)) {
                heldModifiers.add(vcCode);
            }
            return;
        }
        int vk = KeyCodeUtil.vcToVk(vcCode);
        if (vk < 0) {
            // A key Robot cannot reproduce (media keys, some OEM keys). Recording
            // it would produce a step that silently does nothing on replay.
            AppLogger.warn("Macro Recorder: tombol tanpa padanan AWT diabaikan (VC=" + vcCode + ")", null);
            return;
        }

        List<Integer> combo = new ArrayList<>();
        for (int modifier : heldModifiers) {
            int modifierVk = KeyCodeUtil.vcToVk(modifier);
            if (modifierVk >= 0) {
                combo.add(modifierVk);
            }
        }
        combo.add(vk);

        MacroStep step = new MacroStep(ActionType.KEYBOARD);
        KeyActionConfig cfg = new KeyActionConfig();
        cfg.setVkCodes(combo);
        step.setKeyConfig(cfg);
        append(step);
    }

    private void onMousePressed(NativeMouseEvent e) {
        if (!recording.get()) {
            return;
        }
        MacroStep step = new MacroStep(ActionType.MOUSE);
        MouseActionConfig cfg = new MouseActionConfig();
        cfg.setButton(toButtonType(e.getButton()));
        cfg.setClickMode(ClickMode.SINGLE);
        cfg.setPositionMode(PositionMode.FIXED_COORDINATE);
        // JNativeHook reports physical desktop pixels, which is exactly the space
        // this app stores coordinates in - see ScreenCoords. No conversion here.
        cfg.setX(e.getX());
        cfg.setY(e.getY());
        step.setMouseConfig(cfg);
        append(step);
    }

    /**
     * Inserts the elapsed idle time as a DELAY step, then the action itself, so
     * a replay reproduces the original rhythm rather than firing everything at
     * machine speed.
     */
    private void append(MacroStep step) {
        if (recorded.size() >= MAX_STEPS) {
            if (!hitLimit) {
                hitLimit = true;
                AppLogger.warn("Macro Recorder: batas " + MAX_STEPS + " step tercapai, berhenti merekam", null);
                // Unregister from a non-hook thread: stop() removes listeners,
                // and doing that from inside a hook callback is asking for a
                // ConcurrentModificationException inside JNativeHook's dispatch.
                SwingUtilities.invokeLater(this::stop);
            }
            return;
        }
        long now = System.currentTimeMillis();
        long gap = now - lastEventMs;
        lastEventMs = now;
        if (gap >= MIN_DELAY_MS) {
            MacroStep delay = new MacroStep(ActionType.DELAY);
            delay.setDelayMs(gap);
            recorded.add(delay);
        }
        recorded.add(step);

        Listener l = listener;
        if (l != null) {
            int count = recorded.size();
            SwingUtilities.invokeLater(() -> l.onProgress(count));
        }
    }

    private static MouseButtonType toButtonType(int nativeButton) {
        switch (nativeButton) {
            case NativeMouseEvent.BUTTON2:
                return MouseButtonType.RIGHT;
            case NativeMouseEvent.BUTTON3:
                return MouseButtonType.MIDDLE;
            default:
                return MouseButtonType.LEFT;
        }
    }
}
