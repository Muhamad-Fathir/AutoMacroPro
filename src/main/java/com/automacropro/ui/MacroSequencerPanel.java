package com.automacropro.ui;

import com.automacropro.engine.MacroEngine;
import com.automacropro.engine.MacroRecorder;
import com.automacropro.hotkey.GlobalHotkeyManager;
import com.automacropro.model.AppSettings;
import com.automacropro.model.LoopMode;
import com.automacropro.model.MacroProject;
import com.automacropro.model.MacroStep;
import com.automacropro.persistence.MacroProjectIO;
import com.automacropro.persistence.SettingsManager;
import com.automacropro.util.AppLogger;
import com.automacropro.util.I18n;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.AWTException;
import java.awt.event.KeyEvent;
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
    private final JRadioButton loopOnce = new JRadioButton(I18n.t("macro.loop.once"), true);
    private final JRadioButton loopInfinite = new JRadioButton(I18n.t("macro.loop.infinite"));
    private final StepListRenderer renderer = new StepListRenderer();

    private final MacroRecorder recorder = new MacroRecorder();
    private final JButton recordBtn = UiTheme.createNeutralButton(I18n.t("macro.record"));

    public MacroSequencerPanel(AppSettings appSettings) {
        this.appSettings = appSettings;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Horizontal BoxLayout, NOT FlowLayout. FlowLayout silently wraps when the
        // row does not fit, and its preferredLayoutSize only ever reports a single
        // row's height - so the wrapped remainder is allotted no vertical space and
        // vanishes with no scrollbar or clipping to hint at it. That was the
        // "Import Project... is missing until I widen the window" bug: with the
        // bundled Poppins this row wants 855px but only ~820px was usable at the
        // old 880px window width (the SansSerif fallback wanted 797px and fit,
        // which is why the button only disappeared once the fonts were added).
        // BoxLayout never wraps, so a too-narrow window is visibly cramped rather
        // than quietly hiding a control - and MainFrame now sizes itself from the
        // real preferred width, so it starts wide enough regardless of font or DPI.
        GlassPanel topRow = new GlassPanel(new BorderLayout(), I18n.t("macro.group.project"));
        topRow.setLayout(new BoxLayout(topRow, BoxLayout.X_AXIS));
        // BoxLayout hands surplus width to any child with an unbounded maximum;
        // without this the text field would swallow all of it.
        projectNameField.setMaximumSize(projectNameField.getPreferredSize());
        ButtonGroup loopGroup = new ButtonGroup();
        loopGroup.add(loopOnce);
        loopGroup.add(loopInfinite);
        JButton exportBtn = UiTheme.createNeutralButton(I18n.t("macro.export"));
        JButton importBtn = UiTheme.createNeutralButton(I18n.t("macro.import"));
        topRow.add(new JLabel(I18n.t("macro.projectName")));
        topRow.add(Box.createHorizontalStrut(6));
        topRow.add(projectNameField);
        topRow.add(Box.createHorizontalStrut(18));
        topRow.add(new JLabel(I18n.t("macro.loop")));
        topRow.add(Box.createHorizontalStrut(6));
        topRow.add(loopOnce);
        topRow.add(loopInfinite);
        topRow.add(Box.createHorizontalStrut(18));
        topRow.add(exportBtn);
        topRow.add(Box.createHorizontalStrut(6));
        topRow.add(importBtn);
        // Soaks up leftover width so everything above stays left-aligned.
        topRow.add(Box.createHorizontalGlue());

        stepList.setCellRenderer(renderer);
        // MULTIPLE_INTERVAL_SELECTION is what makes Ctrl+A, Ctrl+Click and
        // Shift+Click work - the latter two need no code at all, they are built
        // into JList's mouse handling once the mode allows it.
        stepList.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        // Drag & drop reordering. INSERT (not ON) so the drop indicator is a
        // line *between* rows - dropping "onto" a step has no meaning here.
        stepList.setDragEnabled(true);
        stepList.setDropMode(javax.swing.DropMode.INSERT);
        stepList.setTransferHandler(new StepListTransferHandler(this::syncStepsIntoProject));
        // WHEN_FOCUSED bindings only fire while the list actually holds focus,
        // and clicking a JList does not always grant it (a self-painted button
        // elsewhere in the panel can keep it). Requesting it explicitly on press
        // is what makes Ctrl+C/X/V reachable at all.
        stepList.setFocusable(true);
        stepList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                stepList.requestFocusInWindow();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onEdit();
                }
            }
        });
        JScrollPane listScroll = new JScrollPane(stepList);
        listScroll.setPreferredSize(new Dimension(420, 240));
        listScroll.setBorder(BorderFactory.createLineBorder(UiTheme.GLASS_BORDER));

        JButton addBtn = UiTheme.createNeutralButton(I18n.t("macro.add"));
        JButton editBtn = UiTheme.createNeutralButton(I18n.t("macro.edit"));
        JButton removeBtn = UiTheme.createNeutralButton(I18n.t("macro.remove"));
        JButton upBtn = UiTheme.createNeutralButton(I18n.t("macro.moveUp"));
        JButton downBtn = UiTheme.createNeutralButton(I18n.t("macro.moveDown"));
        JButton clearBtn = UiTheme.createNeutralButton(I18n.t("macro.clear"));

        JPanel listButtons = new JPanel();
        listButtons.setOpaque(false);
        listButtons.setLayout(new BoxLayout(listButtons, BoxLayout.Y_AXIS));
        for (JButton b : new JButton[]{addBtn, editBtn, removeBtn, upBtn, downBtn, clearBtn, recordBtn}) {
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(170, 28));
            b.setPreferredSize(new Dimension(170, 28));
            listButtons.add(b);
            listButtons.add(Box.createVerticalStrut(6));
        }
        JLabel dndHint = new JLabel(I18n.t("macro.dndHint"));
        dndHint.setFont(UiTheme.FONT_BODY.deriveFont(11f));
        dndHint.setForeground(UiTheme.MUTED_TEXT);
        dndHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        listButtons.add(dndHint);

        GlassPanel center = new GlassPanel(new BorderLayout(8, 0), I18n.t("macro.group.steps"));
        center.add(listScroll, BorderLayout.CENTER);
        center.add(listButtons, BorderLayout.EAST);

        JPanel centerWrap = new JPanel(new BorderLayout(0, 8));
        centerWrap.setOpaque(false);
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
        UiTheme.deopaque(this);
        NumericInput.hardenAll(this);
        installKeyBindings();

        addBtn.addActionListener(e -> onAdd());
        clearBtn.addActionListener(e -> onClearSequence());
        recordBtn.addActionListener(e -> onToggleRecording());
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
        com.automacropro.engine.FailsafeMonitor.addAlertListener(controlBar::flashFailsafe);
        controlBar.setFailsafeChecked(appSettings.isFailsafeEnabled());

        try {
            engine = new MacroEngine(new MacroEngine.Listener() {
                @Override
                public void onStarted() {
                    controlBar.setRunningState(true);
                    controlBar.setStatusText(I18n.t("status.running"));
                }

                @Override
                public void onStepStarted(int stepIndex) {
                    setActiveStep(stepIndex);
                }

                @Override
                public void onFinished(long stepsExecuted, int loopsCompleted, MacroEngine.StopReason reason) {
                    controlBar.setRunningState(false);
                    setActiveStep(StepListRenderer.NO_ACTIVE);
                    controlBar.setStatusText(describeFinish(stepsExecuted, loopsCompleted, reason));
                }
            });
        } catch (AWTException ex) {
            AppLogger.error("Gagal inisialisasi java.awt.Robot pada MacroSequencerPanel", ex);
            controlBar.setStatusText(I18n.t("status.robotFailed"));
        }
    }

    /**
     * Starts or stops the recorder.
     *
     * The recorder observes input globally, so it also sees the very click that
     * pressed this button. Recording therefore begins after a short countdown
     * rather than instantly, otherwise every recording opens with a stray click
     * on the Record button itself. Stopping has the same problem in reverse,
     * which is why the recorder drops the trailing click when it stops.
     */
    private void onToggleRecording() {
        if (recorder.isRecording()) {
            recorder.stop();
            return;
        }
        if (engine != null && engine.isRunning()) {
            controlBar.setStatusText(I18n.t("rec.busyRunning"));
            return;
        }
        recordBtn.setEnabled(false);
        controlBar.setStatusText(I18n.t("rec.countdown"));

        // Swing Timer: fires on the EDT, so no thread juggling, and the UI stays
        // responsive during the countdown (Thread.sleep here would freeze it).
        Timer countdown = new Timer(2000, e -> beginRecording());
        countdown.setRepeats(false);
        countdown.start();
    }

    private void beginRecording() {
        boolean started = recorder.start(new MacroRecorder.Listener() {
            @Override
            public void onProgress(int stepCount) {
                controlBar.setStatusText(I18n.t("rec.recording", stepCount));
            }

            @Override
            public void onFinished(List<MacroStep> steps, boolean hitLimit) {
                onRecordingFinished(steps, hitLimit);
            }
        });
        recordBtn.setEnabled(true);
        if (!started) {
            controlBar.setStatusText(I18n.t("rec.noHook"));
            return;
        }
        recordBtn.setText(I18n.t("macro.recordStop"));
        controlBar.setStatusText(I18n.t("rec.recording", 0));
    }

    /** Appends the recording to the existing sequence, on the EDT. */
    private void onRecordingFinished(List<MacroStep> steps, boolean hitLimit) {
        resetRecordButtonLabel();
        if (steps.isEmpty()) {
            controlBar.setStatusText(I18n.t("rec.empty"));
            return;
        }
        // Appending rather than replacing: a recording is usually a piece the
        // user wants to add to what they already built, and an accidental
        // recording that wiped an existing sequence would be unrecoverable
        // (there is no undo).
        for (MacroStep step : steps) {
            listModel.addElement(step);
        }
        syncStepsIntoProject();
        stepList.setSelectedIndex(listModel.size() - 1);
        stepList.ensureIndexIsVisible(listModel.size() - 1);
        controlBar.setStatusText(I18n.t(hitLimit ? "rec.addedLimited" : "rec.added", steps.size()));
    }

    /**
     * Restores the Record button's idle label, hotkey suffix included.
     *
     * Setting the plain string directly would silently drop the "[F9]" suffix
     * the moment a recording ends, so both reset paths go through here.
     */
    private void resetRecordButtonLabel() {
        AutomationControlBar.showHotkeyLabel(recordBtn, I18n.t("macro.record"),
                appSettings.getHotkey(AppSettings.HK_MACRO_RECORD));
    }

    /**
     * Moves the running-step highlight. Always on the EDT (the engine hands
     * this over via invokeLater), so the renderer's activeIndex needs no
     * synchronization. Repaint only - the model is untouched, so this can never
     * disturb a selection or an in-flight drag.
     */
    private void setActiveStep(int index) {
        renderer.setActiveIndex(index);
        stepList.repaint();
    }

    // ---- step list editing ----

    private void onAdd() {
        MacroStep step = ActionEditorDialog.showDialog(SwingUtilities.getWindowAncestor(this), null);
        if (step != null) {
            listModel.addElement(step);
            syncStepsIntoProject();
        }
    }

    /**
     * Edits one step. With several rows selected this edits the lead row - the
     * one the user clicked last - which is what "Edit" most plausibly means
     * after a Ctrl+Click, rather than silently picking the topmost row.
     */
    private void onEdit() {
        int idx = stepList.getLeadSelectionIndex();
        if (idx < 0 || idx >= listModel.size() || !stepList.isSelectedIndex(idx)) {
            idx = stepList.getSelectedIndex();
        }
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
        int[] indices = stepList.getSelectedIndices();
        if (indices.length == 0) {
            return;
        }
        // Bottom-up: removing top-down shifts every later index down by one and
        // deletes the wrong rows.
        for (int i = indices.length - 1; i >= 0; i--) {
            listModel.remove(indices[i]);
        }
        stepList.clearSelection();
        syncStepsIntoProject();
    }

    /** Empties the sequence, with a confirmation because there is no undo. */
    private void onClearSequence() {
        if (listModel.isEmpty()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                I18n.t("macro.confirmClear.body", listModel.size()),
                I18n.t("macro.confirmClear.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        listModel.clear();
        syncStepsIntoProject();
        controlBar.setStatusText(I18n.t("macro.cleared"));
    }

    // ---- clipboard & selection shortcuts ----

    /**
     * Internal clipboard for step copy/cut/paste.
     *
     * Deliberately not the system clipboard: copying text in another
     * application would otherwise silently discard copied steps, and copying
     * steps would clobber the user's text clipboard.
     */
    private final List<MacroStep> clipboard = new ArrayList<>();

    /**
     * Deep-copies a step by round-tripping it through its own JSON mapping.
     *
     * Steps are mutable and shared with {@code project.getSteps()}, so pasting
     * the same instance twice would make editing one row silently change the
     * other. Reusing toMap/fromMap avoids writing (and maintaining) a separate
     * clone method, and that path is already exercised by export/import.
     */
    private static MacroStep copyOf(MacroStep step) {
        return MacroStep.fromMap(step.toMap());
    }

    private void onCopy(boolean cut) {
        int[] indices = stepList.getSelectedIndices();
        if (indices.length == 0) {
            return;
        }
        clipboard.clear();
        for (int index : indices) {
            clipboard.add(copyOf(listModel.get(index)));
        }
        if (cut) {
            onRemove();
        }
        controlBar.setStatusText(I18n.t(cut ? "macro.cut" : "macro.copied", clipboard.size()));
    }

    /** Pastes below the selection, or at the end when nothing is selected. */
    private void onPaste() {
        if (clipboard.isEmpty()) {
            return;
        }
        int[] indices = stepList.getSelectedIndices();
        int insertAt = indices.length == 0 ? listModel.size() : indices[indices.length - 1] + 1;
        int first = insertAt;
        for (MacroStep step : clipboard) {
            // Copy again on paste, so pasting the same clipboard twice does not
            // insert two references to one object.
            listModel.add(insertAt++, copyOf(step));
        }
        stepList.setSelectionInterval(first, insertAt - 1);
        syncStepsIntoProject();
        controlBar.setStatusText(I18n.t("macro.pasted", clipboard.size()));
    }

    private void onDuplicate() {
        int[] indices = stepList.getSelectedIndices();
        if (indices.length == 0) {
            return;
        }
        int insertAt = indices[indices.length - 1] + 1;
        int first = insertAt;
        for (int index : indices) {
            listModel.add(insertAt++, copyOf(listModel.get(index)));
        }
        stepList.setSelectionInterval(first, insertAt - 1);
        syncStepsIntoProject();
        controlBar.setStatusText(I18n.t("macro.duplicated", indices.length));
    }

    /**
     * Standard focused-window key bindings for the step list.
     *
     * These are Swing InputMap/ActionMap bindings, NOT JNativeHook globals -
     * they fire only when this list has focus inside the app, so they cannot
     * interfere with the user's other applications.
     *
     * <h3>Why WHEN_FOCUSED and not WHEN_ANCESTOR_OF_FOCUSED_COMPONENT</h3>
     * The first version of this used the ANCESTOR map, and Ctrl+C/X/V did
     * nothing at all. Two reasons, and both had to be fixed:
     * <ol>
     *   <li>{@code JList}'s <b>own</b> WHEN_FOCUSED InputMap already maps
     *       Ctrl+C/Ctrl+X/Ctrl+V to the TransferHandler copy/cut/paste actions.
     *       WHEN_FOCUSED is consulted <em>before</em> the ancestor map, so those
     *       built-ins swallowed the keystroke and our ancestor entry was never
     *       reached. Because {@link StepListTransferHandler#getSourceActions}
     *       returns MOVE only - and {@code canImport} requires
     *       {@code support.isDrop()} - the built-in clipboard actions silently
     *       did nothing. Hence "absolutely nothing happens".</li>
     *   <li>The list has to actually hold focus. It never received it on its
     *       own, so a click now transfers focus explicitly (see the
     *       constructor's mouse listener) and the list is focusable.</li>
     * </ol>
     * Putting our entries in the list's WHEN_FOCUSED map overrides the
     * built-ins at the same priority level, which is the only way to win.
     */
    private void installKeyBindings() {
        // getMenuShortcutKeyMaskEx() is Cmd on macOS and Ctrl elsewhere. This
        // app is Windows-only (JNA/User32), so it always resolves to Ctrl - but
        // it stays here because it is the correct idiom and costs nothing.
        int menuMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        InputMap inputMap = stepList.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = stepList.getActionMap();

        bind(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask), "amp.copy", () -> onCopy(false));
        bind(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask), "amp.cut", () -> onCopy(true));
        bind(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask), "amp.paste", this::onPaste);
        bind(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_D, menuMask), "amp.duplicate", this::onDuplicate);
        bind(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_A, menuMask), "amp.selectAll", () -> {
            if (!listModel.isEmpty()) {
                stepList.setSelectionInterval(0, listModel.size() - 1);
            }
        });
        bind(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "amp.delete", this::onRemove);
        bind(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "amp.deleteBack", this::onRemove);
        bind(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "amp.deselect",
                stepList::clearSelection);

        // Save/Export/Import are tab-wide, not list-specific: a user who just
        // typed in the project-name field still expects Ctrl+S to save.
        //
        // ANCESTOR_OF_FOCUSED_COMPONENT, not IN_FOCUSED_WINDOW: both module
        // panels live in the same window, so a window-scoped binding here would
        // also fire while the Autoclicker tab is on screen and export a macro
        // the user isn't looking at.
        InputMap panelMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap panelActions = getActionMap();
        bind(panelMap, panelActions, KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask), "amp.save", this::handleSave);
        bind(panelMap, panelActions, KeyStroke.getKeyStroke(KeyEvent.VK_E, menuMask), "amp.export", this::onExport);
        bind(panelMap, panelActions, KeyStroke.getKeyStroke(KeyEvent.VK_L, menuMask), "amp.load", this::onImport);
    }

    private static void bind(InputMap inputMap, ActionMap actionMap, KeyStroke stroke, String name, Runnable action) {
        inputMap.put(stroke, name);
        actionMap.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    /**
     * Shifts the whole selection one slot up or down as a block.
     *
     * Moving each selected row independently would scramble a multi-row
     * selection (and collapse it against the list ends), so the block is
     * refused outright when it is already flush against the edge.
     */
    private void onMove(int delta) {
        int[] indices = stepList.getSelectedIndices();
        if (indices.length == 0) {
            return;
        }
        if (indices[0] + delta < 0 || indices[indices.length - 1] + delta >= listModel.size()) {
            return;
        }
        // Moving up: take rows top-down so the vacated slot is always above the
        // next row to move. Moving down: bottom-up, for the mirror reason.
        if (delta < 0) {
            for (int index : indices) {
                listModel.add(index + delta, listModel.remove(index));
            }
        } else {
            for (int i = indices.length - 1; i >= 0; i--) {
                listModel.add(indices[i] + delta, listModel.remove(indices[i]));
            }
        }
        stepList.setSelectionInterval(indices[0] + delta, indices[indices.length - 1] + delta);
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
                controlBar.setStatusText(I18n.t("macro.exported", file.getName()));
            } catch (IOException ex) {
                AppLogger.error("Gagal export project ke " + file, ex);
                JOptionPane.showMessageDialog(this, I18n.t("macro.exportFailed.body", ex.getMessage()),
                        I18n.t("macro.exportFailed.title"), JOptionPane.ERROR_MESSAGE);
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
                controlBar.setStatusText(I18n.t("macro.imported", project.getName(), project.getSteps().size()));
            } else {
                JOptionPane.showMessageDialog(this, result.errorMessage, I18n.t("macro.importFailed.title"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ---- control bar handlers ----

    private void handleStart() {
        if (engine == null) {
            JOptionPane.showMessageDialog(this, I18n.t("ac.cannotStart.body"),
                    I18n.t("ac.cannotStart.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        // The recorder listens to the OS hook, which cannot tell a Robot-generated
        // click from a real one - so a run started mid-recording would feed its own
        // replayed input straight back into the recording, growing it without end.
        if (recorder.isRecording()) {
            controlBar.setStatusText(I18n.t("rec.busyRecording"));
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

    /**
     * Unified control: idle starts a run, running pauses it, paused resumes.
     *
     * Note isRunning() stays true briefly after stop() returns (the flag is
     * cleared by the worker thread's finally block), so a Toggle pressed in
     * that window pauses a dying run rather than starting a new one - harmless,
     * and preferable to racing a second worker into existence.
     */
    private void handleToggle() {
        if (engine == null) {
            return;
        }
        if (!engine.isRunning()) {
            handleStart();
            return;
        }
        engine.togglePause();
        controlBar.setStatusText(I18n.t(engine.isPaused() ? "status.paused" : "status.running"));
    }

    /** Per spec, "Save Settings" here persists loop-mode preference + hotkeys; the
     *  step sequence itself is saved/loaded explicitly via Export/Import Project. */
    private void handleSave() {
        appSettings.setLastMacroLoopMode(loopInfinite.isSelected() ? LoopMode.INFINITE : LoopMode.ONCE);
        boolean ok = SettingsManager.save(appSettings);
        controlBar.setStatusText(I18n.t(ok ? "macro.savedLoop" : "status.saveFailed"));
    }

    private void handleReset() {
        loopOnce.setSelected(true);
        loopInfinite.setSelected(false);
        controlBar.setStatusText(I18n.t("macro.resetLoop"));
    }

    private void handleConfigureHotkeys() {
        String[] ids = {AppSettings.HK_MACRO_START, AppSettings.HK_MACRO_STOP,
                AppSettings.HK_MACRO_TOGGLE, AppSettings.HK_MACRO_RECORD};
        String[] labels = {I18n.t("control.start"), I18n.t("control.stop"),
                I18n.t("hotkey.toggleLabel"), I18n.t("hotkey.record")};
        HotkeyCaptureDialog dlg = new HotkeyCaptureDialog(
                SwingUtilities.getWindowAncestor(this), I18n.t("hotkey.dialogTitle", I18n.t("tab.macro")),
                appSettings, ids, labels);
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
        controlBar.setStatusText(I18n.t(enabled ? "status.failsafeOn" : "status.failsafeOff"));
    }

    public void registerHotkeys() {
        GlobalHotkeyManager hk = GlobalHotkeyManager.getInstance();
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_MACRO_START), () -> controlBar.getStartButton().doClick());
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_MACRO_STOP), () -> controlBar.getStopButton().doClick());
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_MACRO_TOGGLE), () -> controlBar.getToggleButton().doClick());
        // doClick() inherits the button's disabled state, so a Record hotkey
        // pressed during the pre-roll countdown correctly does nothing.
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_MACRO_RECORD), recordBtn::doClick);
        // Show each binding on its button ("Start [F3]"), refreshed here so a
        // rebind is reflected the moment the hotkey dialog closes.
        controlBar.showHotkeyLabels(
                appSettings.getHotkey(AppSettings.HK_MACRO_START),
                appSettings.getHotkey(AppSettings.HK_MACRO_STOP),
                appSettings.getHotkey(AppSettings.HK_MACRO_TOGGLE));
        // Record lives outside the control bar but gets the same treatment.
        // Guarded because a rebind can land while a recording is in progress,
        // and the button label is "Stop Recording" at that moment.
        if (!recorder.isRecording()) {
            AutomationControlBar.showHotkeyLabel(recordBtn, I18n.t("macro.record"),
                    appSettings.getHotkey(AppSettings.HK_MACRO_RECORD));
        }
    }

    private String describeFinish(long steps, int loops, MacroEngine.StopReason reason) {
        String why;
        switch (reason) {
            case COMPLETED_ONCE: why = I18n.t("macro.stop.once"); break;
            case FAILSAFE: why = I18n.t("ac.stop.failsafe"); break;
            case ERROR: why = I18n.t("ac.stop.error"); break;
            case EMPTY_PROJECT: why = I18n.t("macro.stop.empty"); break;
            default: why = I18n.t("ac.stop.manual");
        }
        return I18n.t("macro.finished", steps, loops, why);
    }
}
