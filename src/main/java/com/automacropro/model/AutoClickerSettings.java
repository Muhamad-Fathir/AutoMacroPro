package com.automacropro.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persisted configuration for the Autoclicker module (module 1).
 */
public class AutoClickerSettings {

    private long intervalMs = 100; // can go as low as 1
    private ClickLimitMode limitMode = ClickLimitMode.INFINITE;
    private long fixedClickCount = 100;
    private MouseActionConfig mouseConfig = new MouseActionConfig();

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = Math.max(1, intervalMs);
    }

    public ClickLimitMode getLimitMode() {
        return limitMode;
    }

    public void setLimitMode(ClickLimitMode limitMode) {
        this.limitMode = limitMode;
    }

    public long getFixedClickCount() {
        return fixedClickCount;
    }

    public void setFixedClickCount(long fixedClickCount) {
        this.fixedClickCount = Math.max(1, fixedClickCount);
    }

    public MouseActionConfig getMouseConfig() {
        return mouseConfig;
    }

    public void setMouseConfig(MouseActionConfig mouseConfig) {
        this.mouseConfig = mouseConfig;
    }

    public static AutoClickerSettings defaults() {
        return new AutoClickerSettings();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("intervalMs", intervalMs);
        m.put("limitMode", limitMode.name());
        m.put("fixedClickCount", fixedClickCount);
        m.put("mouseConfig", mouseConfig.toMap());
        return m;
    }

    @SuppressWarnings("unchecked")
    public static AutoClickerSettings fromMap(Map<String, Object> m) {
        AutoClickerSettings s = new AutoClickerSettings();
        if (m == null) {
            return s;
        }
        Object iv = m.get("intervalMs");
        if (iv instanceof Number) {
            s.intervalMs = Math.max(1, ((Number) iv).longValue());
        }
        Object lm = m.get("limitMode");
        if (lm != null) {
            try {
                s.limitMode = ClickLimitMode.valueOf(String.valueOf(lm));
            } catch (IllegalArgumentException ignored) {
            }
        }
        Object fc = m.get("fixedClickCount");
        if (fc instanceof Number) {
            s.fixedClickCount = Math.max(1, ((Number) fc).longValue());
        }
        Object mc = m.get("mouseConfig");
        if (mc instanceof Map) {
            s.mouseConfig = MouseActionConfig.fromMap((Map<String, Object>) mc);
        }
        return s;
    }
}
