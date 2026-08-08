package com.automacropro.ui;

import com.automacropro.model.HotkeyBinding;
import com.automacropro.util.I18n;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
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

    private final JButton startBtn = UiTheme.createButton(I18n.t("control.start"), UiTheme.START_BG, UiTheme.START_FG);
    private final JButton stopBtn = UiTheme.createButton(I18n.t("control.stop"), UiTheme.STOP_BG, UiTheme.STOP_FG);
    private final JButton toggleBtn = UiTheme.createButton(I18n.t("control.toggle"), UiTheme.TOGGLE_BG, UiTheme.TOGGLE_FG);
    private final JButton saveBtn = UiTheme.createNeutralButton(I18n.t("control.save"));
    private final JButton resetBtn = UiTheme.createNeutralButton(I18n.t("control.reset"));
    private final JButton hotkeyBtn = UiTheme.createNeutralButton(I18n.t("control.hotkeys"));
    private final JCheckBox failsafeCheck = new JCheckBox(I18n.t("control.failsafe"), true);
    private final JLabel statusLabel = new JLabel(I18n.t("control.status", I18n.t("status.idle")));

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
        // Toggle stays ENABLED while idle: it is now the Start path too
        // (idle -> start, running -> pause, paused -> resume), so disabling it
        // when nothing is running would make the unified control unreachable
        // exactly when a user reaches for it.
        toggleBtn.setToolTipText(I18n.t("control.toggle.tip"));

        startBtn.addActionListener(e -> listener.onStart());
        stopBtn.addActionListener(e -> listener.onStop());
        toggleBtn.addActionListener(e -> listener.onToggle());
        saveBtn.addActionListener(e -> listener.onSaveSettings());
        resetBtn.addActionListener(e -> listener.onResetSettings());
        hotkeyBtn.addActionListener(e -> listener.onConfigureHotkeys());
        failsafeCheck.addActionListener(e -> listener.onFailsafeToggle(failsafeCheck.isSelected()));
    }

    /** Reflects engine state in the buttons. Start disabled while running; Stop enabled only then. */
    public void setRunningState(boolean running) {
        startBtn.setEnabled(!running);
        stopBtn.setEnabled(running);
        // toggleBtn is deliberately left enabled in both states - see constructor.
    }

    public void setStatusText(String text) {
        statusLabel.setText(I18n.t("control.status", text));
    }

    /**
     * Appends each button's bound hotkey to its label ("Start [F3]"), so the
     * binding is discoverable without opening the hotkey dialog. Called again
     * after the dialog closes, since a rebind must be reflected immediately.
     *
     * Unbound actions show no suffix rather than an empty bracket pair.
     */
    public void showHotkeyLabels(HotkeyBinding start, HotkeyBinding stop, HotkeyBinding toggle) {
        startBtn.setText(withHotkey(I18n.t("control.start"), start));
        stopBtn.setText(withHotkey(I18n.t("control.stop"), stop));
        toggleBtn.setText(withHotkey(I18n.t("control.toggle"), toggle));
    }

    /**
     * Same suffix treatment for any button outside this bar - the sequencer's
     * Record button, for instance. Kept here beside {@link #withHotkey} so the
     * bracket format cannot drift between the two call sites.
     */
    public static void showHotkeyLabel(javax.swing.JButton button, String baseLabel, HotkeyBinding binding) {
        button.setText(withHotkey(baseLabel, binding));
    }

    private static String withHotkey(String label, HotkeyBinding binding) {
        if (binding == null || binding.isUnbound()) {
            return label;
        }
        return label + "  [" + binding.describe() + "]";
    }

    public void setFailsafeChecked(boolean enabled) {
        failsafeCheck.setSelected(enabled);
    }

    /**
     * Brief red flash on the status line, paired with the beep from
     * {@link com.automacropro.engine.FailsafeMonitor}, so a failsafe stop is
     * unmistakably deliberate rather than looking like a crash.
     *
     * Uses a one-shot {@link Timer} (the Swing one, which fires on the EDT) and
     * NOT Thread.sleep - this is called on the EDT and must return immediately.
     */
    public void flashFailsafe() {
        statusLabel.setForeground(UiTheme.DANGER);
        statusLabel.setFont(UiTheme.FONT_BODY.deriveFont(Font.BOLD));
        Timer timer = new Timer(1200, e -> {
            statusLabel.setForeground(UiTheme.MUTED_TEXT);
            statusLabel.setFont(UiTheme.FONT_BODY.deriveFont(Font.ITALIC));
        });
        timer.setRepeats(false);
        timer.start();
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
