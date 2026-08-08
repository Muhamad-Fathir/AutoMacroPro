package com.automacropro;

import com.automacropro.persistence.SettingsManager;
import com.automacropro.ui.MainFrame;
import com.automacropro.ui.UiTheme;
import com.automacropro.util.AppLogger;
import com.automacropro.util.I18n;

import javax.swing.SwingUtilities;
import java.util.Locale;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // Lightweight error logging: catch anything that slips through an
        // event handler uncaught instead of letting it vanish silently.
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) ->
                AppLogger.error("Uncaught exception pada thread " + thread.getName(), ex));

        // The Look&Feel must be installed before the first component exists,
        // otherwise components built earlier keep the default UI delegates.
        // Same for the language: every label reads its text at construction.
        SwingUtilities.invokeLater(() -> {
            UiTheme.installLookAndFeel();
            I18n.setLocale(Locale.forLanguageTag(SettingsManager.load().getLanguageTag()));
            new MainFrame().setVisible(true);
        });
    }
}
