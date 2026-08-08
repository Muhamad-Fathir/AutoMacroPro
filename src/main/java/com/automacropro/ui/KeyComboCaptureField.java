package com.automacropro.ui;

import com.automacropro.util.I18n;
import com.automacropro.util.KeyCodeUtil;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Lets the user record a key combination (single key or e.g. Ctrl+Shift+A)
 * by literally pressing it while this field has focus. This is a plain,
 * local Swing {@code KeyListener} - NOT a global hook - because it only
 * needs to observe input while the Action Editor dialog itself is focused;
 * the combo recorded here is later replayed via {@code Robot.keyPress},
 * which speaks AWT VK_* codes, matching what this field records.
 */
public class KeyComboCaptureField extends JPanel {

    private final JTextField display = new JTextField(20);
    private final JButton recordButton = new JButton(I18n.t("keycombo.record"));
    private List<Integer> vkCodes = new ArrayList<>();

    public KeyComboCaptureField() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 2));
        display.setEditable(false);
        display.setText(I18n.t("common.unset"));
        add(display);
        add(recordButton);

        recordButton.addActionListener(e -> startRecording());
    }

    private void startRecording() {
        recordButton.setText(I18n.t("keycombo.pressing"));
        recordButton.setEnabled(false);
        display.setText("...");
        display.requestFocusInWindow();

        KeyAdapter recorder = new KeyAdapter() {
            private final List<Integer> combo = new ArrayList<>();

            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (!combo.contains(code)) {
                    combo.add(code);
                }
                if (!KeyCodeUtil.isAwtModifier(code)) {
                    // The first non-modifier key finalizes the combo (modifiers
                    // pressed so far + this key), mirroring how a real shortcut
                    // like Ctrl+Shift+A is actually pressed by a human.
                    finish(this, new ArrayList<>(combo));
                }
            }
        };
        display.addKeyListener(recorder);
    }

    private void finish(KeyAdapter recorder, List<Integer> codes) {
        vkCodes = codes;
        display.setText(describe(codes));
        recordButton.setText(I18n.t("keycombo.record"));
        recordButton.setEnabled(true);
        display.removeKeyListener(recorder);
    }

    private String describe(List<Integer> codes) {
        if (codes.isEmpty()) {
            return "(belum diatur)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) {
                sb.append(" + ");
            }
            sb.append(KeyCodeUtil.vkToDisplayName(codes.get(i)));
        }
        return sb.toString();
    }

    public List<Integer> getVkCodes() {
        return vkCodes;
    }

    public void setVkCodes(List<Integer> codes) {
        this.vkCodes = (codes == null) ? new ArrayList<>() : new ArrayList<>(codes);
        display.setText(describe(this.vkCodes));
    }
}
