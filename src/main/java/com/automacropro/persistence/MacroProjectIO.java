package com.automacropro.persistence;

import com.automacropro.json.SimpleJson;
import com.automacropro.model.MacroProject;
import com.automacropro.util.AppLogger;
import com.automacropro.util.I18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Export/Import for macro sequences. Files use the custom extension
 * {@code .amacro} but the content is plain, human-readable JSON, so a
 * project can also be opened/edited in a text editor if needed.
 *
 * Loading is intentionally tolerant: a file saved by an older version of
 * the app (e.g. before the HOLD click mode existed) must still load
 * without throwing - missing fields fall back to safe defaults inside
 * {@link MacroProject#fromMap}, {@code MacroStep#fromMap} and
 * {@code MouseActionConfig#fromMap}.
 */
public final class MacroProjectIO {

    public static final String EXTENSION = "amacro";

    private MacroProjectIO() {
    }

    public static void save(MacroProject project, Path file) throws IOException {
        String json = SimpleJson.write(project.toMap());
        Files.writeString(file, json);
    }

    /**
     * @return the loaded project, or an empty/default project if the file
     *         could not be read or parsed at all (never throws to the caller -
     *         the UI layer should still inform the user via the boolean-ish
     *         {@code wasSuccessful} pattern below if it needs to distinguish
     *         "empty new project" from "load failed").
     */
    public static LoadResult load(Path file) {
        try {
            String json = Files.readString(file);
            Object parsed = SimpleJson.parse(json);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                MacroProject project = MacroProject.fromMap((Map<String, Object>) parsed);
                return new LoadResult(project, true, null);
            }
            return new LoadResult(new MacroProject(), false, I18n.t("io.notJson"));
        } catch (IOException ex) {
            AppLogger.error("Gagal membaca file project: " + file, ex);
            return new LoadResult(new MacroProject(), false, I18n.t("io.readFailed", ex.getMessage()));
        } catch (RuntimeException ex) {
            // Covers SimpleJson.JsonParseException and any unexpected parsing issue.
            AppLogger.error("Gagal mem-parse file project: " + file, ex);
            return new LoadResult(new MacroProject(), false, I18n.t("io.corrupt", ex.getMessage()));
        }
    }

    public static final class LoadResult {
        public final MacroProject project;
        public final boolean success;
        public final String errorMessage;

        public LoadResult(MacroProject project, boolean success, String errorMessage) {
            this.project = project;
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }
}
