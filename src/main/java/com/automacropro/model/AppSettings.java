package com.automacropro.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything that "Save Settings" / "Reset Settings" persists locally
 * (NOT the macro project itself - that has its own explicit Export/Import,
 * see {@code MacroProjectIO}).
 */
public class AppSettings {

    public static final String HK_AUTOCLICKER_START = "autoclicker.start";
    public static final String HK_AUTOCLICKER_STOP = "autoclicker.stop";
    public static final String HK_AUTOCLICKER_TOGGLE = "autoclicker.toggle";
    public static final String HK_MACRO_START = "macro.start";
    public static final String HK_MACRO_STOP = "macro.stop";
    public static final String HK_MACRO_TOGGLE = "macro.toggle";
    /** Start/stop the Macro Recorder. */
    public static final String HK_MACRO_RECORD = "macro.record";
    /** Window Manager: re-scan the window list. */
    public static final String HK_WM_REFRESH = "windowmanager.refresh";
    /** Window Manager: make the selected window borderless. */
    public static final String HK_WM_BORDERLESS = "windowmanager.borderless";

    private AutoClickerSettings autoClickerSettings = AutoClickerSettings.defaults();
    private LoopMode lastMacroLoopMode = LoopMode.ONCE;
    private boolean failsafeEnabled = true;
    private Map<String, HotkeyBinding> hotkeys = new LinkedHashMap<>();

    /**
     * UI language as a BCP-47 tag ("en", "id"). Defaults to English - see
     * {@code I18n}. Stored as a tag rather than a Locale so the JSON stays
     * human-editable and the model keeps no dependency on the i18n layer.
     */
    private String languageTag = "en";

    public AppSettings() {
        // sensible factory defaults so the app is usable before the user sets anything
        hotkeys.put(HK_AUTOCLICKER_START, HotkeyBinding.fromMap(HK_AUTOCLICKER_START, null));
        hotkeys.put(HK_AUTOCLICKER_STOP, HotkeyBinding.fromMap(HK_AUTOCLICKER_STOP, null));
        hotkeys.put(HK_AUTOCLICKER_TOGGLE, HotkeyBinding.fromMap(HK_AUTOCLICKER_TOGGLE, null));
        hotkeys.put(HK_MACRO_START, HotkeyBinding.fromMap(HK_MACRO_START, null));
        hotkeys.put(HK_MACRO_STOP, HotkeyBinding.fromMap(HK_MACRO_STOP, null));
        hotkeys.put(HK_MACRO_TOGGLE, HotkeyBinding.fromMap(HK_MACRO_TOGGLE, null));
        hotkeys.put(HK_MACRO_RECORD, HotkeyBinding.fromMap(HK_MACRO_RECORD, null));
        hotkeys.put(HK_WM_REFRESH, HotkeyBinding.fromMap(HK_WM_REFRESH, null));
        hotkeys.put(HK_WM_BORDERLESS, HotkeyBinding.fromMap(HK_WM_BORDERLESS, null));
    }

    public AutoClickerSettings getAutoClickerSettings() {
        return autoClickerSettings;
    }

    public void setAutoClickerSettings(AutoClickerSettings autoClickerSettings) {
        this.autoClickerSettings = autoClickerSettings;
    }

    public LoopMode getLastMacroLoopMode() {
        return lastMacroLoopMode;
    }

    public void setLastMacroLoopMode(LoopMode lastMacroLoopMode) {
        this.lastMacroLoopMode = lastMacroLoopMode;
    }

    public boolean isFailsafeEnabled() {
        return failsafeEnabled;
    }

    public void setFailsafeEnabled(boolean failsafeEnabled) {
        this.failsafeEnabled = failsafeEnabled;
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public void setLanguageTag(String languageTag) {
        this.languageTag = (languageTag == null || languageTag.isBlank()) ? "en" : languageTag;
    }

    public HotkeyBinding getHotkey(String id) {
        return hotkeys.computeIfAbsent(id, k -> HotkeyBinding.fromMap(k, null));
    }

    public void setHotkey(HotkeyBinding binding) {
        hotkeys.put(binding.getId(), binding);
    }

    public Map<String, HotkeyBinding> getAllHotkeys() {
        return hotkeys;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("autoClickerSettings", autoClickerSettings.toMap());
        m.put("lastMacroLoopMode", lastMacroLoopMode.name());
        m.put("failsafeEnabled", failsafeEnabled);
        m.put("languageTag", languageTag);
        Map<String, Object> hkMap = new LinkedHashMap<>();
        for (Map.Entry<String, HotkeyBinding> e : hotkeys.entrySet()) {
            hkMap.put(e.getKey(), e.getValue().toMap());
        }
        m.put("hotkeys", hkMap);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static AppSettings fromMap(Map<String, Object> m) {
        AppSettings s = new AppSettings();
        if (m == null) {
            return s;
        }
        Object acs = m.get("autoClickerSettings");
        if (acs instanceof Map) {
            s.autoClickerSettings = AutoClickerSettings.fromMap((Map<String, Object>) acs);
        }
        Object lm = m.get("lastMacroLoopMode");
        if (lm != null) {
            try {
                s.lastMacroLoopMode = LoopMode.valueOf(String.valueOf(lm));
            } catch (IllegalArgumentException ignored) {
            }
        }
        Object fs = m.get("failsafeEnabled");
        s.failsafeEnabled = !(fs instanceof Boolean) || (Boolean) fs; // default true if missing/old file
        Object lang = m.get("languageTag");
        if (lang != null) {
            s.setLanguageTag(String.valueOf(lang));
        }
        Object hk = m.get("hotkeys");
        if (hk instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) hk).entrySet()) {
                if (e.getValue() instanceof Map) {
                    s.hotkeys.put(e.getKey(), HotkeyBinding.fromMap(e.getKey(), (Map<String, Object>) e.getValue()));
                }
            }
        }
        return s;
    }
}
