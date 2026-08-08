package com.automacropro.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single step inside a {@link MacroProject}.
 *
 * Which of {@code mouseConfig} / {@code keyConfig} / {@code delayMs} is
 * meaningful is selected by {@code type} - {@code delayMs} being the duration
 * of a standalone DELAY step.
 *
 * {@link #preDelayMs} is the exception: it applies to <b>every</b> type, and is
 * the pause taken immediately before the step's own action runs. It exists so a
 * sequence can be paced without a standalone DELAY step between every action,
 * which doubles the length of the list for no added expressiveness. Standalone
 * DELAY steps still work and are still what the recorder emits.
 */
public class MacroStep {

    private ActionType type;
    private MouseActionConfig mouseConfig;
    private KeyActionConfig keyConfig;
    private ScrollActionConfig scrollConfig;
    private long delayMs;
    private String label; // optional free-text note shown in the list

    /**
     * Pause (ms) before this step's action executes. Applies to all action
     * types. Defaults to 0 so project files written before this field existed
     * load with no behaviour change.
     */
    private long preDelayMs;

    public MacroStep(ActionType type) {
        this.type = type;
        if (type == ActionType.MOUSE) {
            this.mouseConfig = new MouseActionConfig();
        } else if (type == ActionType.KEYBOARD) {
            this.keyConfig = new KeyActionConfig();
        } else if (type == ActionType.SCROLL) {
            this.scrollConfig = new ScrollActionConfig();
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

    public ScrollActionConfig getScrollConfig() {
        return scrollConfig;
    }

    public void setScrollConfig(ScrollActionConfig scrollConfig) {
        this.scrollConfig = scrollConfig;
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

    public long getPreDelayMs() {
        return preDelayMs;
    }

    public void setPreDelayMs(long preDelayMs) {
        this.preDelayMs = Math.max(0, preDelayMs);
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
            case SCROLL:
                body = "Scroll - " + (scrollConfig == null ? "?" : scrollConfig.describe());
                break;
            default:
                body = "?";
        }
        if (preDelayMs > 0) {
            // Suppressed at 0 so rows without a pre-delay read exactly as before.
            body = body + " - " + com.automacropro.util.I18n.t("macro.preDelay.suffix", preDelayMs);
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
        if (scrollConfig != null) {
            m.put("scrollConfig", scrollConfig.toMap());
        }
        m.put("delayMs", delayMs);
        m.put("preDelayMs", preDelayMs);
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
        Object sc = m.get("scrollConfig");
        if (sc instanceof Map) {
            step.scrollConfig = ScrollActionConfig.fromMap((Map<String, Object>) sc);
        }
        Object d = m.get("delayMs");
        if (d instanceof Number) {
            step.delayMs = ((Number) d).longValue();
        }
        // Absent from files written before pre-delays existed; defaults to 0.
        Object pd = m.get("preDelayMs");
        if (pd instanceof Number) {
            step.preDelayMs = Math.max(0, ((Number) pd).longValue());
        }
        Object lbl = m.get("label");
        if (lbl != null) {
            step.label = String.valueOf(lbl);
        }
        return step;
    }
}
