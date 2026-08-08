package com.automacropro.util;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Lookup for all user-visible text.
 *
 * {@link ResourceBundle} rather than a hand-rolled JSON dictionary: it is in the
 * JDK, it already handles the bundled-in-jar lookup and the fall back to the
 * default bundle for a missing key, and the {@code .properties} files sit beside
 * the fonts in resources with no parser to write or maintain.
 *
 * <h3>English is the default</h3>
 * The UI was written in Indonesian, so {@code messages.properties} (the fallback
 * every unresolved key lands on) holds the <b>English</b> text and
 * {@code messages_id.properties} holds the original Indonesian. That way a key
 * missing from the Indonesian file degrades to English rather than to a raw key
 * name.
 *
 * <h3>Reading, not switching, is what needs care</h3>
 * The active locale is read once at startup and applied as components are built.
 * Switching it re-creates the window, because Swing components cache their text
 * at construction time - walking a live tree and re-setting every string is
 * where a half-translated UI comes from.
 */
public final class I18n {

    /**
     * Locales offered in the UI. English first: it is the default.
     *
     * {@code new Locale(...)} rather than {@code Locale.of(...)} - the latter is
     * Java 19+, and this project targets release 17 (see pom.xml).
     */
    public static final Locale ENGLISH = Locale.ENGLISH;
    @SuppressWarnings("deprecation")
    public static final Locale INDONESIAN = new Locale("id");

    private static final String BUNDLE = "i18n.messages";

    private static volatile ResourceBundle bundle = load(ENGLISH);
    private static volatile Locale current = ENGLISH;

    private I18n() {
    }

    private static ResourceBundle load(Locale locale) {
        // Locale.setDefault is deliberately NOT touched - that would change
        // number and date formatting app-wide as a side effect of a UI language
        // choice, which is a much bigger blast radius than intended.
        return ResourceBundle.getBundle(BUNDLE, locale);
    }

    public static void setLocale(Locale locale) {
        Locale target = locale == null ? ENGLISH : locale;
        bundle = load(target);
        current = target;
        AppLogger.info("UI language set to " + target.toLanguageTag());
    }

    public static Locale getLocale() {
        return current;
    }

    /**
     * The string for {@code key}.
     *
     * A missing key returns the key itself wrapped in {@code !...!} instead of
     * throwing: a typo in a label should look obviously wrong on screen, not
     * take down the panel being built. It also gets logged so it is findable.
     */
    public static String t(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException ex) {
            AppLogger.warn("Missing i18n key: " + key, null);
            return "!" + key + "!";
        }
    }

    /** {@link #t(String)} with {@code {0}}-style placeholders substituted. */
    public static String t(String key, Object... args) {
        String pattern = t(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        // MessageFormat with the CURRENT locale, so a substituted number is
        // grouped the way that language expects.
        return new java.text.MessageFormat(pattern, current).format(args);
    }
}
