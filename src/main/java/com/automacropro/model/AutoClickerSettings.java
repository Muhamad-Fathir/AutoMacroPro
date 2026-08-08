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

    /**
     * Humanizer: +/- spread applied to {@link #intervalMs}, in ms. 0 disables
     * it, which is the default so existing behaviour is untouched.
     *
     * Stored as an absolute value rather than a percentage to match how the
     * feature reads to a user ("100ms +/- 20ms"). The effective delay is
     * clamped to >= 1ms at draw time, so a spread wider than the interval
     * cannot produce a zero or negative sleep.
     */
    private long intervalJitterMs = 0;

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = Math.max(1, intervalMs);
    }

    public long getIntervalJitterMs() {
        return intervalJitterMs;
    }

    public void setIntervalJitterMs(long intervalJitterMs) {
        this.intervalJitterMs = Math.max(0, intervalJitterMs);
    }

    /**
     * Draws the delay to use before the next click: uniform in
     * {@code [interval - jitter, interval + jitter]}, floored at 1ms.
     *
     * Kept here beside the fields it reads so both the engine and the
     * self-check exercise the same arithmetic.
     */
    public long nextIntervalMs(java.util.Random random) {
        if (intervalJitterMs <= 0) {
            return intervalMs;
        }
        // nextLong(origin, bound) is exclusive on the bound, so +1 keeps the
        // spread symmetric - otherwise +jitter could never actually be drawn.
        long lo = Math.max(1, intervalMs - intervalJitterMs);
        long hi = intervalMs + intervalJitterMs + 1;
        return random.nextLong(lo, hi);
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
        m.put("intervalJitterMs", intervalJitterMs);
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
        Object jitter = m.get("intervalJitterMs");
        if (jitter instanceof Number) {
            s.intervalJitterMs = Math.max(0, ((Number) jitter).longValue());
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
