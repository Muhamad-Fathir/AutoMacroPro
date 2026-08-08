package com.automacropro.ui;

import com.automacropro.hotkey.GlobalHotkeyManager;
import com.automacropro.model.AppSettings;
import com.automacropro.model.HotkeyBinding;
import com.automacropro.util.I18n;
import com.automacropro.util.KeyCodeUtil;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A reusable "binding + Set + Clear" table for a set of hotkey ids.
 *
 * Extracted from {@code HotkeyCaptureDialog} so the per-tab dialogs and the
 * unified table in {@link SettingsDialog} share one implementation - two copies
 * of live global-hook capture logic is exactly the kind of duplication that
 * ends with one of them leaking a listener.
 *
 * Capture uses a temporary JNativeHook listener in the same {@code VC_}
 * namespace {@code GlobalHotkeyManager} matches against, so a recorded key
 * works immediately with no code-space translation.
 */
public class HotkeyTablePanel extends JPanel {

    private final AppSettings settings;
    private final Map<String, JLabel> currentLabels = new LinkedHashMap<>();
    private NativeKeyListener activeCapture; // non-null while a capture is in progress
    private boolean changed;

    private final GridBagConstraints gbc = new GridBagConstraints();
    private int row;

    public HotkeyTablePanel(AppSettings settings) {
        this.settings = settings;
        setLayout(new GridBagLayout());
        setOpaque(false);
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
    }

    /** A non-interactive group heading, e.g. "Autoclicker". */
    public void addSection(String title) {
        JLabel heading = new JLabel(title);
        heading.setFont(UiTheme.FONT_TITLE.deriveFont(12f));
        heading.setForeground(UiTheme.ACCENT);
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 4;
        gbc.insets = new Insets(12, 4, 3, 4);
        add(heading, gbc);
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.gridwidth = 1;
    }

    /** One editable binding row. */
    public void addBinding(String id, String label) {
        gbc.gridy = row++;

        gbc.gridx = 0;
        gbc.weightx = 0;
        add(new JLabel(label + ":"), gbc);

        JLabel current = new JLabel(settings.getHotkey(id).describe());
        current.setFont(UiTheme.FONT_MONO_SMALL);
        current.setForeground(UiTheme.TEXT);
        currentLabels.put(id, current);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(current, gbc);

        JButton setBtn = UiTheme.createNeutralButton(I18n.t("hotkey.set"));
        gbc.gridx = 2;
        gbc.weightx = 0;
        add(setBtn, gbc);
        setBtn.addActionListener(e -> startCapture(id, setBtn));

        JButton clearBtn = UiTheme.createNeutralButton(I18n.t("hotkey.clear"));
        gbc.gridx = 3;
        add(clearBtn, gbc);
        clearBtn.addActionListener(e -> {
            HotkeyBinding cleared = new HotkeyBinding(id);
            settings.setHotkey(cleared);
            currentLabels.get(id).setText(cleared.describe());
            changed = true;
        });
    }

    public boolean isChanged() {
        return changed;
    }

    /**
     * Removes any in-progress capture listener. Callers MUST invoke this when
     * the containing dialog closes, or a dialog dismissed mid-capture leaves a
     * listener registered on the app-wide hook forever.
     */
    public void cancelCapture() {
        if (activeCapture != null) {
            GlobalScreen.removeNativeKeyListener(activeCapture);
            activeCapture = null;
        }
    }

    private void startCapture(String id, JButton sourceButton) {
        if (!GlobalHotkeyManager.getInstance().isHookActive()) {
            JOptionPane.showMessageDialog(this, I18n.t("hotkey.noHookBody"),
                    I18n.t("hotkey.noHookTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        cancelCapture(); // only one capture at a time
        final String original = sourceButton.getText();
        sourceButton.setText(I18n.t("hotkey.pressKey"));
        sourceButton.setEnabled(false);

        final Set<Integer> pressed = ConcurrentHashMap.newKeySet();
        activeCapture = new NativeKeyListener() {
            @Override
            public void nativeKeyPressed(NativeKeyEvent e) {
                int code = e.getKeyCode();
                pressed.add(code);
                if (KeyCodeUtil.isNativeModifier(code)) {
                    return; // wait for a real, non-modifier trigger key
                }
                HotkeyBinding hb = new HotkeyBinding(id);
                hb.setCtrl(pressed.contains(NativeKeyEvent.VC_CONTROL));
                hb.setShift(pressed.contains(NativeKeyEvent.VC_SHIFT));
                hb.setAlt(pressed.contains(NativeKeyEvent.VC_ALT));
                hb.setTriggerVcCode(code);

                GlobalScreen.removeNativeKeyListener(this);
                activeCapture = null;
                SwingUtilities.invokeLater(() -> {
                    settings.setHotkey(hb);
                    currentLabels.get(id).setText(hb.describe());
                    sourceButton.setText(original);
                    sourceButton.setEnabled(true);
                    changed = true;
                });
            }

            @Override
            public void nativeKeyReleased(NativeKeyEvent e) {
                pressed.remove(e.getKeyCode());
            }

            @Override
            public void nativeKeyTyped(NativeKeyEvent e) {
            }
        };
        GlobalScreen.addNativeKeyListener(activeCapture);
    }

    /** Wraps this table in a left-aligned column so it does not stretch oddly. */
    public JPanel inColumn() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setAlignmentX(LEFT_ALIGNMENT);
        column.add(this);
        // Glue pushes the rows to the top; no setPreferredSize here on purpose -
        // a hardcoded width is what clipped the "Import Project..." button once
        // the bundled fonts landed, and the Indonesian labels are longer than
        // the English ones. The enclosing JScrollPane handles overflow.
        column.add(Box.createVerticalGlue());
        return column;
    }
}
