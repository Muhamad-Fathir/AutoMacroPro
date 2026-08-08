package com.automacropro.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Full description of one mouse interaction: which button, what gesture
 * (single / double / drag), where it starts, and - for drags - where it
 * ends and how the pointer travels there.
 *
 * Shared by {@link AutoClickerSettings} (one instance) and by each
 * {@link MacroStep} of type {@link ActionType#MOUSE}.
 */
public class MouseActionConfig {

    private MouseButtonType button = MouseButtonType.LEFT;
    private ClickMode clickMode = ClickMode.SINGLE;
    private PositionMode positionMode = PositionMode.CURRENT_CURSOR;

    // Start position (used when positionMode == FIXED_COORDINATE)
    private int x;
    private int y;

    // Drag destination (only meaningful when clickMode == DRAG; always a fixed point)
    private int dragToX;
    private int dragToY;

    // Per-action drag behaviour (confirmed: configurable per action, not global)
    private DragStyle dragStyle = DragStyle.SMOOTH;
    private int dragSteps = 30;        // used only when dragStyle == SMOOTH
    private int dragDurationMs = 300;  // used only when dragStyle == SMOOTH

    // Used only when clickMode == HOLD. Default 0 so old project files
    // (saved before this feature existed) load safely without this key.
    private int holdDurationMs = 0;

    /**
     * Humanizer: scatter each click uniformly inside a circle of this radius
     * (px) around the target point. 0 = exact targeting, which is the default
     * so behaviour is unchanged unless the user opts in.
     *
     * Lives here rather than in {@link AutoClickerSettings} so the Macro
     * Sequencer's MOUSE steps get it too - both modules route through the same
     * {@code RobotExecutor}.
     */
    private int positionJitterPx = 0;

    public MouseActionConfig() {
    }

    public MouseButtonType getButton() {
        return button;
    }

    public void setButton(MouseButtonType button) {
        this.button = button;
    }

    public ClickMode getClickMode() {
        return clickMode;
    }

    public void setClickMode(ClickMode clickMode) {
        this.clickMode = clickMode;
    }

    public PositionMode getPositionMode() {
        return positionMode;
    }

    public void setPositionMode(PositionMode positionMode) {
        this.positionMode = positionMode;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getDragToX() {
        return dragToX;
    }

    public void setDragToX(int dragToX) {
        this.dragToX = dragToX;
    }

    public int getDragToY() {
        return dragToY;
    }

    public void setDragToY(int dragToY) {
        this.dragToY = dragToY;
    }

    public DragStyle getDragStyle() {
        return dragStyle;
    }

    public void setDragStyle(DragStyle dragStyle) {
        this.dragStyle = dragStyle;
    }

    public int getDragSteps() {
        return dragSteps;
    }

    public void setDragSteps(int dragSteps) {
        this.dragSteps = dragSteps;
    }

    public int getDragDurationMs() {
        return dragDurationMs;
    }

    public void setDragDurationMs(int dragDurationMs) {
        this.dragDurationMs = dragDurationMs;
    }

    public int getHoldDurationMs() {
        return holdDurationMs;
    }

    public void setHoldDurationMs(int holdDurationMs) {
        this.holdDurationMs = holdDurationMs;
    }

    public int getPositionJitterPx() {
        return positionJitterPx;
    }

    public void setPositionJitterPx(int positionJitterPx) {
        this.positionJitterPx = Math.max(0, positionJitterPx);
    }

    /** Short human readable summary, used in the macro step list UI. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(button).append(' ').append(clickMode);
        if (clickMode == ClickMode.DRAG) {
            String from = positionMode == PositionMode.CURRENT_CURSOR ? "cursor" : (x + "," + y);
            sb.append(" [").append(from).append(" -> ").append(dragToX).append(',').append(dragToY)
              .append(' ').append(dragStyle).append(']');
        } else if (clickMode == ClickMode.HOLD) {
            String at = positionMode == PositionMode.CURRENT_CURSOR ? "current cursor" : (x + "," + y);
            sb.append(" @ ").append(at).append(" for ").append(holdDurationMs).append(" ms");
        } else {
            String at = positionMode == PositionMode.CURRENT_CURSOR ? "current cursor" : (x + "," + y);
            sb.append(" @ ").append(at);
        }
        return sb.toString();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("button", button.name());
        m.put("clickMode", clickMode.name());
        m.put("positionMode", positionMode.name());
        m.put("x", x);
        m.put("y", y);
        m.put("dragToX", dragToX);
        m.put("dragToY", dragToY);
        m.put("dragStyle", dragStyle.name());
        m.put("dragSteps", dragSteps);
        m.put("dragDurationMs", dragDurationMs);
        m.put("holdDurationMs", holdDurationMs);
        m.put("positionJitterPx", positionJitterPx);
        return m;
    }

    public static MouseActionConfig fromMap(Map<String, Object> m) {
        MouseActionConfig c = new MouseActionConfig();
        if (m == null) {
            return c;
        }
        c.button = MouseButtonType.valueOf(str(m, "button", MouseButtonType.LEFT.name()));
        c.clickMode = ClickMode.valueOf(str(m, "clickMode", ClickMode.SINGLE.name()));
        c.positionMode = PositionMode.valueOf(str(m, "positionMode", PositionMode.CURRENT_CURSOR.name()));
        c.x = intOf(m, "x", 0);
        c.y = intOf(m, "y", 0);
        c.dragToX = intOf(m, "dragToX", 0);
        c.dragToY = intOf(m, "dragToY", 0);
        c.dragStyle = DragStyle.valueOf(str(m, "dragStyle", DragStyle.SMOOTH.name()));
        c.dragSteps = intOf(m, "dragSteps", 30);
        c.dragDurationMs = intOf(m, "dragDurationMs", 300);
        // holdDurationMs did not exist in older project files; intOf() already
        // falls back to the default below if the key is missing or malformed.
        c.holdDurationMs = intOf(m, "holdDurationMs", 0);
        // Also absent from older files - defaults to 0 (exact targeting).
        c.positionJitterPx = Math.max(0, intOf(m, "positionJitterPx", 0));
        return c;
    }

    static String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : String.valueOf(v);
    }

    static int intOf(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return (int) Double.parseDouble((String) v);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }
}
