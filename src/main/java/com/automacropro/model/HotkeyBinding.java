package com.automacropro.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One custom hotkey binding. The key code stored here is a JNativeHook
 * {@code NativeKeyEvent.VC_*} code (NOT an AWT VK_* code) because bindings
 * are matched against global keyboard events delivered by JNativeHook -
 * see {@code com.automacropro.hotkey.GlobalHotkeyManager}.
 */
public class HotkeyBinding {

    /** Stable identifier, e.g. "autoclicker.start", "macro.toggle". */
    private String id;
    private boolean ctrl;
    private boolean shift;
    private boolean alt;
    /** JNativeHook NativeKeyEvent.VC_* code of the non-modifier trigger key. -1 = unbound. */
    private int triggerVcCode = -1;

    public HotkeyBinding(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public boolean isCtrl() {
        return ctrl;
    }

    public void setCtrl(boolean ctrl) {
        this.ctrl = ctrl;
    }

    public boolean isShift() {
        return shift;
    }

    public void setShift(boolean shift) {
        this.shift = shift;
    }

    public boolean isAlt() {
        return alt;
    }

    public void setAlt(boolean alt) {
        this.alt = alt;
    }

    public int getTriggerVcCode() {
        return triggerVcCode;
    }

    public void setTriggerVcCode(int triggerVcCode) {
        this.triggerVcCode = triggerVcCode;
    }

    public boolean isUnbound() {
        return triggerVcCode < 0;
    }

    public String describe() {
        if (isUnbound()) {
            return com.automacropro.util.I18n.t("common.unset");
        }
        StringBuilder sb = new StringBuilder();
        if (ctrl) sb.append("Ctrl+");
        if (shift) sb.append("Shift+");
        if (alt) sb.append("Alt+");
        sb.append(com.automacropro.util.KeyCodeUtil.vcToDisplayName(triggerVcCode));
        return sb.toString();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("ctrl", ctrl);
        m.put("shift", shift);
        m.put("alt", alt);
        m.put("triggerVcCode", triggerVcCode);
        return m;
    }

    public static HotkeyBinding fromMap(String id, Map<String, Object> m) {
        HotkeyBinding b = new HotkeyBinding(id);
        if (m == null) {
            return b;
        }
        b.ctrl = boolOf(m, "ctrl");
        b.shift = boolOf(m, "shift");
        b.alt = boolOf(m, "alt");
        Object code = m.get("triggerVcCode");
        if (code instanceof Number) {
            b.triggerVcCode = ((Number) code).intValue();
        }
        return b;
    }

    private static boolean boolOf(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Boolean && (Boolean) v;
    }
}
