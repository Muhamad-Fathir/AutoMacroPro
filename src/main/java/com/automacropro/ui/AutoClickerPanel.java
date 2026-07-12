package com.automacropro.ui;

import com.automacropro.engine.AutoClickerEngine;
import com.automacropro.hotkey.GlobalHotkeyManager;
import com.automacropro.model.AppSettings;
import com.automacropro.model.AutoClickerSettings;
import com.automacropro.model.ClickLimitMode;
import com.automacropro.persistence.SettingsManager;
import com.automacropro.util.AppLogger;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
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
    private final JRadioButton limitInfinite = new JRadioButton("Infinite", true);
    private final JRadioButton limitFixed = new JRadioButton("Fixed");
    private final JSpinner fixedCountSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 1_000_000_000, 1));

    public AutoClickerPanel(AppSettings appSettings) {
        this.appSettings = appSettings;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel settingsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints g = freshConstraints();
        addRow(settingsPanel, g, "Interval (ms, min 1):", intervalSpinner);

        ButtonGroup limitGroup = new ButtonGroup();
        limitGroup.add(limitInfinite);
        limitGroup.add(limitFixed);
        JPanel limitRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        limitRow.add(limitInfinite);
        limitRow.add(limitFixed);
        limitRow.add(new JLabel("Jumlah:"));
        limitRow.add(fixedCountSpinner);
        addRow(settingsPanel, g, "Click Limit:", limitRow);
        limitInfinite.addActionListener(e -> updateLimitEnabled());
        limitFixed.addActionListener(e -> updateLimitEnabled());

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(settingsPanel, BorderLayout.NORTH);

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
        mouseScroll.getViewport().setBackground(mousePanel.getBackground());
        mouseScroll.getVerticalScrollBar().setUnitIncrement(16);
        top.add(mouseScroll, BorderLayout.CENTER);

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
        controlBar.setFailsafeChecked(appSettings.isFailsafeEnabled());

        try {
            engine = new AutoClickerEngine(new AutoClickerEngine.Listener() {
                @Override
                public void onStarted() {
                    controlBar.setRunningState(true);
                    controlBar.setStatusText("Running...");
                }

                @Override
                public void onFinished(long totalClicks, AutoClickerEngine.StopReason reason) {
                    controlBar.setRunningState(false);
                    controlBar.setStatusText(describeFinish(totalClicks, reason));
                }
            });
        } catch (AWTException ex) {
            AppLogger.error("Gagal inisialisasi java.awt.Robot pada AutoClickerPanel", ex);
            controlBar.setStatusText("Gagal inisialisasi Robot - lihat log. Coba jalankan sebagai Administrator.");
        }
    }

    private void handleFailsafeToggle(boolean enabled) {
        com.automacropro.engine.FailsafeMonitor.setEnabled(enabled);
        appSettings.setFailsafeEnabled(enabled);
        SettingsManager.save(appSettings);
        controlBar.setStatusText(enabled ? "Failsafe diaktifkan." : "Failsafe dimatikan - tidak ada killswitch otomatis.");
    }

    private void updateLimitEnabled() {
        fixedCountSpinner.setEnabled(limitFixed.isSelected());
    }

    private void handleStart() {
        if (engine == null) {
            JOptionPane.showMessageDialog(this, "Robot gagal diinisialisasi sebelumnya. Restart aplikasi.",
                    "Tidak bisa Start", JOptionPane.ERROR_MESSAGE);
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

    private void handleToggle() {
        if (engine != null) {
            engine.togglePause();
            controlBar.setStatusText(engine.isPaused() ? "Paused" : "Running...");
        }
    }

    private void handleSave() {
        appSettings.setAutoClickerSettings(buildSettingsFromUi());
        boolean ok = SettingsManager.save(appSettings);
        controlBar.setStatusText(ok ? "Settings disimpan." : "Gagal menyimpan settings (lihat log).");
    }

    private void handleReset() {
        applySettingsToUi(AutoClickerSettings.defaults());
        controlBar.setStatusText("Dikembalikan ke default (klik 'Save Settings' untuk menyimpan).");
    }

    private void handleConfigureHotkeys() {
        String[] ids = {AppSettings.HK_AUTOCLICKER_START, AppSettings.HK_AUTOCLICKER_STOP, AppSettings.HK_AUTOCLICKER_TOGGLE};
        String[] labels = {"Start", "Stop", "Toggle (Pause/Resume)"};
        HotkeyCaptureDialog dlg = new HotkeyCaptureDialog(
                SwingUtilities.getWindowAncestor(this), "Custom Hotkeys - Autoclicker", appSettings, ids, labels);
        dlg.setVisible(true);
        if (dlg.isChanged()) {
            registerHotkeys();
            SettingsManager.save(appSettings);
        }
    }

    /** (Re)binds this module's hotkeys to doClick() on the control bar buttons - see AutomationControlBar javadoc. */
    private void registerHotkeys() {
        GlobalHotkeyManager hk = GlobalHotkeyManager.getInstance();
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_AUTOCLICKER_START), () -> controlBar.getStartButton().doClick());
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_AUTOCLICKER_STOP), () -> controlBar.getStopButton().doClick());
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_AUTOCLICKER_TOGGLE), () -> controlBar.getToggleButton().doClick());
    }

    private AutoClickerSettings buildSettingsFromUi() {
        AutoClickerSettings s = new AutoClickerSettings();
        s.setIntervalMs(((Integer) intervalSpinner.getValue()).longValue());
        s.setLimitMode(limitFixed.isSelected() ? ClickLimitMode.FIXED : ClickLimitMode.INFINITE);
        s.setFixedClickCount(((Integer) fixedCountSpinner.getValue()).longValue());
        s.setMouseConfig(mousePanel.getConfig());
        return s;
    }

    private void applySettingsToUi(AutoClickerSettings s) {
        intervalSpinner.setValue((int) Math.min(Integer.MAX_VALUE, s.getIntervalMs()));
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
            case LIMIT_REACHED: why = "limit klik tercapai"; break;
            case FAILSAFE: why = "FAILSAFE aktif (kursor menyentuh pojok layar)"; break;
            case ERROR: why = "berhenti karena error - lihat log"; break;
            default: why = "dihentikan manual";
        }
        return "Selesai - " + totalClicks + " klik (" + why + ")";
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
