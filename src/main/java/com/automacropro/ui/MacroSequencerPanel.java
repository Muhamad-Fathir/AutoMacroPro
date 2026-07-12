package com.automacropro.ui;

import com.automacropro.engine.MacroEngine;
import com.automacropro.hotkey.GlobalHotkeyManager;
import com.automacropro.model.AppSettings;
import com.automacropro.model.LoopMode;
import com.automacropro.model.MacroProject;
import com.automacropro.model.MacroStep;
import com.automacropro.persistence.MacroProjectIO;
import com.automacropro.persistence.SettingsManager;
import com.automacropro.util.AppLogger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.AWTException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Module 2: Chronological & Repeatable Input Automation (Macro Sequencer).
 */
public class MacroSequencerPanel extends JPanel {

    private final AppSettings appSettings;
    private MacroEngine engine; // null if Robot failed to initialize
    private final AutomationControlBar controlBar;

    private MacroProject project = new MacroProject();
    private final DefaultListModel<MacroStep> listModel = new DefaultListModel<>();
    private final JList<MacroStep> stepList = new JList<>(listModel);

    private final JTextField projectNameField = new JTextField(18);
    private final JRadioButton loopOnce = new JRadioButton("1x (Once)", true);
    private final JRadioButton loopInfinite = new JRadioButton("Infinite");

    public MacroSequencerPanel(AppSettings appSettings) {
        this.appSettings = appSettings;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        topRow.add(new JLabel("Nama Project:"));
        topRow.add(projectNameField);
        ButtonGroup loopGroup = new ButtonGroup();
        loopGroup.add(loopOnce);
        loopGroup.add(loopInfinite);
        topRow.add(new JLabel("    Loop:"));
        topRow.add(loopOnce);
        topRow.add(loopInfinite);
        JButton exportBtn = new JButton("Export Project...");
        JButton importBtn = new JButton("Import Project...");
        topRow.add(exportBtn);
        topRow.add(importBtn);

        stepList.setCellRenderer(stepRenderer());
        stepList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onEdit();
                }
            }
        });
        JScrollPane listScroll = new JScrollPane(stepList);
        listScroll.setPreferredSize(new Dimension(420, 240));

        JButton addBtn = new JButton("Add Action");
        JButton editBtn = new JButton("Edit Action");
        JButton removeBtn = new JButton("Remove Action");
        JButton upBtn = new JButton("Move Up");
        JButton downBtn = new JButton("Move Down");
        JPanel listButtons = new JPanel();
        listButtons.setLayout(new BoxLayout(listButtons, BoxLayout.Y_AXIS));
        for (JButton b : new JButton[]{addBtn, editBtn, removeBtn, upBtn, downBtn}) {
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(170, 28));
            b.setPreferredSize(new Dimension(170, 28));
            listButtons.add(b);
            listButtons.add(Box.createVerticalStrut(6));
        }

        JPanel center = new JPanel(new BorderLayout(8, 0));
        center.add(listScroll, BorderLayout.CENTER);
        center.add(listButtons, BorderLayout.EAST);

        JPanel centerWrap = new JPanel(new BorderLayout(0, 8));
        centerWrap.add(topRow, BorderLayout.NORTH);
        centerWrap.add(center, BorderLayout.CENTER);

        controlBar = new AutomationControlBar(new AutomationControlBar.Listener() {
            @Override public void onStart() { handleStart(); }
            @Override public void onStop() { handleStop(); }
            @Override public void onToggle() { handleToggle(); }
            @Override public void onSaveSettings() { handleSave(); }
            @Override public void onResetSettings() { handleReset(); }
            @Override public void onConfigureHotkeys() { handleConfigureHotkeys(); }
            @Override public void onFailsafeToggle(boolean enabled) { handleFailsafeToggle(enabled); }
        });

        add(centerWrap, BorderLayout.CENTER);
        add(controlBar, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> onAdd());
        editBtn.addActionListener(e -> onEdit());
        removeBtn.addActionListener(e -> onRemove());
        upBtn.addActionListener(e -> onMove(-1));
        downBtn.addActionListener(e -> onMove(1));
        exportBtn.addActionListener(e -> onExport());
        importBtn.addActionListener(e -> onImport());

        projectNameField.setText(project.getName());
        LoopMode lastLoop = appSettings.getLastMacroLoopMode();
        loopInfinite.setSelected(lastLoop == LoopMode.INFINITE);
        loopOnce.setSelected(lastLoop != LoopMode.INFINITE);

        registerHotkeys();
        com.automacropro.engine.FailsafeMonitor.setEnabled(appSettings.isFailsafeEnabled());
        controlBar.setFailsafeChecked(appSettings.isFailsafeEnabled());

        try {
            engine = new MacroEngine(new MacroEngine.Listener() {
                @Override
                public void onStarted() {
                    controlBar.setRunningState(true);
                    controlBar.setStatusText("Running...");
                }

                @Override
                public void onFinished(long stepsExecuted, int loopsCompleted, MacroEngine.StopReason reason) {
                    controlBar.setRunningState(false);
                    controlBar.setStatusText(describeFinish(stepsExecuted, loopsCompleted, reason));
                }
            });
        } catch (AWTException ex) {
            AppLogger.error("Gagal inisialisasi java.awt.Robot pada MacroSequencerPanel", ex);
            controlBar.setStatusText("Gagal inisialisasi Robot - lihat log. Coba jalankan sebagai Administrator.");
        }
    }

    private ListCellRenderer<MacroStep> stepRenderer() {
        return (list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel((index + 1) + ". " + value.describe());
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            return label;
        };
    }

    // ---- step list editing ----

    private void onAdd() {
        MacroStep step = ActionEditorDialog.showDialog(SwingUtilities.getWindowAncestor(this), null);
        if (step != null) {
            listModel.addElement(step);
            syncStepsIntoProject();
        }
    }

    private void onEdit() {
        int idx = stepList.getSelectedIndex();
        if (idx < 0) {
            return;
        }
        MacroStep edited = ActionEditorDialog.showDialog(SwingUtilities.getWindowAncestor(this), listModel.get(idx));
        if (edited != null) {
            listModel.set(idx, edited);
            syncStepsIntoProject();
        }
    }

    private void onRemove() {
        int idx = stepList.getSelectedIndex();
        if (idx < 0) {
            return;
        }
        listModel.remove(idx);
        syncStepsIntoProject();
    }

    private void onMove(int delta) {
        int idx = stepList.getSelectedIndex();
        int newIdx = idx + delta;
        if (idx < 0 || newIdx < 0 || newIdx >= listModel.size()) {
            return;
        }
        MacroStep step = listModel.remove(idx);
        listModel.add(newIdx, step);
        stepList.setSelectedIndex(newIdx);
        syncStepsIntoProject();
    }

    private void syncStepsIntoProject() {
        List<MacroStep> steps = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            steps.add(listModel.get(i));
        }
        project.setSteps(steps);
        stepList.repaint(); // numbering ("1.", "2.", ...) depends on index, so repaint after reorder
    }

    private void refreshListFromProject() {
        listModel.clear();
        for (MacroStep s : project.getSteps()) {
            listModel.addElement(s);
        }
    }

    // ---- export / import ----

    private void onExport() {
        syncStepsIntoProject();
        project.setName(projectNameField.getText().isBlank() ? "Untitled Macro" : projectNameField.getText());
        project.setLoopMode(loopInfinite.isSelected() ? LoopMode.INFINITE : LoopMode.ONCE);

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                "AutoMacro Pro Project (*." + MacroProjectIO.EXTENSION + ")", MacroProjectIO.EXTENSION));
        chooser.setSelectedFile(new File(project.getName() + "." + MacroProjectIO.EXTENSION));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith("." + MacroProjectIO.EXTENSION)) {
                file = new File(file.getParentFile(), file.getName() + "." + MacroProjectIO.EXTENSION);
            }
            try {
                MacroProjectIO.save(project, file.toPath());
                controlBar.setStatusText("Project diekspor ke " + file.getName());
            } catch (IOException ex) {
                AppLogger.error("Gagal export project ke " + file, ex);
                JOptionPane.showMessageDialog(this, "Gagal menyimpan file:\n" + ex.getMessage(),
                        "Export gagal", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onImport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                "AutoMacro Pro Project (*." + MacroProjectIO.EXTENSION + ")", MacroProjectIO.EXTENSION));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath();
            MacroProjectIO.LoadResult result = MacroProjectIO.load(path);
            if (result.success) {
                project = result.project;
                projectNameField.setText(project.getName());
                loopInfinite.setSelected(project.getLoopMode() == LoopMode.INFINITE);
                loopOnce.setSelected(project.getLoopMode() != LoopMode.INFINITE);
                refreshListFromProject();
                controlBar.setStatusText("Project diimpor: " + project.getName()
                        + " (" + project.getSteps().size() + " step)");
            } else {
                JOptionPane.showMessageDialog(this, result.errorMessage, "Import gagal", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ---- control bar handlers ----

    private void handleStart() {
        if (engine == null) {
            JOptionPane.showMessageDialog(this, "Robot gagal diinisialisasi sebelumnya. Restart aplikasi.",
                    "Tidak bisa Start", JOptionPane.ERROR_MESSAGE);
            return;
        }
        syncStepsIntoProject();
        project.setName(projectNameField.getText().isBlank() ? "Untitled Macro" : projectNameField.getText());
        LoopMode mode = loopInfinite.isSelected() ? LoopMode.INFINITE : LoopMode.ONCE;
        project.setLoopMode(mode);
        appSettings.setLastMacroLoopMode(mode);
        engine.start(project);
    }

    private void handleStop() {
        if (engine != null) {
            engine.stop();
        }
    }

    private void handleToggle() {
        if (engine != null) {
            engine.togglePause();
            controlBar.setStatusText(engine.isPaused() ? "Paused" : "Running...");
        }
    }

    /** Per spec, "Save Settings" here persists loop-mode preference + hotkeys; the
     *  step sequence itself is saved/loaded explicitly via Export/Import Project. */
    private void handleSave() {
        appSettings.setLastMacroLoopMode(loopInfinite.isSelected() ? LoopMode.INFINITE : LoopMode.ONCE);
        boolean ok = SettingsManager.save(appSettings);
        controlBar.setStatusText(ok ? "Settings (loop mode & hotkeys) disimpan."
                : "Gagal menyimpan settings (lihat log).");
    }

    private void handleReset() {
        loopOnce.setSelected(true);
        loopInfinite.setSelected(false);
        controlBar.setStatusText("Loop mode dikembalikan ke default (1x). Klik 'Save Settings' untuk menyimpan.");
    }

    private void handleConfigureHotkeys() {
        String[] ids = {AppSettings.HK_MACRO_START, AppSettings.HK_MACRO_STOP, AppSettings.HK_MACRO_TOGGLE};
        String[] labels = {"Start", "Stop", "Toggle (Pause/Resume)"};
        HotkeyCaptureDialog dlg = new HotkeyCaptureDialog(
                SwingUtilities.getWindowAncestor(this), "Custom Hotkeys - Macro Sequencer", appSettings, ids, labels);
        dlg.setVisible(true);
        if (dlg.isChanged()) {
            registerHotkeys();
            SettingsManager.save(appSettings);
        }
    }

    private void handleFailsafeToggle(boolean enabled) {
        com.automacropro.engine.FailsafeMonitor.setEnabled(enabled);
        appSettings.setFailsafeEnabled(enabled);
        SettingsManager.save(appSettings);
        controlBar.setStatusText(enabled ? "Failsafe diaktifkan." : "Failsafe dimatikan - tidak ada killswitch otomatis.");
    }

    private void registerHotkeys() {
        GlobalHotkeyManager hk = GlobalHotkeyManager.getInstance();
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_MACRO_START), () -> controlBar.getStartButton().doClick());
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_MACRO_STOP), () -> controlBar.getStopButton().doClick());
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_MACRO_TOGGLE), () -> controlBar.getToggleButton().doClick());
    }

    private String describeFinish(long steps, int loops, MacroEngine.StopReason reason) {
        String why;
        switch (reason) {
            case COMPLETED_ONCE: why = "selesai 1x putaran"; break;
            case FAILSAFE: why = "FAILSAFE aktif (kursor menyentuh pojok layar)"; break;
            case ERROR: why = "berhenti karena error - lihat log"; break;
            case EMPTY_PROJECT: why = "sequence kosong, tidak ada yang dijalankan"; break;
            default: why = "dihentikan manual";
        }
        return "Selesai - " + steps + " step / " + loops + " loop (" + why + ")";
    }
}
