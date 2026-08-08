package com.automacropro.ui;

import com.automacropro.model.AppSettings;
import com.automacropro.persistence.SettingsManager;
import com.automacropro.util.I18n;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Locale;

/**
 * Application preferences: language, every hotkey in one table, and a reference
 * card for the in-app keyboard shortcuts.
 *
 * The hotkey table is unified here because bindings previously lived in two
 * per-tab dialogs with no single place to see them - which made a conflict
 * between, say, the Autoclicker's Start and the recorder's toggle invisible
 * until it misfired. The per-tab dialogs still exist and still work; both edit
 * the same {@link AppSettings} bindings, so they cannot disagree.
 */
public class SettingsDialog extends JDialog {

    private final AppSettings settings;
    private final HotkeyTablePanel hotkeyTable;
    private boolean hotkeysChanged;
    private Locale chosenLocale;

    public SettingsDialog(Window owner, AppSettings settings) {
        super(owner, I18n.t("settings.title"), ModalityType.APPLICATION_MODAL);
        this.settings = settings;
        this.chosenLocale = I18n.getLocale();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(I18n.t("settings.tab.general"), buildGeneralTab());

        hotkeyTable = buildHotkeyTable(settings);
        tabs.addTab(I18n.t("settings.tab.hotkeys"), new JScrollPane(hotkeyTable.inColumn()));
        tabs.addTab(I18n.t("settings.tab.shortcuts"), new JScrollPane(buildShortcutsTab()));

        JButton close = UiTheme.createNeutralButton(I18n.t("common.close"));
        close.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        south.setOpaque(false);
        south.add(close);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setSize(600, 520);
        setLocationRelativeTo(owner);
    }

    /** Every binding in the app, grouped by the module that owns it. */
    private static HotkeyTablePanel buildHotkeyTable(AppSettings settings) {
        HotkeyTablePanel table = new HotkeyTablePanel(settings);
        table.addSection(I18n.t("tab.autoclicker"));
        table.addBinding(AppSettings.HK_AUTOCLICKER_START, I18n.t("control.start"));
        table.addBinding(AppSettings.HK_AUTOCLICKER_STOP, I18n.t("control.stop"));
        table.addBinding(AppSettings.HK_AUTOCLICKER_TOGGLE, I18n.t("hotkey.toggleLabel"));

        table.addSection(I18n.t("tab.macro"));
        table.addBinding(AppSettings.HK_MACRO_START, I18n.t("control.start"));
        table.addBinding(AppSettings.HK_MACRO_STOP, I18n.t("control.stop"));
        table.addBinding(AppSettings.HK_MACRO_TOGGLE, I18n.t("hotkey.toggleLabel"));
        table.addBinding(AppSettings.HK_MACRO_RECORD, I18n.t("hotkey.record"));

        table.addSection(I18n.t("tab.windowManager"));
        table.addBinding(AppSettings.HK_WM_REFRESH, I18n.t("wm.refresh"));
        table.addBinding(AppSettings.HK_WM_BORDERLESS, I18n.t("wm.borderless"));
        return table;
    }

    private JPanel buildGeneralTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        g.gridy = 0;

        panel.add(new JLabel(I18n.t("lang.label")), g);

        JComboBox<LanguageOption> combo = new JComboBox<>(new LanguageOption[]{
                new LanguageOption(I18n.ENGLISH, I18n.t("lang.english")),
                new LanguageOption(I18n.INDONESIAN, I18n.t("lang.indonesian")),
        });
        combo.setSelectedIndex(
                I18n.INDONESIAN.getLanguage().equals(I18n.getLocale().getLanguage()) ? 1 : 0);
        combo.addActionListener(e -> {
            LanguageOption picked = (LanguageOption) combo.getSelectedItem();
            if (picked != null) {
                chosenLocale = picked.locale;
            }
        });
        g.gridx = 1;
        panel.add(combo, g);

        g.gridx = 0;
        g.gridy = 1;
        g.gridwidth = 2;
        JLabel note = new JLabel(I18n.t("settings.langNote"));
        note.setFont(UiTheme.FONT_BODY.deriveFont(Font.ITALIC, 11f));
        note.setForeground(UiTheme.MUTED_TEXT);
        panel.add(note, g);
        return panel;
    }

    /**
     * Read-only reference for the in-app shortcuts. These are Swing key
     * bindings scoped to the focused window, deliberately distinct from the
     * global hotkeys on the previous tab - the tab split is what keeps that
     * distinction visible to the user.
     */
    private JPanel buildShortcutsTab() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JLabel intro = new JLabel(I18n.t("settings.shortcutsIntro"));
        intro.setFont(UiTheme.FONT_BODY.deriveFont(Font.ITALIC, 11f));
        intro.setForeground(UiTheme.MUTED_TEXT);
        intro.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(intro);
        panel.add(Box.createVerticalStrut(10));

        String[][] shortcuts = {
                {"Ctrl + C", I18n.t("shortcut.copy")},
                {"Ctrl + X", I18n.t("shortcut.cut")},
                {"Ctrl + V", I18n.t("shortcut.paste")},
                {"Ctrl + D", I18n.t("shortcut.duplicate")},
                {"Ctrl + A", I18n.t("shortcut.selectAll")},
                {"Ctrl + S", I18n.t("shortcut.save")},
                {"Ctrl + E", I18n.t("shortcut.export")},
                {"Ctrl + L", I18n.t("shortcut.load")},
                {"Delete / Backspace", I18n.t("shortcut.delete")},
                {"Esc", I18n.t("shortcut.deselect")},
                {"Shift + Click", I18n.t("shortcut.shiftClick")},
                {"Ctrl + Click", I18n.t("shortcut.ctrlClick")},
        };

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 4, 3, 12);
        g.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < shortcuts.length; i++) {
            g.gridx = 0;
            g.gridy = i;
            JLabel keys = new JLabel(shortcuts[i][0]);
            keys.setFont(UiTheme.FONT_MONO_SMALL);
            keys.setForeground(UiTheme.ACCENT);
            grid.add(keys, g);
            g.gridx = 1;
            grid.add(new JLabel(shortcuts[i][1]), g);
        }
        panel.add(grid);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    @Override
    public void dispose() {
        // Never leave a capture listener on the app-wide hook.
        if (hotkeyTable != null) {
            hotkeyTable.cancelCapture();
            hotkeysChanged = hotkeyTable.isChanged();
            if (hotkeysChanged) {
                SettingsManager.save(settings);
            }
        }
        super.dispose();
    }

    public boolean isHotkeysChanged() {
        return hotkeysChanged;
    }

    /** The locale picked in this dialog, or the current one if unchanged. */
    public Locale getChosenLocale() {
        return chosenLocale;
    }

    /** Combo entry pairing a locale with its display name. */
    static final class LanguageOption {
        final Locale locale;
        final String label;

        LanguageOption(Locale locale, String label) {
            this.locale = locale;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
