package com.automacropro.ui;

import com.automacropro.engine.WindowManager;
import com.automacropro.hotkey.GlobalHotkeyManager;
import com.automacropro.model.AppSettings;
import com.automacropro.util.AppLogger;
import com.automacropro.util.I18n;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Module 3: Window Manager - makes another application's window borderless and
 * fits it to the monitor, then restores it.
 *
 * All Win32 work runs in {@link SwingWorker}s. This is not defensive
 * boilerplate: {@code SetWindowPos} synchronously messages the target window,
 * so pointing this at a hung game would freeze our own UI if it ran on the EDT
 * - the same no-blocking-the-EDT rule the engines follow.
 */
public class WindowManagerPanel extends JPanel {

    private final DefaultListModel<WindowManager.WindowInfo> model = new DefaultListModel<>();
    private final JList<WindowManager.WindowInfo> windowList = new JList<>(model);
    private final JLabel statusLabel = new JLabel(I18n.t("control.status", I18n.t("status.idle")));

    private final JButton refreshBtn = UiTheme.createNeutralButton(I18n.t("wm.refresh"));
    private final JButton borderlessBtn = UiTheme.createButton(I18n.t("wm.borderless"), UiTheme.START_BG, UiTheme.START_FG);
    private final JButton restoreBtn = UiTheme.createButton(I18n.t("wm.restore"), UiTheme.TOGGLE_BG, UiTheme.TOGGLE_FG);

    private final AppSettings appSettings;

    public WindowManagerPanel(AppSettings appSettings) {
        this.appSettings = appSettings;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GlassPanel listCard = new GlassPanel(new BorderLayout(0, 6), I18n.t("wm.group.windows"));
        windowList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        windowList.setFont(UiTheme.FONT_BODY);
        JScrollPane scroll = new JScrollPane(windowList);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.GLASS_BORDER));
        listCard.add(scroll, BorderLayout.CENTER);

        GlassPanel actions = new GlassPanel(new FlowLayout(FlowLayout.LEFT, 10, 4), I18n.t("wm.group.actions"));
        actions.add(refreshBtn);
        actions.add(borderlessBtn);
        actions.add(restoreBtn);

        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(actions, BorderLayout.NORTH);
        statusLabel.setFont(UiTheme.FONT_BODY.deriveFont(Font.ITALIC));
        statusLabel.setForeground(UiTheme.MUTED_TEXT);
        south.add(statusLabel, BorderLayout.SOUTH);

        add(listCard, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        UiTheme.deopaque(this);

        refreshBtn.addActionListener(e -> refresh());
        borderlessBtn.addActionListener(e -> run(true));
        restoreBtn.addActionListener(e -> run(false));
        windowList.addListSelectionListener(e -> updateButtons());

        if (!WindowManager.isSupported()) {
            refreshBtn.setEnabled(false);
            borderlessBtn.setEnabled(false);
            restoreBtn.setEnabled(false);
            statusLabel.setText(I18n.t("control.status", I18n.t("wm.unsupported")));
        } else {
            refresh();
        }
        registerHotkeys();
    }

    /**
     * Binds this module's global hotkeys.
     *
     * Routed through {@code doClick()} rather than calling the handlers
     * directly, so each action inherits its button's enabled-state guard for
     * free - "Make Borderless" with nothing selected, or any action on a
     * non-Windows platform, correctly does nothing.
     */
    public void registerHotkeys() {
        GlobalHotkeyManager hk = GlobalHotkeyManager.getInstance();
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_WM_REFRESH), refreshBtn::doClick);
        hk.setBinding(appSettings.getHotkey(AppSettings.HK_WM_BORDERLESS), borderlessBtn::doClick);
    }

    private void updateButtons() {
        WindowManager.WindowInfo selected = windowList.getSelectedValue();
        borderlessBtn.setEnabled(selected != null && !selected.isBorderless());
        restoreBtn.setEnabled(selected != null && selected.isBorderless());
    }

    private void refresh() {
        refreshBtn.setEnabled(false);
        statusLabel.setText(I18n.t("control.status", I18n.t("wm.scanning")));
        new SwingWorker<List<WindowManager.WindowInfo>, Void>() {
            @Override
            protected List<WindowManager.WindowInfo> doInBackground() {
                return WindowManager.listWindows();
            }

            @Override
            protected void done() {
                refreshBtn.setEnabled(true);
                try {
                    List<WindowManager.WindowInfo> windows = get();
                    model.clear();
                    windows.forEach(model::addElement);
                    statusLabel.setText(I18n.t("control.status", I18n.t("wm.found", windows.size())));
                } catch (InterruptedException | ExecutionException ex) {
                    AppLogger.error("Failed to enumerate windows", ex);
                    statusLabel.setText(I18n.t("control.status", I18n.t("wm.listFailed")));
                }
                updateButtons();
            }
        }.execute();
    }

    private void run(boolean borderless) {
        WindowManager.WindowInfo target = windowList.getSelectedValue();
        if (target == null) {
            return;
        }
        borderlessBtn.setEnabled(false);
        restoreBtn.setEnabled(false);
        statusLabel.setText(I18n.t("control.status", I18n.t("wm.applying", target.title)));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (borderless) {
                    WindowManager.makeBorderless(target);
                } else {
                    WindowManager.restore(target);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    statusLabel.setText(I18n.t("control.status",
                            I18n.t(borderless ? "wm.appliedBorderless" : "wm.appliedRestore", target.title)));
                    windowList.repaint(); // the [borderless] marker in toString() changed
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    AppLogger.error("Window Manager action failed on " + target.title, cause);
                    statusLabel.setText(I18n.t("control.status",
                            cause instanceof WindowManager.WindowManagerException
                                    ? cause.getMessage() : I18n.t("wm.actionFailed")));
                }
                updateButtons();
            }
        }.execute();
    }
}
