package com.automacropro.ui;

import com.automacropro.hotkey.GlobalHotkeyManager;
import com.automacropro.hotkey.PositionPicker;
import com.automacropro.model.PositionMode;
import com.automacropro.util.AppLogger;
import com.automacropro.util.I18n;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * Reusable "where does this mouse action happen" control logic.
 *
 * IMPORTANT STRUCTURAL NOTE (2026-07-12): this used to extend JPanel itself
 * (one extra container level: section -> CoordinatePickerField -> row ->
 * control). On at least one real Windows machine (mixed-DPI multi-monitor:
 * 100% + 125%), that extra nesting level combined with WindowsLookAndFeel's
 * native theme rendering resulted in currentCursorRadio/fixedRadio/pickButton/
 * xSpinner painting literally 0% non-background pixels - confirmed with a
 * full region pixel scan, not a guess - while the structurally-shallower
 * "Mouse Button" row (one level less nesting) rendered fine on the same
 * machine. Rather than keep chasing the exact native rendering mechanism,
 * this class no longer inserts itself as a container in the component tree:
 * it builds {@link #getModeRow()} and {@link #getCoordRow()} as plain
 * JPanels that the caller adds DIRECTLY to its own layout (see
 * MouseActionConfigPanel.addSectionRow), matching the exact nesting depth of
 * the row that is known to render correctly everywhere. The radio buttons,
 * spinners, and button are also switched to explicit Basic*UI delegates /
 * self-painted rendering (the same defensive technique already used by
 * {@link UiTheme#createButton}) as a second, independent layer of protection
 * against native-theme-specific rendering failures.
 */
public class CoordinatePickerField {

    private final boolean allowCurrentCursor;
    private final JRadioButton currentCursorRadio = new JRadioButton(I18n.t("coord.currentCursor"));
    private final JRadioButton fixedRadio = new JRadioButton(I18n.t("coord.fixed"), true);
    private final JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(0, -20000, 20000, 1));
    private final JSpinner ySpinner = new JSpinner(new SpinnerNumberModel(0, -20000, 20000, 1));
    private final JButton pickButton = UiTheme.createNeutralButton(I18n.t("coord.pick"));
    private final Color defaultSpinnerBg;
    private final JPanel modeRow; // null when !allowCurrentCursor
    private final JPanel coordRow;

    public CoordinatePickerField(boolean allowCurrentCursor) {
        AppLogger.info("CoordinatePickerField(allowCurrentCursor=" + allowCurrentCursor
                + ") constructor - build-2026-07-12a-flattened-fix");
        this.allowCurrentCursor = allowCurrentCursor;

        // Defense-in-depth: force pure-Java rendering (no native OS theme calls at
        // all) for the controls that were confirmed not painting under real
        // WindowsLookAndFeel. This does not change behavior, only how the chrome
        // gets drawn.
        currentCursorRadio.setUI(new BasicRadioButtonUI());
        fixedRadio.setUI(new BasicRadioButtonUI());
        xSpinner.setUI(new BasicSpinnerUI());
        ySpinner.setUI(new BasicSpinnerUI());

        Dimension spinnerSize = new Dimension(72, xSpinner.getPreferredSize().height);
        xSpinner.setPreferredSize(spinnerSize);
        ySpinner.setPreferredSize(spinnerSize);
        defaultSpinnerBg = xSpinner.getBackground();

        if (allowCurrentCursor) {
            ButtonGroup group = new ButtonGroup();
            group.add(currentCursorRadio);
            group.add(fixedRadio);
            modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            modeRow.add(currentCursorRadio);
            modeRow.add(fixedRadio);
        } else {
            modeRow = null;
        }

        coordRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        coordRow.add(new JLabel("X:"));
        coordRow.add(xSpinner);
        coordRow.add(new JLabel("Y:"));
        coordRow.add(ySpinner);
        coordRow.add(pickButton);

        currentCursorRadio.addActionListener(e -> updateEnabledState());
        fixedRadio.addActionListener(e -> updateEnabledState());
        updateEnabledState();

        pickButton.addActionListener(e -> onPickClicked());
    }

    /** Row A: Current Cursor / Fixed Coordinate radios. Null when !allowCurrentCursor. */
    public JPanel getModeRow() {
        return modeRow;
    }

    /** Row B: X/Y spinners + Pick Location button. Always present. */
    public JPanel getCoordRow() {
        return coordRow;
    }

    private void updateEnabledState() {
        boolean fixed = !allowCurrentCursor || fixedRadio.isSelected();
        xSpinner.setEnabled(fixed);
        ySpinner.setEnabled(fixed);
    }

    private void onPickClicked() {
        if (!GlobalHotkeyManager.getInstance().isHookActive()) {
            JOptionPane.showMessageDialog(coordRow,
                    "Global hook tidak aktif (lihat log), jadi Pick Location tidak bisa menangkap klik di luar aplikasi.\n"
                            + "Anda masih bisa mengisi X/Y secara manual.",
                    "Pick Location tidak tersedia", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final String original = pickButton.getText();
        pickButton.setText(I18n.t("coord.picking"));
        pickButton.setEnabled(false);
        PositionPicker.captureNextClick(point -> {
            xSpinner.setValue(point.x);
            ySpinner.setValue(point.y);
            if (allowCurrentCursor) {
                fixedRadio.setSelected(true);
                updateEnabledState();
            }
            pickButton.setText(original);
            pickButton.setEnabled(true);
            flashCaptureConfirmation();
        });
    }

    /** Brief green flash on the X/Y spinners so a successful capture is obvious, not silent. */
    private void flashCaptureConfirmation() {
        Color flash = new Color(0xC8, 0xE6, 0xC9);
        xSpinner.setBackground(flash);
        ySpinner.setBackground(flash);
        Timer t = new Timer(500, e -> {
            xSpinner.setBackground(defaultSpinnerBg);
            ySpinner.setBackground(defaultSpinnerBg);
        });
        t.setRepeats(false);
        t.start();
    }

    public PositionMode getPositionMode() {
        return (allowCurrentCursor && currentCursorRadio.isSelected())
                ? PositionMode.CURRENT_CURSOR : PositionMode.FIXED_COORDINATE;
    }

    public void setPositionMode(PositionMode mode) {
        if (allowCurrentCursor) {
            boolean current = mode == PositionMode.CURRENT_CURSOR;
            currentCursorRadio.setSelected(current);
            fixedRadio.setSelected(!current);
        }
        updateEnabledState();
    }

    public int getX() {
        return (Integer) xSpinner.getValue();
    }

    public int getY() {
        return (Integer) ySpinner.getValue();
    }

    public void setX(int x) {
        xSpinner.setValue(x);
    }

    public void setY(int y) {
        ySpinner.setValue(y);
    }
}
