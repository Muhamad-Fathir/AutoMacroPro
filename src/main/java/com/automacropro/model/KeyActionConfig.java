package com.automacropro.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One keyboard action: a single key, or a combination (e.g. Ctrl+Shift+A).
 * Codes are stored as {@code java.awt.event.KeyEvent.VK_*} values because
 * this configuration is only ever consumed by {@code java.awt.Robot}, which
 * speaks the AWT virtual-key namespace (NOT the JNativeHook VC_ namespace
 * used for global hotkeys - those two are intentionally kept separate).
 */
public class KeyActionConfig {

    /** AWT VK_* codes, held in press order; released in reverse order. */
    private List<Integer> vkCodes = new ArrayList<>();

    /** How long (ms) to hold the full combo down before releasing it. */
    private int holdMs = 40;

    public KeyActionConfig() {
    }

    public List<Integer> getVkCodes() {
        return vkCodes;
    }

    public void setVkCodes(List<Integer> vkCodes) {
        this.vkCodes = vkCodes;
    }

    public int getHoldMs() {
        return holdMs;
    }

    public void setHoldMs(int holdMs) {
        this.holdMs = holdMs;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vkCodes.size(); i++) {
            if (i > 0) {
                sb.append(" + ");
            }
            sb.append(com.automacropro.util.KeyCodeUtil.vkToDisplayName(vkCodes.get(i)));
        }
        return sb.length() == 0 ? "(no key set)" : sb.toString();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vkCodes", new ArrayList<>(vkCodes));
        m.put("holdMs", holdMs);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static KeyActionConfig fromMap(Map<String, Object> m) {
        KeyActionConfig c = new KeyActionConfig();
        if (m == null) {
            return c;
        }
        Object rawList = m.get("vkCodes");
        if (rawList instanceof List) {
            for (Object o : (List<Object>) rawList) {
                if (o instanceof Number) {
                    c.vkCodes.add(((Number) o).intValue());
                }
            }
        }
        Object hold = m.get("holdMs");
        if (hold instanceof Number) {
            c.holdMs = ((Number) hold).intValue();
        }
        return c;
    }
}
