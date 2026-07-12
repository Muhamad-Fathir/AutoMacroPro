package com.automacropro.ui;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Font;
import java.awt.FlowLayout;

/**
 * The control row described identically for both modules in the spec:
 * Start / Stop / Toggle (pause-resume) / Save Settings / Reset Settings /
 * a button to configure this module's custom hotkeys / a Failsafe on-off
 * checkbox, plus a status line.
 *
 * Both {@code AutoClickerPanel} and {@code MacroSequencerPanel} embed their
 * own independent instance (independent hotkey namespace per module, as the
 * spec lists "Custom Hotkeys" separately for each module). The Failsafe
 * checkbox controls the same app-wide {@code FailsafeMonitor} flag from
 * either tab for convenience.
 */
public class AutomationControlBar extends JPanel {

    public interface Listener {
        void onStart();
        void onStop();
        void onToggle();
        void onSaveSettings();
        void onResetSettings();
        void onConfigureHotkeys();
        void onFailsafeToggle(boolean enabled);
    }

    private final JButton startBtn = UiTheme.createButton("Start", UiTheme.START_BG, UiTheme.START_FG);
    private final JButton stopBtn = UiTheme.createButton("Stop", UiTheme.STOP_BG, UiTheme.STOP_FG);
    private final JButton toggleBtn = UiTheme.createButton("Toggle", UiTheme.TOGGLE_BG, UiTheme.TOGGLE_FG);
    private final JButton saveBtn = new JButton("Save Settings");
    private final JButton resetBtn = new JButton("Reset Settings");
    private final JButton hotkeyBtn = new JButton("Configure Hotkeys...");
    private final JCheckBox failsafeCheck = new JCheckBox("Failsafe Aktif (kursor ke pojok layar = stop)", true);
    private final JLabel statusLabel = new JLabel("Status: Idle");

    public AutomationControlBar(Listener listener) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(4, 0, 4, 0));

        // Row 1: the 3 main controls, uniform size (set inside UiTheme.createButton),
        // laid out in a simple left-aligned FlowLayout so they stay neatly side by side.
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        row1.add(startBtn);
        row1.add(stopBtn);
        row1.add(toggleBtn);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row2.add(saveBtn);
        row2.add(resetBtn);
        row2.add(hotkeyBtn);

        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row3.add(failsafeCheck);

        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        statusLabel.setFont(UiTheme.FONT_BODY.deriveFont(Font.ITALIC));
        statusLabel.setForeground(UiTheme.MUTED_TEXT);
        row4.add(statusLabel);

        add(row1);
        add(row2);
        add(row3);
        add(row4);

        stopBtn.setEnabled(false);
        toggleBtn.setEnabled(false);
        toggleBtn.setToolTipText("Pause / Resume tanpa menghentikan run sepenuhnya");

        startBtn.addActionListener(e -> listener.onStart());
        stopBtn.addActionListener(e -> listener.onStop());
        toggleBtn.addActionListener(e -> listener.onToggle());
        saveBtn.addActionListener(e -> listener.onSaveSettings());
        resetBtn.addActionListener(e -> listener.onResetSettings());
        hotkeyBtn.addActionListener(e -> listener.onConfigureHotkeys());
        failsafeCheck.addActionListener(e -> listener.onFailsafeToggle(failsafeCheck.isSelected()));
    }

    /** Reflects engine state in the buttons. Start disabled while running; Stop/Toggle enabled only then. */
    public void setRunningState(boolean running) {
        startBtn.setEnabled(!running);
        stopBtn.setEnabled(running);
        toggleBtn.setEnabled(running);
    }

    public void setStatusText(String text) {
        statusLabel.setText("Status: " + text);
    }

    public void setFailsafeChecked(boolean enabled) {
        failsafeCheck.setSelected(enabled);
    }

    /**
     * Exposed so hotkey callbacks can trigger {@code doClick()} instead of
     * calling the listener directly - this automatically reuses each
     * button's enabled/disabled guard (e.g. a Start hotkey pressed while
     * already running correctly does nothing, with zero extra logic).
     */
    public JButton getStartButton() {
        return startBtn;
    }

    public JButton getStopButton() {
        return stopBtn;
    }

    public JButton getToggleButton() {
        return toggleBtn;
    }

    public JButton getSaveButton() {
        return saveBtn;
    }

    public JButton getResetButton() {
        return resetBtn;
    }
}
