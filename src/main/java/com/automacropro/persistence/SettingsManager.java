package com.automacropro.persistence;

import com.automacropro.json.SimpleJson;
import com.automacropro.model.AppSettings;
import com.automacropro.util.AppLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Persists {@link AppSettings} (Autoclicker params + all hotkey bindings)
 * to a single local file so they survive an app restart. Backs the
 * "Save Settings" / "Reset Settings" controls of both modules.
 */
public final class SettingsManager {

    private static final String FILE_NAME = "settings.json";

    private SettingsManager() {
    }

    private static Path filePath() {
        return AppLogger.getAppDataDir().resolve(FILE_NAME);
    }

    /** Loads saved settings, or sensible defaults if no file exists yet / it is unreadable. */
    @SuppressWarnings("unchecked")
    public static AppSettings load() {
        Path path = filePath();
        if (!Files.exists(path)) {
            return new AppSettings();
        }
        try {
            String json = Files.readString(path);
            Object parsed = SimpleJson.parse(json);
            if (parsed instanceof Map) {
                return AppSettings.fromMap((Map<String, Object>) parsed);
            }
        } catch (IOException ex) {
            AppLogger.warn("Gagal membaca settings.json, memakai default", ex);
        } catch (RuntimeException ex) {
            // Malformed JSON (e.g. hand-edited file) -> fall back instead of crashing the app.
            AppLogger.warn("settings.json tidak valid, memakai default", ex);
        }
        return new AppSettings();
    }

    public static boolean save(AppSettings settings) {
        try {
            String json = SimpleJson.write(settings.toMap());
            Files.writeString(filePath(), json);
            return true;
        } catch (IOException ex) {
            AppLogger.error("Gagal menyimpan settings.json", ex);
            return false;
        }
    }
}
