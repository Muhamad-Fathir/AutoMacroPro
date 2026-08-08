package com.automacropro.ui;

import com.automacropro.model.ActionType;
import com.automacropro.model.KeyActionConfig;
import com.automacropro.model.MacroStep;
import com.automacropro.model.ScrollActionConfig;
import com.automacropro.util.I18n;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

/**
 * The Macro Sequencer's "Action Panel": add/edit one {@link MacroStep}.
 *
 * An outer {@link CardLayout} switches between the Mouse / Keyboard / Delay
 * editors. The Mouse editor itself is the shared {@link MouseActionConfigPanel},
 * which already contains the "Hold Click" dropdown option and its conditional
 * "Hold Duration (ms)" field - implemented once, used here and in the
 * Autoclicker module.
 */
public class ActionEditorDialog extends JDialog {

    private static final String CARD_MOUSE = "MOUSE";
    private static final String CARD_KEYBOARD = "KEYBOARD";
    private static final String CARD_DELAY = "DELAY";
    private static final String CARD_SCROLL = "SCROLL";

    private final JComboBox<ActionType> actionTypeCombo = new JComboBox<>(ActionType.values());
    private final CardLayout outerCards = new CardLayout();
    private final JPanel outerCardPanel = new JPanel(outerCards);

    private final MouseActionConfigPanel mousePanel = new MouseActionConfigPanel();
    private final KeyComboCaptureField keyComboField = new KeyComboCaptureField();
    private final JSpinner keyHoldSpinner = new JSpinner(new SpinnerNumberModel(40, 0, 60000, 10));
    private final JSpinner delaySpinner = new JSpinner(new SpinnerNumberModel(500, 0, 3_600_000, 100));

    private final JComboBox<ScrollActionConfig.ScrollDirection> scrollDirCombo =
            new JComboBox<>(ScrollActionConfig.ScrollDirection.values());
    private final JSpinner scrollNotchSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 1000, 1));

    /**
     * Pre-delay applies to every action type, so it lives outside the
     * CardLayout - one field shared by all three editors rather than three
     * copies that could drift apart.
     */
    private final JSpinner preDelaySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 3_600_000, 50));

    private boolean confirmed = false;

    public ActionEditorDialog(Window owner, MacroStep existing) {
        super(owner, I18n.t(existing == null ? "action.addTitle" : "action.editTitle"),
                ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        typeRow.add(new JLabel(I18n.t("action.type")));
        typeRow.add(actionTypeCombo);
        typeRow.add(Box.createHorizontalStrut(12));
        typeRow.add(new JLabel(I18n.t("action.preDelay")));
        typeRow.add(preDelaySpinner);
        add(typeRow, BorderLayout.NORTH);

        outerCardPanel.add(mousePanel, CARD_MOUSE);
        outerCardPanel.add(buildKeyboardPanel(), CARD_KEYBOARD);
        outerCardPanel.add(buildDelayPanel(), CARD_DELAY);
        outerCardPanel.add(buildScrollPanel(), CARD_SCROLL);
        add(outerCardPanel, BorderLayout.CENTER);
        add(buildButtonsRow(), BorderLayout.SOUTH);

        actionTypeCombo.addActionListener(e ->
                outerCards.show(outerCardPanel, ((ActionType) actionTypeCombo.getSelectedItem()).name()));
        // Without a renderer the combo shows the raw enum constants
        // (MOUSE / KEYBOARD / DELAY), which are neither translated nor
        // capitalised the way the rest of the UI is.
        actionTypeCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ActionType) {
                    setText(I18n.t("action.type." + ((ActionType) value).name().toLowerCase()));
                }
                return this;
            }
        });

        if (existing != null) {
            populateFrom(existing);
        }

        // Covers keyHold/delay/preDelay here and every spinner inside the
        // nested mouse panel: commit-on-keystroke, clamping, and wheel support.
        NumericInput.hardenAll(this);

        setSize(560, 470);
        setLocationRelativeTo(owner);
    }

    private JPanel buildKeyboardPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints g = freshConstraints();
        addRow(panel, g, I18n.t("action.keyCombo"), keyComboField);
        addRow(panel, g, I18n.t("action.keyHold"), keyHoldSpinner);
        return panel;
    }

    private JPanel buildDelayPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints g = freshConstraints();
        addRow(panel, g, I18n.t("action.delayDuration"), delaySpinner);
        return panel;
    }

    private JPanel buildScrollPanel() {
        // Localize the two enum constants the same way the action-type combo is.
        scrollDirCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ScrollActionConfig.ScrollDirection) {
                    setText(I18n.t("scroll.dir." + value.toString().toLowerCase()));
                }
                return this;
            }
        });
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints g = freshConstraints();
        addRow(panel, g, I18n.t("scroll.direction"), scrollDirCombo);
        addRow(panel, g, I18n.t("scroll.amount"), scrollNotchSpinner);
        return panel;
    }

    private JPanel buildButtonsRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton ok = UiTheme.createButton(I18n.t("common.ok"), UiTheme.START_BG, UiTheme.START_FG);
        JButton cancel = UiTheme.createNeutralButton(I18n.t("common.cancel"));
        ok.addActionListener(e -> onOk());
        cancel.addActionListener(e -> dispose());
        row.add(cancel);
        row.add(ok);
        return row;
    }

    private void onOk() {
        if ((ActionType) actionTypeCombo.getSelectedItem() == ActionType.KEYBOARD
                && keyComboField.getVkCodes().isEmpty()) {
            JOptionPane.showMessageDialog(this, I18n.t("action.noComboBody"),
                    I18n.t("action.noComboTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        confirmed = true;
        dispose();
    }

    private void populateFrom(MacroStep step) {
        actionTypeCombo.setSelectedItem(step.getType());
        outerCards.show(outerCardPanel, step.getType().name());

        if (step.getMouseConfig() != null) {
            mousePanel.setConfig(step.getMouseConfig());
        }
        KeyActionConfig kc = step.getKeyConfig();
        if (kc != null) {
            keyComboField.setVkCodes(kc.getVkCodes());
            keyHoldSpinner.setValue(kc.getHoldMs());
        }
        delaySpinner.setValue((int) step.getDelayMs());
        if (step.getScrollConfig() != null) {
            scrollDirCombo.setSelectedItem(step.getScrollConfig().getDirection());
            scrollNotchSpinner.setValue(step.getScrollConfig().getNotches());
        }
        preDelaySpinner.setValue((int) Math.min(Integer.MAX_VALUE, step.getPreDelayMs()));
    }

    /** @return the built step if the user pressed OK, otherwise null. */
    public MacroStep getResult() {
        if (!confirmed) {
            return null;
        }
        NumericInput.clampAll(this);
        ActionType type = (ActionType) actionTypeCombo.getSelectedItem();
        MacroStep step = new MacroStep(type);
        if (type == ActionType.MOUSE) {
            step.setMouseConfig(mousePanel.getConfig());
        } else if (type == ActionType.KEYBOARD) {
            KeyActionConfig kc = new KeyActionConfig();
            kc.setVkCodes(keyComboField.getVkCodes());
            kc.setHoldMs((Integer) keyHoldSpinner.getValue());
            step.setKeyConfig(kc);
        } else if (type == ActionType.SCROLL) {
            ScrollActionConfig sc = new ScrollActionConfig();
            sc.setDirection((ScrollActionConfig.ScrollDirection) scrollDirCombo.getSelectedItem());
            sc.setNotches((Integer) scrollNotchSpinner.getValue());
            step.setScrollConfig(sc);
        } else {
            step.setDelayMs(((Number) delaySpinner.getValue()).longValue());
        }
        // Outside the branch: pre-delay applies to every type.
        step.setPreDelayMs(((Number) preDelaySpinner.getValue()).longValue());
        return step;
    }

    /** Convenience static launcher mirroring JOptionPane's style. */
    public static MacroStep showDialog(Window owner, MacroStep existing) {
        ActionEditorDialog dlg = new ActionEditorDialog(owner, existing);
        dlg.setVisible(true);
        return dlg.getResult();
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
