package com.automacropro.ui;

import com.automacropro.model.ActionType;
import com.automacropro.model.KeyActionConfig;
import com.automacropro.model.MacroStep;

import javax.swing.BorderFactory;
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

    private final JComboBox<ActionType> actionTypeCombo = new JComboBox<>(ActionType.values());
    private final CardLayout outerCards = new CardLayout();
    private final JPanel outerCardPanel = new JPanel(outerCards);

    private final MouseActionConfigPanel mousePanel = new MouseActionConfigPanel();
    private final KeyComboCaptureField keyComboField = new KeyComboCaptureField();
    private final JSpinner keyHoldSpinner = new JSpinner(new SpinnerNumberModel(40, 0, 60000, 10));
    private final JSpinner delaySpinner = new JSpinner(new SpinnerNumberModel(500, 0, 3_600_000, 100));

    private boolean confirmed = false;

    public ActionEditorDialog(Window owner, MacroStep existing) {
        super(owner, existing == null ? "Tambah Aksi" : "Edit Aksi", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        typeRow.add(new JLabel("Tipe Aksi:"));
        typeRow.add(actionTypeCombo);
        add(typeRow, BorderLayout.NORTH);

        outerCardPanel.add(mousePanel, CARD_MOUSE);
        outerCardPanel.add(buildKeyboardPanel(), CARD_KEYBOARD);
        outerCardPanel.add(buildDelayPanel(), CARD_DELAY);
        add(outerCardPanel, BorderLayout.CENTER);
        add(buildButtonsRow(), BorderLayout.SOUTH);

        actionTypeCombo.addActionListener(e ->
                outerCards.show(outerCardPanel, ((ActionType) actionTypeCombo.getSelectedItem()).name()));

        if (existing != null) {
            populateFrom(existing);
        }

        setSize(560, 440);
        setLocationRelativeTo(owner);
    }

    private JPanel buildKeyboardPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints g = freshConstraints();
        addRow(panel, g, "Kombinasi Tombol:", keyComboField);
        addRow(panel, g, "Tahan (ms):", keyHoldSpinner);
        return panel;
    }

    private JPanel buildDelayPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints g = freshConstraints();
        addRow(panel, g, "Durasi Delay (ms):", delaySpinner);
        return panel;
    }

    private JPanel buildButtonsRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton ok = UiTheme.createButton("OK", UiTheme.START_BG, UiTheme.START_FG);
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> onOk());
        cancel.addActionListener(e -> dispose());
        row.add(cancel);
        row.add(ok);
        return row;
    }

    private void onOk() {
        if ((ActionType) actionTypeCombo.getSelectedItem() == ActionType.KEYBOARD
                && keyComboField.getVkCodes().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Silakan rekam kombinasi tombol terlebih dahulu.",
                    "Kombinasi belum diatur", JOptionPane.WARNING_MESSAGE);
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
    }

    /** @return the built step if the user pressed OK, otherwise null. */
    public MacroStep getResult() {
        if (!confirmed) {
            return null;
        }
        ActionType type = (ActionType) actionTypeCombo.getSelectedItem();
        MacroStep step = new MacroStep(type);
        if (type == ActionType.MOUSE) {
            step.setMouseConfig(mousePanel.getConfig());
        } else if (type == ActionType.KEYBOARD) {
            KeyActionConfig kc = new KeyActionConfig();
            kc.setVkCodes(keyComboField.getVkCodes());
            kc.setHoldMs((Integer) keyHoldSpinner.getValue());
            step.setKeyConfig(kc);
        } else {
            step.setDelayMs(((Integer) delaySpinner.getValue()).longValue());
        }
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
