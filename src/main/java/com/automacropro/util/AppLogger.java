package com.automacropro.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Minimal, dependency-free file logger. Intentionally lightweight - this is
 * meant to catch and record execution failures (e.g. a Robot call throwing,
 * a hook registration failing) for later inspection, not to be a full
 * logging framework.
 */
public final class AppLogger {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Object LOCK = new Object();

    private AppLogger() {
    }

    /** Resolves (and creates if needed) the per-user folder this app stores its files in. */
    public static Path getAppDataDir() {
        String appData = System.getenv("APPDATA");
        Path base = (appData != null && !appData.isBlank())
                ? Paths.get(appData, "AutoMacroPro")
                : Paths.get(System.getProperty("user.home"), ".automacropro");
        try {
            Files.createDirectories(base);
        } catch (IOException ignored) {
            // Fall back silently to current directory if we truly cannot create it.
        }
        return base;
    }

    private static Path logFile() {
        return getAppDataDir().resolve("automacropro.log");
    }

    public static void info(String message) {
        write("INFO", message, null);
    }

    public static void warn(String message, Throwable t) {
        write("WARN", message, t);
    }

    public static void error(String message, Throwable t) {
        write("ERROR", message, t);
    }

    private static void write(String level, String message, Throwable t) {
        String line = "[" + LocalDateTime.now().format(TS) + "] [" + level + "] " + message;
        // Always echo to stderr/stdout too, useful when running from a console/IDE.
        if ("ERROR".equals(level) || "WARN".equals(level)) {
            System.err.println(line);
        } else {
            System.out.println(line);
        }
        synchronized (LOCK) {
            try (Writer w = Files.newBufferedWriter(logFile(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(line);
                w.write(System.lineSeparator());
                if (t != null) {
                    StringWriter sw = new StringWriter();
                    t.printStackTrace(new PrintWriter(sw));
                    w.write(sw.toString());
                    w.write(System.lineSeparator());
                }
            } catch (IOException ioEx) {
                // Logging must never crash the app; best effort only.
                System.err.println("AppLogger: gagal menulis log file: " + ioEx.getMessage());
            }
        }
    }
}
