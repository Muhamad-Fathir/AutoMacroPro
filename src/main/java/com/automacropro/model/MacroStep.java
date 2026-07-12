package com.automacropro.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single step inside a {@link MacroProject}. Exactly one of
 * {@code mouseConfig} / {@code keyConfig} / {@code delayMs} is meaningful,
 * selected by {@code type}.
 */
public class MacroStep {

    private ActionType type;
    private MouseActionConfig mouseConfig;
    private KeyActionConfig keyConfig;
    private long delayMs;
    private String label; // optional free-text note shown in the list

    public MacroStep(ActionType type) {
        this.type = type;
        if (type == ActionType.MOUSE) {
            this.mouseConfig = new MouseActionConfig();
        } else if (type == ActionType.KEYBOARD) {
            this.keyConfig = new KeyActionConfig();
        }
    }

    public ActionType getType() {
        return type;
    }

    public void setType(ActionType type) {
        this.type = type;
    }

    public MouseActionConfig getMouseConfig() {
        return mouseConfig;
    }

    public void setMouseConfig(MouseActionConfig mouseConfig) {
        this.mouseConfig = mouseConfig;
    }

    public KeyActionConfig getKeyConfig() {
        return keyConfig;
    }

    public void setKeyConfig(KeyActionConfig keyConfig) {
        this.keyConfig = keyConfig;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** Human readable one-liner, used directly as the JList row text. */
    public String describe() {
        String body;
        switch (type) {
            case MOUSE:
                body = "Mouse - " + (mouseConfig == null ? "?" : mouseConfig.describe());
                break;
            case KEYBOARD:
                body = "Keyboard - " + (keyConfig == null ? "?" : keyConfig.describe());
                break;
            case DELAY:
                body = "Delay - " + delayMs + " ms";
                break;
            default:
                body = "?";
        }
        return (label != null && !label.isBlank()) ? (label + " (" + body + ")") : body;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type.name());
        if (mouseConfig != null) {
            m.put("mouseConfig", mouseConfig.toMap());
        }
        if (keyConfig != null) {
            m.put("keyConfig", keyConfig.toMap());
        }
        m.put("delayMs", delayMs);
        if (label != null) {
            m.put("label", label);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    public static MacroStep fromMap(Map<String, Object> m) {
        ActionType t = ActionType.valueOf(String.valueOf(m.get("type")));
        MacroStep step = new MacroStep(t);
        Object mc = m.get("mouseConfig");
        if (mc instanceof Map) {
            step.mouseConfig = MouseActionConfig.fromMap((Map<String, Object>) mc);
        }
        Object kc = m.get("keyConfig");
        if (kc instanceof Map) {
            step.keyConfig = KeyActionConfig.fromMap((Map<String, Object>) kc);
        }
        Object d = m.get("delayMs");
        if (d instanceof Number) {
            step.delayMs = ((Number) d).longValue();
        }
        Object lbl = m.get("label");
        if (lbl != null) {
            step.label = String.valueOf(lbl);
        }
        return step;
    }
}
