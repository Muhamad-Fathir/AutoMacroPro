package com.automacropro.ui;

import com.automacropro.hotkey.GlobalHotkeyManager;
import com.automacropro.model.AppSettings;
import com.automacropro.persistence.SettingsManager;
import com.automacropro.util.AppLogger;
import com.automacropro.util.I18n;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Top-level window: one tab per module. Owns the application-wide lifecycle
 * of the JNativeHook global hook (initialized here once at startup,
 * unregistered here once on window close) so no module panel has to worry
 * about it.
 *
 * <p>The former mixed-DPI {@code updateComponentTreeUI} workaround is gone
 * along with the Windows Look&amp;Feel it existed for. That bug came from
 * WindowsLookAndFeel painting component chrome from native icon resources
 * cached per-DPI-scale-factor, which could be stale for whichever monitor the
 * window ended up on. {@link UiTheme} now installs Metal, which renders
 * everything in Java2D from theme colours with no native per-scale resource
 * cache, so there is nothing left to invalidate.
 */
public class MainFrame extends JFrame {

    /**
     * Build marker for verifying which copy of the source is actually running.
     * Shows up in the window title AND in automacropro.log at startup. If you
     * rebuild and this marker does NOT change, NetBeans/Maven is not compiling
     * the file you think it is - see the packaging notes in README.md.
     */
    private static final String BUILD_MARKER = "2.0 Dev";

    public MainFrame() {
        super(I18n.t("app.title") + "  [" + BUILD_MARKER + "]");
        AppLogger.info("=== MainFrame() start - " + BUILD_MARKER + " ===");
        installWindowIcons();

        AppSettings appSettings = SettingsManager.load();
        GlobalHotkeyManager.getInstance().initialize();

        JTabbedPane tabs = new JTabbedPane();
        AutoClickerPanel clickerPanel = new AutoClickerPanel(appSettings);
        tabs.addTab(I18n.t("tab.autoclicker"), clickerPanel);
        MacroSequencerPanel macroPanel = new MacroSequencerPanel(appSettings);
        tabs.addTab(I18n.t("tab.macro"), macroPanel);
        WindowManagerPanel windowPanel = new WindowManagerPanel(appSettings);
        tabs.addTab(I18n.t("tab.windowManager"), windowPanel);
        tabs.setBackground(UiTheme.BG_DEEP);

        JPanel root = new JPanel(new BorderLayout());
        root.setOpaque(false);
        root.add(buildTopBar(appSettings, clickerPanel, macroPanel, windowPanel), BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        setContentPane(root);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                GlobalHotkeyManager.getInstance().shutdown();
                dispose();
                System.exit(0);
            }
        });

        // Size from what the content actually needs, not a magic number.
        // A hardcoded width is a latent version of the bug that hid the "Import
        // Project..." button: the required width depends on the active font and
        // the DPI scale, and it will grow again the moment the Indonesian strings
        // land, so measuring beats guessing. pack() asks the layout, then the
        // floor keeps small sequences from opening in a cramped window and the
        // ceiling keeps it on screen on a small display.
        pack();
        Rectangle usable = getGraphicsConfiguration().getBounds();
        Dimension packed = getSize();
        setSize(
                Math.min(Math.max(packed.width, 900), usable.width - 80),
                Math.min(Math.max(packed.height, 700), usable.height - 80));
        // Minimum tracks the packed width so no control can be resized out of
        // sight - the failure mode that made the wrapped button unrecoverable.
        setMinimumSize(new Dimension(Math.min(packed.width, usable.width - 80), 600));
        setLocationRelativeTo(null);
    }

    /**
     * Installs the multi-size window icon set (title bar + taskbar) from the
     * bundled PNG resources.
     *
     * <p>Each size is loaded independently and skipped - with a log warning -
     * when its resource is missing or unreadable, so a single absent icon can
     * never prevent the app from starting. Passing the whole list lets the
     * native platform pick the best match: Windows uses the 16px in the title
     * bar and Alt+Tab, the 32px in the taskbar, and larger sizes where scaled.
     * Re-run on every frame rebuild (language switch) via the constructor.
     */
    private void installWindowIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[]{16, 32, 48, 256}) {
            URL url = MainFrame.class.getResource("/icons/icon-" + size + ".png");
            if (url == null) {
                AppLogger.warn("Window icon missing: /icons/icon-" + size + ".png - skipping", null);
                continue;
            }
            try {
                Image image = ImageIO.read(url);
                if (image != null) {
                    icons.add(image);
                }
            } catch (IOException ex) {
                AppLogger.error("Failed to decode window icon icon-" + size + ".png", ex);
            }
        }
        if (!icons.isEmpty()) {
            setIconImages(icons);
        }
    }

    /**
     * Top bar: a Settings button, reachable from every tab.
     *
     * The language selector moved into that dialog so preferences live in one
     * place instead of being scattered between a frame-level combo and two
     * per-tab hotkey buttons.
     */
    private JPanel buildTopBar(AppSettings appSettings, AutoClickerPanel clickerPanel,
            MacroSequencerPanel macroPanel, WindowManagerPanel windowPanel) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        bar.setOpaque(false);

        JButton settingsBtn = UiTheme.createNeutralButton(I18n.t("settings.title"));
        settingsBtn.addActionListener(e -> {
            SettingsDialog dlg = new SettingsDialog(this, appSettings);
            dlg.setVisible(true);
            Locale chosen = dlg.getChosenLocale();
            if (!chosen.getLanguage().equals(I18n.getLocale().getLanguage())) {
                // Rebuilding the window re-registers every hotkey anyway, so the
                // language branch subsumes the hotkey refresh below.
                switchLanguage(appSettings, chosen);
            } else if (dlg.isHotkeysChanged()) {
                // Re-bind so an edited hotkey takes effect immediately, and so
                // each button's "[F3]" label suffix matches the new binding.
                clickerPanel.registerHotkeys();
                macroPanel.registerHotkeys();
                windowPanel.registerHotkeys();
            }
        });

        bar.add(settingsBtn);
        return bar;
    }

    /**
     * Applies a language by rebuilding the window.
     *
     * Swing widgets copy their text at construction time, so there is no
     * "re-translate in place" short of walking the tree and re-setting every
     * label - which is precisely how a half-translated UI happens, since any
     * component the walk misses keeps the old language silently. Rebuilding is
     * a few lines and cannot be partially correct. State survives because it
     * lives in settings.json, which is saved first.
     */
    private void switchLanguage(AppSettings appSettings, Locale locale) {
        appSettings.setLanguageTag(locale.toLanguageTag());
        SettingsManager.save(appSettings);
        I18n.setLocale(locale);

        // The recorder/engines belong to the panels being discarded; the hook
        // itself is app-wide and deliberately left registered.
        SwingUtilities.invokeLater(() -> {
            MainFrame replacement = new MainFrame();
            replacement.setVisible(true);
            dispose();
        });
    }
}
