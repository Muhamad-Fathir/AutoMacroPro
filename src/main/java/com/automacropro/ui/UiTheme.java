package com.automacropro.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;

/**
 * Small, central place for the handful of style choices reused across
 * panels/dialogs, so the app looks like one cohesive product instead of a
 * pile of default-grey Swing dialogs.
 *
 * Button colors are applied via {@link #createButton} using a small custom
 * paintComponent override, NOT via plain {@code setBackground()}. This is a
 * deliberate fix for a real, well-known Swing pitfall: several native
 * Look&amp;Feels - the Windows L&amp;F in particular - paint JButton
 * backgrounds using the OS theme engine and simply ignore
 * {@code setBackground()}, while still honoring {@code setForeground()}.
 * The visible symptom is exactly what was reported: white button text
 * rendered on the native default (light/white) background, unreadable.
 * Disabling content-area painting and filling the background ourselves
 * bypasses that native theming entirely, so the colors are guaranteed to
 * show correctly regardless of which L&amp;F is active.
 */
public final class UiTheme {

    public static final Color START_BG = new Color(0x2E, 0x7D, 0x32);   // deep green
    public static final Color START_FG = Color.WHITE;
    public static final Color STOP_BG = new Color(0xC6, 0x28, 0x28);    // deep red
    public static final Color STOP_FG = Color.WHITE;
    public static final Color TOGGLE_BG = new Color(0xFF, 0xC1, 0x07);  // amber
    public static final Color TOGGLE_FG = Color.BLACK;

    public static final Color NEUTRAL_TEXT = new Color(0x3A, 0x3A, 0x3A);
    public static final Color MUTED_TEXT = new Color(0x7A, 0x7A, 0x7A);

    public static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 15);
    public static final Font FONT_BODY = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font FONT_MONO_SMALL = new Font("Monospaced", Font.PLAIN, 12);

    /** Uniform size for the 3 main control buttons (Start/Stop/Toggle), per spec. */
    public static final Dimension MAIN_BUTTON_SIZE = new Dimension(150, 36);

    private UiTheme() {
    }

    /** Applies the most native-looking Look&Feel available, falling back gracefully. */
    public static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Stick with the cross-platform default - cosmetic only, never fatal.
        }
    }

    /**
     * Builds a flat, solid-colored button whose background paints correctly
     * under every Look&amp;Feel (see class javadoc). Used for Start/Stop/Toggle.
     */
    public static JButton createButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(isEnabled() ? getBackground() : getBackground().darker());
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        button.setContentAreaFilled(false); // we paint the background ourselves, above
        button.setOpaque(true);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFont(FONT_BODY.deriveFont(Font.BOLD));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(background.darker(), 1),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        button.setPreferredSize(MAIN_BUTTON_SIZE);
        button.setMinimumSize(MAIN_BUTTON_SIZE);
        return button;
    }

    /**
     * Same technique as {@link #createButton} (self-painted background, native
     * theming bypassed) but sized to its own text instead of the fixed main-button
     * size, and styled as a plain neutral button rather than a colored action
     * button. Used for controls like "Pick Location" that need the same
     * don't-trust-native-painting protection without looking like Start/Stop/Toggle.
     */
    public static JButton createNeutralButton(String text) {
        Color bg = new Color(0xE1, 0xE1, 0xE1);
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(isEnabled() ? getBackground() : getBackground().darker());
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setBackground(bg);
        button.setForeground(NEUTRAL_TEXT);
        button.setFont(FONT_BODY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker(), 1),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        return button;
    }
}
