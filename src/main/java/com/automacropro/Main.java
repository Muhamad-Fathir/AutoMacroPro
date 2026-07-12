package com.automacropro;

import com.automacropro.ui.MainFrame;
import com.automacropro.util.AppLogger;

import javax.swing.SwingUtilities;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // Lightweight error logging: catch anything that slips through an
        // event handler uncaught instead of letting it vanish silently.
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) ->
                AppLogger.error("Uncaught exception pada thread " + thread.getName(), ex));

        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
