package com.automacropro.ui;

import com.automacropro.engine.AutoClickerEngine;
import com.automacropro.hotkey.GlobalHotkeyManager;
import com.automacropro.model.AppSettings;
import com.automacropro.model.AutoClickerSettings;
import com.automacropro.model.ClickLimitMode;
import com.automacropro.persistence.ProfileManager;
import com.automacropro.persistence.SettingsManager;
import com.automacropro.util.AppLogger;
import com.automacropro.util.I18n;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Module 1: Advanced Autoclicker. Interval down to 1ms, Infinite/Fixed click
 * limit, and the full mouse action editor (Single/Double/Drag/Hold Click)
 * via the shared {@link MouseActionConfigPanel}.
 */
public class AutoClickerPanel extends JPanel {

    private final AppSettings appSettings;
    private AutoClickerEngine engine; // null if Robot failed to initialize (see catch below)
    private final AutomationControlBar controlBar;
    private final MouseActionConfigPanel mousePanel = new MouseActionConfigPanel();

    private final JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 3_600_000, 1));
    private final JRadioButton limitInfinite = new JRadioButton(I18n.t("ac.limit.infinite"), true);
    private final JRadioButton limitFixed = new JRadioButton(I18n.t("ac.limit.fixed"));
    private final JSpinner fixedCountSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 1_000_000_000, 1));

    // Humanizer (anti-detection): both default to off, so opting in is explicit.
    private final JCheckBox humanizeCheck = new JCheckBox(I18n.t("ac.humanize.enable"));
    private final JSpinner intervalJitterSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 600_000, 5));
    private final JSpinner positionJitterSpinner = new JSpinner(new SpinnerNumberModel(3, 0, 500, 1));
    private final JLabel jitterPreview = new JLabel();

    /** Collapsible container for the jitter fields - see the Humanizer card. */
    private final JPanel humanizerFields = new JPanel();

    // Local profiles ("RPG Farming", "Idle Game", ...). Editable so the combo
    // doubles as the name field when saving a new preset.
    private final JComboBox<String> profileCombo = new JComboBox<>();

    public AutoClickerPanel(AppSettings appSettings) {
        this.appSettings = appSettings;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Group 1: how fast and how many - the two settings that decide the run's
        // shape - in their own glass card, separate from *what* the click does.
        GlassPanel timingCard = new GlassPanel(new GridBagLayout(), I18n.t("ac.group.timing"));
        GridBagConstraints g = freshConstraints();
        addRow(timingCard, g, I18n.t("ac.interval"), intervalSpinner);

        ButtonGroup limitGroup = new ButtonGroup();
        limitGroup.add(limitInfinite);
        limitGroup.add(limitFixed);
        JPanel limitRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        limitRow.add(limitInfinite);
        limitRow.add(limitFixed);
        limitRow.add(new JLabel(I18n.t("ac.limit.count")));
        limitRow.add(fixedCountSpinner);
        addRow(timingCard, g, I18n.t("ac.limit"), limitRow);
        limitInfinite.addActionListener(e -> updateLimitEnabled());
        limitFixed.addActionListener(e -> updateLimitEnabled());

        // Group 3: Humanizer. Sits with the timing card because interval spread
        // is the setting it modifies most visibly. The jitter fields live in
        // their own sub-panel so the whole block can be hidden in one call -
        // setEnabled alone would grey them out while still occupying the
        // vertical space that "Pick Location" needs on a 1080p screen.
        GlassPanel humanizerCard = new GlassPanel(new BorderLayout(0, 4), I18n.t("ac.group.humanizer"));
        humanizerCard.add(humanizeCheck, BorderLayout.NORTH);

        humanizerFields.setOpaque(false);
        humanizerFields.setLayout(new GridBagLayout());
        GridBagConstraints hg = freshConstraints();
        addRow(humanizerFields, hg, I18n.t("ac.humanize.interval"), intervalJitterSpinner);
        addRow(humanizerFields, hg, I18n.t("ac.humanize.radius"), positionJitterSpinner);
        humanizerCard.add(humanizerFields, BorderLayout.CENTER);

        jitterPreview.setFont(UiTheme.FONT_BODY.deriveFont(11f));
        jitterPreview.setForeground(UiTheme.MUTED_TEXT);
        // Preview stays visible when collapsed, so the card always says what
        // state it is in rather than becoming an unexplained empty box.
        humanizerCard.add(jitterPreview, BorderLayout.SOUTH);

        humanizeCheck.addActionListener(e -> updateHumanizerEnabled());
        // Keep the preview honest as the numbers change.
        javax.swing.event.ChangeListener previewSync = e -> updateJitterPreview();
        intervalJitterSpinner.addChangeListener(previewSync);
        positionJitterSpinner.addChangeListener(previewSync);
        intervalSpinner.addChangeListener(previewSync);

        JPanel settingsColumn = new JPanel(new BorderLayout(0, 8));
        settingsColumn.setOpaque(false);
        settingsColumn.add(buildProfileCard(), BorderLayout.NORTH);
        settingsColumn.add(timingCard, BorderLayout.CENTER);
        settingsColumn.add(humanizerCard, BorderLayout.SOUTH);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(settingsColumn, BorderLayout.NORTH);

        // IMPORTANT: mousePanel is a BoxLayout column whose natural height varies with
        // Look&Feel/DPI (Windows/Nimbus need visibly more room per row than Metal does).
        // Placing it bare in BorderLayout.CENTER let BoxLayout's shrink algorithm crush
        // whichever child had the most "give" - in practice the Posisi Awal row - down to
        // near-zero height with no visible sign anything was wrong. A JScrollPane makes
        // "not enough room" show up as a scrollbar instead of silently missing content.
        JScrollPane mouseScroll = new JScrollPane(mousePanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        mouseScroll.setBorder(BorderFactory.createEmptyBorder());
        // Both the scroll pane AND its viewport must be non-opaque, or the
        // viewport paints its own background over the glass card behind it.
        mouseScroll.setOpaque(false);
        mouseScroll.getViewport().setOpaque(false);
        mouseScroll.getVerticalScrollBar().setUnitIncrement(16);

        // Group 2: what the click actually does (button, interaction, position).
        GlassPanel actionCard = new GlassPanel(new BorderLayout(), I18n.t("ac.group.action"));
        actionCard.add(mouseScroll, BorderLayout.CENTER);
        top.add(actionCard, BorderLayout.CENTER);

        controlBar = new AutomationControlBar(new AutomationControlBar.Listener() {
            @Override public void onStart() { handleStart(); }
            @Override public void onStop() { handleStop(); }
            @Override public void onToggle() { handleToggle(); }
            @Override public void onSaveSettings() { handleSave(); }
            @Override public void onResetSettings() { handleReset(); }
            @Override public void onConfigureHotkeys() { handleConfigureHotkeys(); }
            @Override public void onFailsafeToggle(boolean enabled) { handleFailsafeToggle(enabled); }
        });

        add(top, BorderLayout.CENTER);
        add(controlBar, BorderLayout.SOUTH);

        applySettingsToUi(appSettings.getAutoClickerSettings());
        updateLimitEnabled();
        registerHotkeys();
        com.automacropro.engine.FailsafeMonitor.setEnabled(appSettings.isFailsafeEnabled());
        com.automacropro.engine.FailsafeMonitor.addAlertListener(controlBar::flashFailsafe);
        controlBar.setFailsafeChecked(appSettings.isFailsafeEnabled());

        // Must run after every child exists - it walks the tree.
        UiTheme.deopaque(this);
        NumericInput.hardenAll(this);

        try {
            engine = new AutoClickerEngine(new AutoClickerEngine.Listener() {
                @Override
                public void onStarted() {
                    controlBar.setRunningState(true);
                    controlBar.setStatusText(I18n.t("status.running"));
                }

                @Override
                public void onFinished(long totalClicks, AutoClickerEngine.StopReason reason) {
                    controlBar.setRunningState(false);
                    controlBar.setStatusText(describeFinish(totalClicks, reason));
                }
            });
        } catch (AWTException ex) {
            AppLogger.error("Gagal inisialisasi java.awt.Robot pada AutoClickerPanel", ex);
            controlBar.setStatusText(I18n.t("status.robotFailed"));
        }
    }

    private void handleFailsafeToggle(boolean enabled) {
        com.automacropro.engine.FailsafeMonitor.setEnabled(enabled);
        appSettings.setFailsafeEnabled(enabled);
        SettingsManager.save(appSettings);
        controlBar.setStatusText(I18n.t(enabled ? "status.failsafeOn" : "status.failsafeOff"));
    }

    /**
     * Profile row: an editable combo (so it is both the picker and the name
     * field for a new preset) plus Load / Save / Delete. BoxLayout, not
     * FlowLayout - see the wrap bug documented in MacroSequencerPanel.
     */
    private GlassPanel buildProfileCard() {
        GlassPanel card = new GlassPanel(new BorderLayout(), I18n.t("ac.group.profile"));
        card.setLayout(new BoxLayout(card, BoxLayout.X_AXIS));

        profileCombo.setEditable(true);
        profileCombo.setMaximumSize(new Dimension(220, 28));
        profileCombo.setPreferredSize(new Dimension(220, 28));

        JButton loadBtn = UiTheme.createNeutralButton(I18n.t("profile.load"));
        JButton saveBtn = UiTheme.createNeutralButton(I18n.t("profile.save"));
        JButton deleteBtn = UiTheme.createNeutralButton(I18n.t("profile.delete"));
        loadBtn.addActionListener(e -> handleProfileLoad());
        saveBtn.addActionListener(e -> handleProfileSave());
        deleteBtn.addActionListener(e -> handleProfileDelete());

        card.add(new JLabel(I18n.t("profile.label")));
        card.add(Box.createHorizontalStrut(6));
        card.add(profileCombo);
        card.add(Box.createHorizontalStrut(10));
        card.add(loadBtn);
        card.add(Box.createHorizontalStrut(6));
        card.add(saveBtn);
        card.add(Box.createHorizontalStrut(6));
        card.add(deleteBtn);
        card.add(Box.createHorizontalGlue());

        refreshProfileList(null);
        return card;
    }

    /** Rebuilds the dropdown from disk, optionally re-selecting a name. */
    private void refreshProfileList(String selectName) {
        profileCombo.removeAllItems();
        for (String name : ProfileManager.list()) {
            profileCombo.addItem(name);
        }
        if (selectName != null) {
            profileCombo.setSelectedItem(selectName);
        } else if (profileCombo.getItemCount() > 0) {
            profileCombo.setSelectedIndex(0);
        } else {
            profileCombo.setSelectedItem("");
        }
    }

    /** Whatever is typed or picked in the editable combo. */
    private String currentProfileName() {
        Object value = profileCombo.getEditor().getItem();
        return value == null ? "" : value.toString().trim();
    }

    private void handleProfileLoad() {
        String name = currentProfileName();
        AutoClickerSettings loaded = ProfileManager.load(name);
        if (loaded == null) {
            controlBar.setStatusText(I18n.t("profile.notFound", name));
            return;
        }
        applySettingsToUi(loaded);
        controlBar.setStatusText(I18n.t("profile.loaded", name));
    }

    private void handleProfileSave() {
        String name = currentProfileName();
        if (!ProfileManager.isValidName(name)) {
            controlBar.setStatusText(I18n.t("profile.invalidName"));
            return;
        }
        boolean overwriting = ProfileManager.list().stream().anyMatch(n -> n.equalsIgnoreCase(name));
        if (overwriting) {
            int answer = JOptionPane.showConfirmDialog(this,
                    I18n.t("profile.overwrite.body", name),
                    I18n.t("profile.overwrite.title"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        boolean ok = ProfileManager.save(name, buildSettingsFromUi());
        controlBar.setStatusText(ok ? I18n.t("profile.saved", name) : I18n.t("profile.saveFailed"));
        if (ok) {
            refreshProfileList(name);
        }
    }

    private void handleProfileDelete() {
        String name = currentProfileName();
        if (name.isBlank()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                I18n.t("profile.confirmDelete.body", name),
                I18n.t("profile.confirmDelete.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        boolean ok = ProfileManager.delete(name);
        controlBar.setStatusText(ok ? I18n.t("profile.deleted", name) : I18n.t("profile.notFound", name));
        refreshProfileList(null);
    }

    private void updateLimitEnabled() {
        fixedCountSpinner.setEnabled(limitFixed.isSelected());
    }

    /**
     * Collapses the Humanizer card when the feature is off, reclaiming the
     * vertical space rather than just greying the fields out. The revalidate on
     * the top-level panel is what makes the surrounding layout actually take
     * the freed height back.
     */
    private void updateHumanizerEnabled() {
        boolean on = humanizeCheck.isSelected();
        humanizerFields.setVisible(on);
        intervalJitterSpinner.setEnabled(on);
        positionJitterSpinner.setEnabled(on);
        updateJitterPreview();
        revalidate();
        repaint();
    }

    /**
     * Shows the actual resulting range, because "+/- 20ms" on its own hides the
     * one case that matters: a spread wider than the interval would imply a
     * sub-1ms delay, and the engine floors it at 1ms. Better to show that here
     * than have the user wonder why a huge spread behaves tamely.
     */
    private void updateJitterPreview() {
        if (!humanizeCheck.isSelected()) {
            jitterPreview.setText(I18n.t("ac.humanize.off"));
            return;
        }
        long interval = ((Number) intervalSpinner.getValue()).longValue();
        long jitter = ((Number) intervalJitterSpinner.getValue()).longValue();
        int radius = ((Number) positionJitterSpinner.getValue()).intValue();
        long lo = Math.max(1, interval - jitter);
        long hi = interval + jitter;
        String clampNote = (interval - jitter) < 1 ? I18n.t("ac.humanize.clampNote") : "";
        jitterPreview.setText(I18n.t("ac.humanize.preview", lo, hi, clampNote)
                + (radius > 0 ? I18n.t("ac.humanize.scatter", radius) : I18n.t("ac.humanize.noScatter")));
    }

    private void handleStart() {
        if (engine == null) {
            JOptionPane.showMessageDialog(this, I18n.t("ac.cannotStart.body"),
                    I18n.t("ac.cannotStart.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        AutoClickerSettings s = buildSettingsFromUi();
        appSettings.setAutoClickerSettings(s);
        engine.start(s);
    }

    private void handleStop() {
        if (engine != null) {
            engine.stop();
        }
    }

    /**
     * Unified control: idle starts a run, running pauses it, paused resumes.
     *
     * Note isRunning() stays true briefly after stop() returns (the flag is
     * cleared by the worker thread's finally block), so a Toggle pressed in
     * that window pauses a dying run rather than starting a new one - harmless,
     * and preferable to racing a second worker into existence.
     */
    private void handleToggle() {
        if (engine == null) {
            return;
        }
        if (!engine.isRunning()) {
            handleStart();
            return;
        }
        engine.togglePause();
        controlBar.setStatusText(I18n.t(engine.isPaused() ? "status.paused" : "status.running"));
    }

    private void handleSave() {
        appSettings.setAutoClickerSettings(buildSettingsFromUi());
        boolean ok = SettingsManager.save(appSettings);
        controlBar.setStatusText(I18n.t(ok ? "status.saved" : "status.saveFailed"));
    }

    private void handleReset() {
        applySettingsToUi(AutoClickerSettings.defaults());
        controlBar.setStatusText(I18n.t("status.resetDone"));
    }

    private void handleConfigureHotkeys() {
        String[] ids = {AppSettings.HK_AUTOCLICKER_START, AppSettings.HK_AUTOCLICKER_STOP, AppSettings.HK_AUTOCLICKER_TOGGLE};
        String[] labels = {I18n.t("control.start"), I18n.t("control.stop"), I18n.t("hotkey.toggleLabel")};
        HotkeyCaptureDialog dlg = new HotkeyCaptureDialog(
                SwingUtilities.getWindowAncestor(this), I18n.t("hotkey.dialogTitle", I18n.t("tab.autoclicker")),
                appSettings, ids, labels);
        dlg.setVisible(true);
        if (dlg.isChanged()) {
            registerHotkeys();
            SettingsManager.save(appSettings);
        }
    }

    /** (Re)binds this module's hotkeys to doClick() on the control bar buttons - see AutomationControlBar javadoc. */
    public void registerHotkeys() {
        GlobalHotkeyManager hk = GlobalHotkeyManager.getInstance();
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_AUTOCLICKER_START), () -> controlBar.getStartButton().doClick());
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_AUTOCLICKER_STOP), () -> controlBar.getStopButton().doClick());
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_AUTOCLICKER_TOGGLE), () -> controlBar.getToggleButton().doClick());
        // Show each binding on its button ("Start [F3]"), refreshed here so a
        // rebind is reflected the moment the hotkey dialog closes.
        controlBar.showHotkeyLabels(
                appSettings.getHotkey(AppSettings.HK_AUTOCLICKER_START),
                appSettings.getHotkey(AppSettings.HK_AUTOCLICKER_STOP),
                appSettings.getHotkey(AppSettings.HK_AUTOCLICKER_TOGGLE));
    }

    private AutoClickerSettings buildSettingsFromUi() {
        // Force every spinner to commit and clamp first. Without this, a value
        // typed but never confirmed (no Enter, no focus change) is still sitting
        // in the editor and the model returns the PREVIOUS value - so the run
        // would silently use a different interval than the one on screen.
        NumericInput.clampAll(this);

        AutoClickerSettings s = new AutoClickerSettings();
        s.setIntervalMs(((Number) intervalSpinner.getValue()).longValue());
        s.setLimitMode(limitFixed.isSelected() ? ClickLimitMode.FIXED : ClickLimitMode.INFINITE);
        s.setFixedClickCount(((Number) fixedCountSpinner.getValue()).longValue());
        s.setIntervalJitterMs(humanizeCheck.isSelected()
                ? ((Number) intervalJitterSpinner.getValue()).longValue() : 0);
        var mouseConfig = mousePanel.getConfig();
        mouseConfig.setPositionJitterPx(humanizeCheck.isSelected()
                ? ((Number) positionJitterSpinner.getValue()).intValue() : 0);
        s.setMouseConfig(mouseConfig);
        return s;
    }

    private void applySettingsToUi(AutoClickerSettings s) {
        intervalSpinner.setValue((int) Math.min(Integer.MAX_VALUE, s.getIntervalMs()));
        int posJitter = s.getMouseConfig() == null ? 0 : s.getMouseConfig().getPositionJitterPx();
        boolean humanized = s.getIntervalJitterMs() > 0 || posJitter > 0;
        humanizeCheck.setSelected(humanized);
        if (s.getIntervalJitterMs() > 0) {
            intervalJitterSpinner.setValue((int) Math.min(Integer.MAX_VALUE, s.getIntervalJitterMs()));
        }
        if (posJitter > 0) {
            positionJitterSpinner.setValue(posJitter);
        }
        updateHumanizerEnabled();
        if (s.getLimitMode() == ClickLimitMode.FIXED) {
            limitFixed.setSelected(true);
        } else {
            limitInfinite.setSelected(true);
        }
        fixedCountSpinner.setValue((int) Math.min(Integer.MAX_VALUE, s.getFixedClickCount()));
        mousePanel.setConfig(s.getMouseConfig());
        updateLimitEnabled();
    }

    private String describeFinish(long totalClicks, AutoClickerEngine.StopReason reason) {
        String why;
        switch (reason) {
            case LIMIT_REACHED: why = I18n.t("ac.stop.limit"); break;
            case FAILSAFE: why = I18n.t("ac.stop.failsafe"); break;
            case ERROR: why = I18n.t("ac.stop.error"); break;
            default: why = I18n.t("ac.stop.manual");
        }
        return I18n.t("ac.finished", totalClicks, why);
    }

    private static GridBagConstraints freshConstraints() {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(4, 4, 4, 4);
        g.fill = GridBagConstraints.HORIZONTAL;
        return g;
    }

    private static void addRow(JPanel panel, GridBagConstraints g, String label, JComponent field) {
        g.gridx = 0;
        g.gridwidth = 1;
        g.weightx = 0;
        panel.add(new JLabel(label), g);
        g.gridx = 1;
        g.weightx = 1;
        panel.add(field, g);
        g.gridy++;
    }
}
