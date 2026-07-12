package com.automacropro.ui;

import com.automacropro.hotkey.GlobalHotkeyManager;
import com.automacropro.model.AppSettings;
import com.automacropro.persistence.SettingsManager;
import com.automacropro.util.AppLogger;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Top-level window: one tab per module. Owns the application-wide lifecycle
 * of the JNativeHook global hook (initialized here once at startup,
 * unregistered here once on window close) so neither module panel has to
 * worry about it.
 */
public class MainFrame extends JFrame {

    /**
     * Build marker for verifying which copy of the source is actually running.
     * Shows up in the window title AND in automacropro.log at startup. If you
     * rebuild and this marker does NOT change, NetBeans/Maven is not compiling
     * the file you think it is - see the packaging notes in README.md.
     */
    private static final String BUILD_MARKER = "1.0 Release";

    public MainFrame() {
        super("AutoMacro Pro - Workflow Automation & Auto-Clicker  [" + BUILD_MARKER + "]");
        AppLogger.info("=== MainFrame() start - " + BUILD_MARKER + " ===");
        UiTheme.installLookAndFeel();

        AppSettings appSettings = SettingsManager.load();
        GlobalHotkeyManager.getInstance().initialize();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Autoclicker", new AutoClickerPanel(appSettings));
        tabs.addTab("Macro Sequencer", new MacroSequencerPanel(appSettings));
        setContentPane(tabs);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                GlobalHotkeyManager.getInstance().shutdown();
                dispose();
                System.exit(0);
            }
        });

        setMinimumSize(new Dimension(760, 600));
        setSize(860, 680);
        setLocationRelativeTo(null);

        // --- Mixed-DPI multi-monitor workaround -----------------------------------
        // Reported symptom: on a setup with different per-monitor scaling (e.g. 100%
        // laptop + 125% external display), some WindowsLookAndFeel component chrome
        // (radio button circles, button faces) can fail to paint even though layout
        // geometry (bounds/colors/opaque/showing) all compute correctly - because that
        // chrome comes from icon resources cached per-DPI-scale-factor, and the cache
        // can end up stale for whichever monitor/scale the window actually ends up on.
        // updateComponentTreeUI() forces every component to re-fetch its UI delegate
        // (and the resources that come with it) right now, on the monitor the window
        // is actually showing on - the standard remedy for stale/incorrect L&F-derived
        // visuals. We force this once right after the window opens, and again any time
        // the window's GraphicsConfiguration changes (i.e. it moved to a different
        // monitor), since that is exactly when a scale-factor mismatch would occur.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    AppLogger.info("DIAG windowOpened - forcing updateComponentTreeUI (mixed-DPI workaround)");
                    SwingUtilities.updateComponentTreeUI(MainFrame.this);
                    revalidate();
                    repaint();
                });
            }
        });
        addComponentListener(new ComponentAdapter() {
            private GraphicsConfiguration lastGc = getGraphicsConfiguration();

            @Override
            public void componentMoved(ComponentEvent e) {
                checkForDpiChange();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                checkForDpiChange();
            }

            private void checkForDpiChange() {
                GraphicsConfiguration gc = getGraphicsConfiguration();
                if (gc != null && gc != lastGc) {
                    lastGc = gc;
                    AppLogger.info("DIAG GraphicsConfiguration changed (monitor/DPI change detected) - "
                            + "forcing updateComponentTreeUI");
                    SwingUtilities.invokeLater(() -> {
                        SwingUtilities.updateComponentTreeUI(MainFrame.this);
                        revalidate();
                        repaint();
                    });
                }
            }
        });
    }
}
