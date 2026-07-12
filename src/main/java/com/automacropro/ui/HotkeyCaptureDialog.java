package com.automacropro.ui;

import com.automacropro.model.AppSettings;
import com.automacropro.model.HotkeyBinding;
import com.automacropro.util.KeyCodeUtil;
import com.automacropro.hotkey.GlobalHotkeyManager;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom Hotkeys editor for one module's set of controls. Capture uses a
 * temporary JNativeHook listener (same VC_ namespace {@code GlobalHotkeyManager}
 * matches against), so whatever is recorded here works immediately without
 * any code-space translation.
 */
public class HotkeyCaptureDialog extends JDialog {

    private final AppSettings settings;
    private final Map<String, JLabel> currentLabels = new LinkedHashMap<>();
    private boolean changed = false;
    private NativeKeyListener activeCapture; // non-null while a "Set" capture is in progress

    public HotkeyCaptureDialog(Window owner, String title, AppSettings settings, String[] ids, String[] labels) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.settings = settings;

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];

            g.gridx = 0;
            g.gridy = i;
            g.weightx = 0;
            content.add(new JLabel(labels[i] + ":"), g);

            JLabel currentLabel = new JLabel(settings.getHotkey(id).describe());
            currentLabel.setFont(UiTheme.FONT_MONO_SMALL);
            currentLabels.put(id, currentLabel);
            g.gridx = 1;
            g.weightx = 1;
            content.add(currentLabel, g);

            JButton setBtn = new JButton("Set");
            g.gridx = 2;
            g.weightx = 0;
            content.add(setBtn, g);
            setBtn.addActionListener(e -> startCapture(id, setBtn));

            JButton clearBtn = new JButton("Clear");
            g.gridx = 3;
            content.add(clearBtn, g);
            clearBtn.addActionListener(e -> {
                HotkeyBinding hb = new HotkeyBinding(id);
                settings.setHotkey(hb);
                currentLabels.get(id).setText(hb.describe());
                changed = true;
            });
        }

        JButton closeBtn = new JButton("Tutup");
        closeBtn.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(closeBtn);

        setLayout(new BorderLayout());
        add(new JScrollPane(content), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setSize(440, 90 + ids.length * 42);
        setLocationRelativeTo(owner);
    }

    public boolean isChanged() {
        return changed;
    }

    @Override
    public void dispose() {
        // Safety net: if the dialog is closed mid-capture, don't leave a
        // dangling temporary listener registered on the global hook.
        if (activeCapture != null) {
            GlobalScreen.removeNativeKeyListener(activeCapture);
            activeCapture = null;
        }
        super.dispose();
    }

    private void startCapture(String id, JButton sourceButton) {
        if (!GlobalHotkeyManager.getInstance().isHookActive()) {
            JOptionPane.showMessageDialog(this,
                    "Global hook tidak aktif (lihat log), jadi hotkey tidak bisa direkam/berfungsi saat ini.",
                    "Hotkey tidak tersedia", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final String original = sourceButton.getText();
        sourceButton.setText("Tekan tombol...");
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
}
