package com.automacropro.ui;

import com.automacropro.model.ClickMode;
import com.automacropro.model.DragStyle;
import com.automacropro.model.MouseActionConfig;
import com.automacropro.model.MouseButtonType;
import com.automacropro.util.I18n;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * Reusable editor for a complete {@link MouseActionConfig}: which button,
 * the interaction dropdown (Single / Double / Drag / Hold Click), the start
 * position, and - shown only for the relevant mode via a nested
 * {@link CardLayout} - either the Drag destination+style fields or the
 * Hold Duration (ms) field.
 *
 * Layout: a single top-to-bottom {@code BoxLayout} (Y_AXIS) with one clearly
 * labeled row per concern - Mouse Button, then Interaksi Mouse, then Posisi
 * Awal, then the conditional extra-details box. Each row is its own small
 * {@code JPanel}, separated by a fixed {@code Box.createVerticalStrut} gap.
 * This intentionally avoids a two-column "label left / field right"
 * {@code GridBagLayout}, which vertically centers a short label against a
 * taller field and can visually read as the label floating awkwardly mid-row
 * - straightforward vertical stacking has no such ambiguity.
 */
public class MouseActionConfigPanel extends JPanel {

    private static final String EXTRA_DRAG = "DRAG";
    private static final String EXTRA_HOLD = "HOLD";

    private final JRadioButton btnLeft = new JRadioButton(I18n.t("mouse.left"), true);
    private final JRadioButton btnRight = new JRadioButton(I18n.t("mouse.right"));
    private final JRadioButton btnMiddle = new JRadioButton(I18n.t("mouse.middle"));
    private final JComboBox<ClickMode> clickModeCombo = new JComboBox<>(ClickMode.values());
    private final CoordinatePickerField startPicker = new CoordinatePickerField(true);

    private final CardLayout extraCards = new CardLayout();
    private final JPanel extraPanel = new JPanel(extraCards);

    private final JRadioButton dragInstant = new JRadioButton(I18n.t("mouse.dragInstant"), false);
    private final JRadioButton dragSmooth = new JRadioButton(I18n.t("mouse.dragSmooth"), true);
    private final JSpinner dragStepsSpinner = new JSpinner(new SpinnerNumberModel(30, 2, 500, 1));
    private final JSpinner dragDurationSpinner = new JSpinner(new SpinnerNumberModel(300, 10, 10000, 10));
    private final CoordinatePickerField dragToPicker = new CoordinatePickerField(false);

    /** Hold Click: how long (ms) the button stays down before release. */
    private final JSpinner holdDurationSpinner = new JSpinner(new SpinnerNumberModel(500, 0, 600000, 50));

    public MouseActionConfigPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // --- Baris 1: Mouse Button ---
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(btnLeft);
        buttonGroup.add(btnRight);
        buttonGroup.add(btnMiddle);
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttonRow.add(btnLeft);
        buttonRow.add(btnRight);
        buttonRow.add(btnMiddle);
        addSectionRow(I18n.t("mouse.button"), buttonRow);

        // --- Baris 2: Interaksi Mouse (dropdown incl. Hold Click) ---
        clickModeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ClickMode) {
                    setText(displayClickMode((ClickMode) value));
                }
                return this;
            }
        });
        clickModeCombo.addActionListener(e -> updateExtraCard());
        JPanel comboRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        comboRow.add(clickModeCombo);
        addSectionRow(I18n.t("mouse.interaction"), comboRow);

        // --- Baris 3: Posisi Awal (Current Cursor / Fixed + X,Y + Pick Location) ---
        // startPicker no longer IS a container (see CoordinatePickerField javadoc) -
        // its two rows are added directly, at the same nesting depth as every other
        // section here (this is what was actually different before: this used to be
        // one extra JPanel level deeper than Mouse Button/Interaksi Mouse).
        addSectionRow(I18n.t("mouse.startPosition"), startPicker.getModeRow(), startPicker.getCoordRow());

        // --- Detail tambahan (Drag atau Hold), tampil sesuai pilihan dropdown di atas ---
        buildExtraCards();
        extraPanel.setBorder(UiTheme.titled(I18n.t("mouse.extraDetails")));
        extraPanel.setOpaque(false);
        extraPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(extraPanel);

        updateExtraCard();
        NumericInput.hardenAll(this);
    }

    /** One labeled section: a bold label on its own line, then the content directly below it. */
    private void addSectionRow(String label, javax.swing.JComponent content) {
        addSectionRow(label, new javax.swing.JComponent[]{content});
    }

    /**
     * Same as {@link #addSectionRow(String, javax.swing.JComponent)} but for a
     * label followed by several rows added directly (not wrapped in another
     * container) - null entries are skipped, so a nullable row (like
     * CoordinatePickerField.getModeRow() when allowCurrentCursor is false) is safe
     * to pass straight through.
     */
    private void addSectionRow(String label, javax.swing.JComponent... rows) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setOpaque(false);
        section.setBorder(new EmptyBorder(4, 0, 4, 0));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(UiTheme.FONT_BODY.deriveFont(Font.BOLD));
        labelComp.setForeground(UiTheme.ACCENT);
        labelComp.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(labelComp);

        for (javax.swing.JComponent row : rows) {
            if (row == null) {
                continue;
            }
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(row);
        }

        // Fixed minimum AND maximum height (both pinned to the preferred height computed
        // right now, with every child already added) - this gives BoxLayout zero "shrink
        // range" to take from this section. Without setMinimumSize, a taller section (like
        // Posisi Awal's two stacked rows) has more shrink range than its 1-row siblings and
        // absorbs a disproportionate share of any vertical deficit - down to near-zero in
        // practice - whenever available space is smaller than total preferred height (which
        // varies by Look&Feel/DPI, so this could pass on one machine and fail on another).
        int sectionHeight = section.getPreferredSize().height;
        section.setMinimumSize(new java.awt.Dimension(0, sectionHeight));
        section.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, sectionHeight));
        add(section);
    }

    private void buildExtraCards() {
        ButtonGroup dragStyleGroup = new ButtonGroup();
        dragStyleGroup.add(dragInstant);
        dragStyleGroup.add(dragSmooth);

        JPanel dragPanel = new JPanel();
        dragPanel.setLayout(new BoxLayout(dragPanel, BoxLayout.Y_AXIS));
        JPanel styleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        styleRow.add(new JLabel(I18n.t("mouse.dragStyle")));
        styleRow.add(dragInstant);
        styleRow.add(dragSmooth);
        styleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        dragPanel.add(styleRow);

        JPanel stepsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        stepsRow.add(new JLabel(I18n.t("mouse.dragSteps")));
        stepsRow.add(dragStepsSpinner);
        stepsRow.add(new JLabel("   " + I18n.t("mouse.dragDuration")));
        stepsRow.add(dragDurationSpinner);
        stepsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        dragPanel.add(stepsRow);

        JLabel dragToLabel = new JLabel(I18n.t("mouse.dragTarget"));
        dragToLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dragPanel.add(dragToLabel);
        dragToPicker.getCoordRow().setAlignmentX(Component.LEFT_ALIGNMENT);
        dragPanel.add(dragToPicker.getCoordRow());

        JPanel holdPanel = new JPanel();
        holdPanel.setLayout(new BoxLayout(holdPanel, BoxLayout.Y_AXIS));
        JPanel holdRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        holdRow.add(new JLabel(I18n.t("mouse.holdDuration")));
        holdRow.add(holdDurationSpinner);
        holdRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        holdPanel.add(holdRow);
        JLabel hint = new JLabel(I18n.t("mouse.holdHint"));
        hint.setForeground(UiTheme.MUTED_TEXT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        holdPanel.add(hint);

        dragPanel.setOpaque(false);
        holdPanel.setOpaque(false);
        extraPanel.add(dragPanel, EXTRA_DRAG);
        extraPanel.add(holdPanel, EXTRA_HOLD);
    }

    /**
     * Shows the Drag/Hold details, or hides the box entirely.
     *
     * The empty "EXTRA_NONE" card this used to show is gone, and that is the
     * actual fix for the big empty "Detail Tambahan" rectangle: an empty JPanel
     * inside a TitledBorder still occupies its full preferred height, so
     * Single/Double Click always reserved a titled box with nothing in it. A
     * container with nothing to say should not be on screen at all, so it is now
     * hidden outright - no toggle button needed, and the space is genuinely
     * reclaimed instead of merely collapsed.
     */
    private void updateExtraCard() {
        ClickMode mode = (ClickMode) clickModeCombo.getSelectedItem();
        boolean hasExtra = mode == ClickMode.DRAG || mode == ClickMode.HOLD;
        extraPanel.setVisible(hasExtra);
        if (hasExtra) {
            extraCards.show(extraPanel, mode == ClickMode.DRAG ? EXTRA_DRAG : EXTRA_HOLD);
        }
        revalidate();
        repaint();
    }

    private static String displayClickMode(ClickMode m) {
        switch (m) {
            case SINGLE: return I18n.t("clickmode.single");
            case DOUBLE: return I18n.t("clickmode.double");
            case DRAG: return I18n.t("clickmode.drag");
            case HOLD: return I18n.t("clickmode.hold");
            default: return m.name();
        }
    }

    public MouseActionConfig getConfig() {
        MouseActionConfig mc = new MouseActionConfig();
        mc.setButton(btnRight.isSelected() ? MouseButtonType.RIGHT
                : btnMiddle.isSelected() ? MouseButtonType.MIDDLE : MouseButtonType.LEFT);
        mc.setClickMode((ClickMode) clickModeCombo.getSelectedItem());
        mc.setPositionMode(startPicker.getPositionMode());
        mc.setX(startPicker.getX());
        mc.setY(startPicker.getY());
        mc.setDragStyle(dragSmooth.isSelected() ? DragStyle.SMOOTH : DragStyle.INSTANT);
        mc.setDragSteps((Integer) dragStepsSpinner.getValue());
        mc.setDragDurationMs((Integer) dragDurationSpinner.getValue());
        mc.setDragToX(dragToPicker.getX());
        mc.setDragToY(dragToPicker.getY());
        mc.setHoldDurationMs((Integer) holdDurationSpinner.getValue());
        return mc;
    }

    public void setConfig(MouseActionConfig mc) {
        if (mc == null) {
            return;
        }
        switch (mc.getButton()) {
            case RIGHT: btnRight.setSelected(true); break;
            case MIDDLE: btnMiddle.setSelected(true); break;
            default: btnLeft.setSelected(true);
        }
        clickModeCombo.setSelectedItem(mc.getClickMode());
        startPicker.setPositionMode(mc.getPositionMode());
        startPicker.setX(mc.getX());
        startPicker.setY(mc.getY());
        dragInstant.setSelected(mc.getDragStyle() == DragStyle.INSTANT);
        dragSmooth.setSelected(mc.getDragStyle() != DragStyle.INSTANT);
        dragStepsSpinner.setValue(mc.getDragSteps());
        dragDurationSpinner.setValue(mc.getDragDurationMs());
        dragToPicker.setX(mc.getDragToX());
        dragToPicker.setY(mc.getDragToY());
        holdDurationSpinner.setValue(mc.getHoldDurationMs());
        updateExtraCard();
    }
}
