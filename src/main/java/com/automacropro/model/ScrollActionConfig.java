package com.automacropro.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One mouse-wheel action: a direction and a number of notches.
 *
 * The signed amount handed to {@code Robot.mouseWheel} is derived in
 * {@link #getWheelAmount()} rather than stored, so the JSON stays human-readable
 * ({@code direction: "UP", notches: 3}) instead of an unexplained {@code -3}.
 * Robot's convention: negative = up/away from the user, positive = down/toward.
 */
public class ScrollActionConfig {

    public enum ScrollDirection { UP, DOWN }

    private ScrollDirection direction = ScrollDirection.DOWN;
    private int notches = 3;

    public ScrollActionConfig() {
    }

    public ScrollDirection getDirection() {
        return direction;
    }

    public void setDirection(ScrollDirection direction) {
        this.direction = direction;
    }

    public int getNotches() {
        return notches;
    }

    public void setNotches(int notches) {
        this.notches = Math.max(1, notches);
    }

    /** Signed value for {@code Robot.mouseWheel}: up is negative, down positive. */
    public int getWheelAmount() {
        return direction == ScrollDirection.UP ? -notches : notches;
    }

    public String describe() {
        return direction + " x" + notches;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("direction", direction.name());
        m.put("notches", notches);
        return m;
    }

    public static ScrollActionConfig fromMap(Map<String, Object> m) {
        ScrollActionConfig c = new ScrollActionConfig();
        if (m == null) {
            return c;
        }
        Object dir = m.get("direction");
        if (dir != null) {
            try {
                c.direction = ScrollDirection.valueOf(String.valueOf(dir));
            } catch (IllegalArgumentException ignored) {
                c.direction = ScrollDirection.DOWN;
            }
        }
        Object n = m.get("notches");
        if (n instanceof Number) {
            c.setNotches(((Number) n).intValue());
        }
        return c;
    }
}
