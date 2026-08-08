package com.automacropro.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A complete macro sequence: an ordered list of {@link MacroStep}s plus its
 * loop behaviour. This is the object that gets exported/imported as a
 * {@code .amacro} (JSON) project file via {@code MacroProjectIO}.
 */
public class MacroProject {

    /**
     * Bumped 2 -> 3 when per-step {@code preDelayMs} was added.
     * Bumped 3 -> 4 when {@code SCROLL} action type was added.
     *
     * Nothing reads this value: {@link #fromMap} tolerates missing keys per
     * field, so older files load correctly without a version check and the
     * addition is purely additive. It is maintained as an honest marker for
     * anyone inspecting a .amacro file by hand.
     */
    public static final int FILE_FORMAT_VERSION = 4;

    private String name = "Untitled Macro";
    private List<MacroStep> steps = new ArrayList<>();
    private LoopMode loopMode = LoopMode.ONCE;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<MacroStep> getSteps() {
        return steps;
    }

    public void setSteps(List<MacroStep> steps) {
        this.steps = steps;
    }

    public LoopMode getLoopMode() {
        return loopMode;
    }

    public void setLoopMode(LoopMode loopMode) {
        this.loopMode = loopMode;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("formatVersion", FILE_FORMAT_VERSION);
        m.put("name", name);
        m.put("loopMode", loopMode.name());
        List<Object> stepMaps = new ArrayList<>();
        for (MacroStep s : steps) {
            stepMaps.add(s.toMap());
        }
        m.put("steps", stepMaps);
        return m;
    }

    /**
     * Rebuilds a project from a parsed JSON map. Unknown / missing keys all
     * fall back to safe defaults so older project files (saved by an earlier
     * version of this app, e.g. before the HOLD click mode existed) keep
     * loading correctly instead of throwing.
     */
    @SuppressWarnings("unchecked")
    public static MacroProject fromMap(Map<String, Object> m) {
        MacroProject p = new MacroProject();
        if (m == null) {
            return p;
        }
        Object n = m.get("name");
        if (n != null) {
            p.name = String.valueOf(n);
        }
        Object lm = m.get("loopMode");
        if (lm != null) {
            try {
                p.loopMode = LoopMode.valueOf(String.valueOf(lm));
            } catch (IllegalArgumentException ignored) {
                p.loopMode = LoopMode.ONCE;
            }
        }
        Object rawSteps = m.get("steps");
        if (rawSteps instanceof List) {
            for (Object o : (List<Object>) rawSteps) {
                if (o instanceof Map) {
                    try {
                        p.steps.add(MacroStep.fromMap((Map<String, Object>) o));
                    } catch (Exception ex) {
                        // Skip a single corrupted/unrecognized step rather than
                        // failing the whole project load.
                        com.automacropro.util.AppLogger.warn(
                                "Melewati satu step yang tidak terbaca saat memuat project", ex);
                    }
                }
            }
        }
        return p;
    }
}
