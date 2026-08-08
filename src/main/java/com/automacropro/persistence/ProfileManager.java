package com.automacropro.persistence;

import com.automacropro.json.SimpleJson;
import com.automacropro.model.AutoClickerSettings;
import com.automacropro.util.AppLogger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Named Autoclicker presets ("RPG Farming", "Idle Game", ...), one JSON file
 * each under {@code profiles/} beside settings.json.
 *
 * A file per profile rather than one combined file: saving or deleting one
 * preset then cannot corrupt the others, and a user can hand-copy a single
 * profile between machines. Reuses {@link AutoClickerSettings#toMap()} so a
 * profile is exactly the settings payload already persisted for the main
 * config - no second serialization format to keep in sync.
 */
public final class ProfileManager {

    private static final String DIR_NAME = "profiles";
    private static final String EXTENSION = ".json";

    private ProfileManager() {
    }

    private static Path directory() throws IOException {
        Path dir = AppLogger.getAppDataDir().resolve(DIR_NAME);
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Strips anything that is not safe in a filename.
     *
     * A profile name is user-typed free text and becomes a path, so this is a
     * trust boundary: every character outside the allowlist - including the
     * {@code .} and {@code /} of a {@code ../../} traversal attempt - is
     * replaced, so the result can only ever name a file directly inside the
     * profiles directory.
     */
    static String toFileName(String profileName) {
        String cleaned = profileName == null ? "" : profileName.trim().replaceAll("[^a-zA-Z0-9 _-]", "_");
        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64);
        }
        return cleaned;
    }

    /**
     * True only for a name that is meaningful as a profile.
     *
     * Sanitizing alone is not enough to accept a name: {@code "../../evil"}
     * sanitizes to {@code ".._.._evil"}, which is harmless but is also not a
     * name the user meant to type, and silently storing it under a mangled
     * spelling is worse than refusing. So a valid name must contain at least
     * one alphanumeric character of its own - which rejects traversal attempts
     * and all-punctuation input while leaving every ordinary name
     * ("RPG Farming", "idle-game_2") untouched.
     */
    public static boolean isValidName(String profileName) {
        if (profileName == null) {
            return false;
        }
        String trimmed = profileName.trim();
        if (trimmed.isEmpty() || toFileName(trimmed).isBlank()) {
            return false;
        }
        return trimmed.chars().anyMatch(Character::isLetterOrDigit)
                && trimmed.indexOf('/') < 0
                && trimmed.indexOf('\\') < 0
                && !trimmed.contains("..");
    }

    /** Profile names, sorted, derived from the filenames present. */
    public static List<String> list() {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory(), "*" + EXTENSION)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                names.add(fileName.substring(0, fileName.length() - EXTENSION.length()));
            }
        } catch (IOException ex) {
            AppLogger.warn("Gagal membaca daftar profile", ex);
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    public static boolean save(String profileName, AutoClickerSettings settings) {
        // Gate on isValidName, not merely on a non-blank sanitized form: the
        // stricter check is the one that rejects traversal and punctuation-only
        // names, and every write must apply it, not just the UI path.
        if (!isValidName(profileName)) {
            AppLogger.warn("Nama profile tidak valid: " + profileName, null);
            return false;
        }
        String fileName = toFileName(profileName);
        try {
            Files.writeString(directory().resolve(fileName + EXTENSION), SimpleJson.write(settings.toMap()));
            return true;
        } catch (IOException ex) {
            AppLogger.error("Gagal menyimpan profile " + fileName, ex);
            return false;
        }
    }

    /** Loads a profile, or null if it is missing or unreadable. */
    @SuppressWarnings("unchecked")
    public static AutoClickerSettings load(String profileName) {
        if (!isValidName(profileName)) {
            return null;
        }
        String fileName = toFileName(profileName);
        try {
            Path file = directory().resolve(fileName + EXTENSION);
            if (!Files.exists(file)) {
                return null;
            }
            Object parsed = SimpleJson.parse(Files.readString(file));
            if (parsed instanceof Map) {
                return AutoClickerSettings.fromMap((Map<String, Object>) parsed);
            }
        } catch (IOException ex) {
            AppLogger.error("Gagal membaca profile " + fileName, ex);
        } catch (RuntimeException ex) {
            // Hand-edited or truncated file - report as "not loadable" rather than crash.
            AppLogger.warn("Profile " + fileName + " tidak valid", ex);
        }
        return null;
    }

    public static boolean delete(String profileName) {
        if (!isValidName(profileName)) {
            return false;
        }
        String fileName = toFileName(profileName);
        try {
            return Files.deleteIfExists(directory().resolve(fileName + EXTENSION));
        } catch (IOException ex) {
            AppLogger.error("Gagal menghapus profile " + fileName, ex);
            return false;
        }
    }
}
