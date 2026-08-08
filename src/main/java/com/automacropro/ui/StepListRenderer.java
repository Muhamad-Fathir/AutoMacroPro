package com.automacropro.ui;

import com.automacropro.model.MacroStep;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

/**
 * Row renderer for the sequencer list: a per-{@code ActionType} icon (see
 * {@link StepIcon}), the 1-based step number, and the step description.
 *
 * Also paints the active-step indicator. The running step index is pushed in
 * from the panel via {@link #setActiveIndex(int)} and only ever read here on
 * the EDT during painting, so no synchronization is needed on this field - the
 * engine never touches it directly.
 */
final class StepListRenderer implements ListCellRenderer<MacroStep> {

    private static final Color ACTIVE_BG = new Color(0x00, 0x87, 0x93, 90);

    /** Pass to {@link #setActiveIndex(int)} when nothing is running. */
    static final int NO_ACTIVE = -1;

    private int activeIndex = NO_ACTIVE;

    void setActiveIndex(int index) {
        activeIndex = index;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends MacroStep> list, MacroStep value,
                                                 int index, boolean isSelected, boolean cellHasFocus) {
        boolean active = index == activeIndex;
        JLabel label = new JLabel((active ? "▶ " : "") + (index + 1) + ".  " + value.describe());
        label.setIcon(new StepIcon(value.getType()));
        label.setIconTextGap(8);
        label.setOpaque(true);
        label.setFont(active ? UiTheme.FONT_BODY.deriveFont(Font.BOLD) : UiTheme.FONT_BODY);
        label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));

        if (isSelected) {
            label.setBackground(list.getSelectionBackground());
            label.setForeground(list.getSelectionForeground());
        } else if (active) {
            label.setBackground(ACTIVE_BG);
            label.setForeground(UiTheme.ACCENT);
        } else {
            label.setBackground(list.getBackground());
            label.setForeground(list.getForeground());
        }
        return label;
    }
}
