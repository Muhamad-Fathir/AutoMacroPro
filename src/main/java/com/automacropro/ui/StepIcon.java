package com.automacropro.ui;

import com.automacropro.model.ActionType;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Tiny vector icon per {@link ActionType} for the sequencer list.
 *
 * Drawn with Java2D rather than using Unicode glyphs (⌨ / 🖱 / ⏱) on purpose:
 * those code points are missing from the default logical fonts on plenty of
 * Windows installs and render as tofu boxes, and the app deliberately falls
 * back to SansSerif when the bundled .ttf files are absent. Shapes always
 * render, scale with the row, and can use the accent palette directly.
 */
final class StepIcon implements Icon {

    private static final int SIZE = 16;

    private final ActionType type;

    StepIcon(ActionType type) {
        this.type = type;
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(x, y);
        switch (type) {
            case MOUSE:
                // Mouse body with a split top and a scroll wheel.
                g2.setColor(UiTheme.ACCENT);
                g2.drawRoundRect(3, 1, 10, 14, 8, 8);
                g2.drawLine(8, 2, 8, 6);
                g2.fillRect(7, 3, 3, 4);
                break;
            case KEYBOARD:
                // Key slab with three key marks and a spacebar.
                g2.setColor(UiTheme.INCH_WORM.brighter());
                g2.drawRoundRect(1, 4, 14, 9, 3, 3);
                g2.fillRect(3, 6, 2, 2);
                g2.fillRect(7, 6, 2, 2);
                g2.fillRect(11, 6, 2, 2);
                g2.fillRect(4, 10, 8, 2);
                break;
            case SCROLL:
                // Up/down chevrons: a mouse-wheel scroll.
                g2.setColor(UiTheme.ACCENT);
                g2.drawLine(4, 5, 8, 2);
                g2.drawLine(8, 2, 12, 5);
                g2.drawLine(4, 11, 8, 14);
                g2.drawLine(8, 14, 12, 11);
                break;
            case DELAY:
            default:
                // Clock face with hands.
                g2.setColor(UiTheme.MUTED_TEXT.brighter());
                g2.drawOval(1, 2, 13, 13);
                g2.drawLine(8, 5, 8, 9);
                g2.drawLine(8, 9, 11, 10);
                break;
        }
        g2.dispose();
    }
}
