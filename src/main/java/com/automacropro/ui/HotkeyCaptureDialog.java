package com.automacropro.ui;

import com.automacropro.model.AppSettings;
import com.automacropro.util.I18n;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;

/**
 * Per-module Custom Hotkeys editor, kept as a quick path from each tab's
 * "Configure Hotkeys" button.
 *
 * The capture logic itself lives in {@link HotkeyTablePanel}, shared with the
 * unified table in {@link SettingsDialog} - both edit the same
 * {@link AppSettings} bindings, so the two entry points cannot drift out of
 * agreement.
 */
public class HotkeyCaptureDialog extends JDialog {

    private final HotkeyTablePanel table;

    public HotkeyCaptureDialog(Window owner, String title, AppSettings settings, String[] ids, String[] labels) {
        super(owner, title, ModalityType.APPLICATION_MODAL);

        table = new HotkeyTablePanel(settings);
        for (int i = 0; i < ids.length; i++) {
            // Tolerate a short labels array rather than throwing an
            // ArrayIndexOutOfBounds at a call site that added an id and forgot
            // the matching label.
            table.addBinding(ids[i], i < labels.length ? labels[i] : ids[i]);
        }

        JButton closeBtn = UiTheme.createNeutralButton(I18n.t("common.close"));
        closeBtn.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setOpaque(false);
        south.add(closeBtn);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(table, BorderLayout.NORTH);

        setLayout(new BorderLayout());
        add(new JScrollPane(content), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setSize(460, 110 + ids.length * 40);
        setLocationRelativeTo(owner);
    }

    public boolean isChanged() {
        return table.isChanged();
    }

    @Override
    public void dispose() {
        // Safety net: closing mid-capture must not leave a dangling temporary
        // listener registered on the global hook.
        table.cancelCapture();
        super.dispose();
    }
}
