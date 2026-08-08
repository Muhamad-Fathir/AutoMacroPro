package com.automacropro.ui;

import com.automacropro.util.AppLogger;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.io.InputStream;

/**
 * Central design system: palette, offline-bundled fonts, and the dark
 * Look&amp;Feel install.
 *
 * <h3>Why Metal and not the system L&amp;F</h3>
 * The Windows L&amp;F paints component backgrounds through the OS theme engine
 * and ignores {@code setBackground()} - that is why the buttons below still
 * paint their own background. The same limitation means it also ignores the
 * {@code UIManager} colour defaults a dark theme depends on, so a dark mode
 * built on top of it would only ever be half-applied. Metal reads its colours
 * from a pluggable {@link javax.swing.plaf.metal.MetalTheme}, so overriding
 * ~8 methods in {@link DarkTheme} recolours <em>every</em> Swing component in
 * the app - tabs, scrollbars, spinners, dialogs - without touching a single
 * panel class. Metal also renders entirely in Java2D rather than from
 * per-DPI-cached native icon resources, which removes the root cause of the
 * mixed-DPI painting bug that MainFrame works around.
 *
 * <h3>Glass</h3>
 * CSS glassmorphism blurs whatever is behind the panel. Swing has no
 * backdrop-filter, but our backdrop is the flat {@link #BG_DEEP} - and a blur
 * of a flat colour is that same flat colour. So {@link GlassPanel} just alpha
 * -composites {@link #GLASS_FILL} over it, which is pixel-for-pixel what a
 * real blur would produce here, for none of the cost.
 */
public final class UiTheme {

    // --- palette (spec) -------------------------------------------------------
    /** Deep Blue app background. */
    public static final Color BG_DEEP = new Color(0x0B, 0x1A, 0x2B);
    /** One step up from the background: text fields, lists, input surfaces. */
    public static final Color SURFACE = new Color(0x12, 0x29, 0x3D);
    /** rgba(255,255,255,0.06) */
    public static final Color GLASS_FILL = new Color(255, 255, 255, 15);
    /** rgba(168,235,18,0.18) */
    public static final Color GLASS_BORDER = new Color(0xA8, 0xEB, 0x12, 46);

    /** Vivid Lime Green - primary/highlight. */
    public static final Color ACCENT = new Color(0xA8, 0xEB, 0x12);
    /** Astronaut - secondary/hover. */
    public static final Color ASTRONAUT = new Color(0x41, 0x4F, 0x6C);
    /** Inch Worm - secondary/hover. */
    public static final Color INCH_WORM = new Color(0x00, 0x87, 0x93);
    /** Dark Cerulean - deep accent. */
    public static final Color CERULEAN = new Color(0x00, 0x4D, 0x7A);

    public static final Color TEXT = new Color(0xE6, 0xEE, 0xF6);
    public static final Color NEUTRAL_TEXT = TEXT;
    public static final Color MUTED_TEXT = new Color(0x8C, 0x9E, 0xB2);
    public static final Color DANGER = new Color(0xE5, 0x48, 0x4D);

    public static final Color START_BG = ACCENT;
    public static final Color START_FG = new Color(0x0B, 0x1A, 0x2B);
    public static final Color STOP_BG = DANGER;
    public static final Color STOP_FG = Color.WHITE;
    public static final Color TOGGLE_BG = INCH_WORM;
    public static final Color TOGGLE_FG = Color.WHITE;

    // --- typography -----------------------------------------------------------
    /** Space Grotesk - titles, metrics, numbers. */
    public static final Font FONT_TITLE;
    /** Poppins - body, notes. */
    public static final Font FONT_BODY;
    public static final Font FONT_MONO_SMALL = new Font("Consolas", Font.PLAIN, 12);

    /** Uniform size for the 3 main control buttons (Start/Stop/Toggle), per spec. */
    public static final Dimension MAIN_BUTTON_SIZE = new Dimension(150, 36);

    private static final int GLASS_RADIUS = 14;

    static {
        Font display = loadFont("SpaceGrotesk-Bold.ttf");
        Font body = loadFont("Poppins-Regular.ttf");
        FONT_TITLE = display != null ? display.deriveFont(Font.BOLD, 15f)
                : new Font("SansSerif", Font.BOLD, 15);
        FONT_BODY = body != null ? body.deriveFont(Font.PLAIN, 13f)
                : new Font("SansSerif", Font.PLAIN, 13);
    }

    private UiTheme() {
    }

    /**
     * Loads a .ttf from {@code src/main/resources/fonts/} and registers it so
     * it is also resolvable by name (needed for HTML-in-JLabel rendering).
     * Fonts are bundled in the jar - nothing is fetched at runtime. A missing
     * or corrupt file degrades to the SansSerif fallback rather than failing
     * startup, because a font is never worth crashing over.
     */
    private static Font loadFont(String fileName) {
        try (InputStream in = UiTheme.class.getResourceAsStream("/fonts/" + fileName)) {
            if (in == null) {
                AppLogger.info("Font /fonts/" + fileName + " not bundled - falling back to SansSerif");
                return null;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (Exception ex) {
            AppLogger.error("Failed to load bundled font " + fileName, ex);
            return null;
        }
    }

    /** Installs Metal + the dark theme. Must run before any component is created. */
    public static void installLookAndFeel() {
        try {
            UIManager.put("swing.boldMetal", Boolean.FALSE); // Metal bolds all text by default
            MetalLookAndFeel.setCurrentTheme(new DarkTheme());
            UIManager.setLookAndFeel(new MetalLookAndFeel());
            applyDarkDefaults();
        } catch (Exception ex) {
            AppLogger.error("Failed to install dark Look&Feel - continuing with the default", ex);
        }
    }

    /**
     * The handful of keys Metal derives from its theme in a way that reads
     * wrong when the theme is inverted (it treats {@code getWhite()} as both
     * "input background" and "3D highlight edge").
     */
    private static void applyDarkDefaults() {
        UIManager.put("TextField.background", new ColorUIResource(SURFACE));
        UIManager.put("TextField.foreground", new ColorUIResource(TEXT));
        UIManager.put("TextField.caretForeground", new ColorUIResource(ACCENT));
        UIManager.put("TextArea.background", new ColorUIResource(SURFACE));
        UIManager.put("TextArea.foreground", new ColorUIResource(TEXT));
        UIManager.put("List.background", new ColorUIResource(SURFACE));
        UIManager.put("List.foreground", new ColorUIResource(TEXT));
        UIManager.put("List.selectionBackground", new ColorUIResource(INCH_WORM));
        UIManager.put("List.selectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("TitledBorder.titleColor", new ColorUIResource(ACCENT));
        UIManager.put("ToolTip.background", new ColorUIResource(ASTRONAUT));
        UIManager.put("ToolTip.foreground", new ColorUIResource(TEXT));
        UIManager.put("ScrollPane.background", new ColorUIResource(BG_DEEP));
        UIManager.put("Viewport.background", new ColorUIResource(BG_DEEP));
        UIManager.put("TabbedPane.selected", new ColorUIResource(ASTRONAUT));
        UIManager.put("TabbedPane.contentAreaColor", new ColorUIResource(BG_DEEP));
    }

    /** Accent-coloured titled border - the "categorise settings" grouping from the spec. */
    public static Border titled(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(GLASS_BORDER), title);
        border.setTitleColor(ACCENT);
        border.setTitleFont(FONT_TITLE.deriveFont(12f));
        return BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(4, 8, 6, 8));
    }

    /**
     * Makes a container tree non-opaque so a {@link GlassPanel} ancestor shows
     * through. Needed because Metal panels/labels are opaque by default and
     * would paint {@link #BG_DEEP} straight over the glass. Leaves inputs
     * (text fields, lists) alone - those are meant to read as solid surfaces.
     */
    public static void deopaque(Component component) {
        if (component instanceof javax.swing.JPanel || component instanceof javax.swing.JLabel
                || component instanceof javax.swing.JRadioButton || component instanceof javax.swing.JCheckBox) {
            ((javax.swing.JComponent) component).setOpaque(false);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                deopaque(child);
            }
        }
    }

    /**
     * Builds a flat, solid-coloured button whose background paints correctly
     * under every Look&amp;Feel - see class javadoc. Used for Start/Stop/Toggle.
     */
    public static JButton createButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getBackground();
                if (!isEnabled()) {
                    bg = blend(bg, BG_DEEP, 0.6f);
                } else if (getModel().isRollover()) {
                    bg = bg.brighter();
                }
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }

            /**
             * {@link #MAIN_BUTTON_SIZE} acts as a floor, not a fixed size.
             *
             * A hard {@code setPreferredSize(MAIN_BUTTON_SIZE)} is what
             * truncated a label like "Start  [Ctrl+Backspace]" to
             * "Start [Ctrl+Back ...". Taking the max keeps the tidy uniform
             * width for short labels while letting a long hotkey suffix grow
             * the button instead of clipping it.
             */
            @Override
            public Dimension getPreferredSize() {
                Dimension natural = super.getPreferredSize();
                return new Dimension(
                        Math.max(natural.width, MAIN_BUTTON_SIZE.width),
                        Math.max(natural.height, MAIN_BUTTON_SIZE.height));
            }
        };
        button.setContentAreaFilled(false); // we paint the background ourselves, above
        button.setOpaque(false);            // rounded corners must let the parent show through
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFont(FONT_TITLE.deriveFont(13f));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        button.setMinimumSize(MAIN_BUTTON_SIZE);
        return button;
    }

    /**
     * Same self-painted technique as {@link #createButton}, but sized purely to
     * its own text - it must NOT inherit the main-button width floor, or every
     * small button ("Load", "Save", "X:") would be padded out to 150px.
     */
    public static JButton createNeutralButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = isEnabled() ? (getModel().isRollover() ? ASTRONAUT.brighter() : ASTRONAUT)
                        : blend(ASTRONAUT, BG_DEEP, 0.6f);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBackground(ASTRONAUT);
        button.setForeground(TEXT);
        button.setFont(FONT_BODY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        return button;
    }

    static int glassRadius() {
        return GLASS_RADIUS;
    }

    private static Color blend(Color a, Color b, float bWeight) {
        float aw = 1f - bWeight;
        return new Color(
                Math.round(a.getRed() * aw + b.getRed() * bWeight),
                Math.round(a.getGreen() * aw + b.getGreen() * bWeight),
                Math.round(a.getBlue() * aw + b.getBlue() * bWeight));
    }

    /**
     * Inverted Metal theme. Metal asks its theme for 8 semantic colours and
     * derives every component colour from them, so this is the whole dark mode:
     * {@code secondary3} is the control background, {@code black} is the
     * foreground (hence a near-white value), {@code white} is the input/highlight
     * surface (hence a dark one).
     */
    private static final class DarkTheme extends DefaultMetalTheme {
        private final FontUIResource controlFont = new FontUIResource(FONT_BODY);
        private final FontUIResource titleFont = new FontUIResource(FONT_TITLE);
        private final FontUIResource smallFont = new FontUIResource(FONT_BODY.deriveFont(11f));

        @Override public String getName() { return "AutoMacro Dark"; }

        @Override protected ColorUIResource getPrimary1() { return new ColorUIResource(CERULEAN); }
        @Override protected ColorUIResource getPrimary2() { return new ColorUIResource(ASTRONAUT); }
        @Override protected ColorUIResource getPrimary3() { return new ColorUIResource(INCH_WORM); }
        @Override protected ColorUIResource getSecondary1() { return new ColorUIResource(0x07121E); }
        @Override protected ColorUIResource getSecondary2() { return new ColorUIResource(SURFACE); }
        @Override protected ColorUIResource getSecondary3() { return new ColorUIResource(BG_DEEP); }
        @Override protected ColorUIResource getBlack() { return new ColorUIResource(TEXT); }
        @Override protected ColorUIResource getWhite() { return new ColorUIResource(SURFACE); }

        @Override public ColorUIResource getControlTextColor() { return new ColorUIResource(TEXT); }
        @Override public ColorUIResource getInactiveControlTextColor() { return new ColorUIResource(MUTED_TEXT); }
        @Override public ColorUIResource getSystemTextColor() { return new ColorUIResource(TEXT); }
        @Override public ColorUIResource getUserTextColor() { return new ColorUIResource(TEXT); }
        @Override public ColorUIResource getFocusColor() { return new ColorUIResource(ACCENT); }

        @Override public FontUIResource getControlTextFont() { return controlFont; }
        @Override public FontUIResource getSystemTextFont() { return controlFont; }
        @Override public FontUIResource getUserTextFont() { return controlFont; }
        @Override public FontUIResource getMenuTextFont() { return controlFont; }
        @Override public FontUIResource getWindowTitleFont() { return titleFont; }
        @Override public FontUIResource getSubTextFont() { return smallFont; }
    }
}
