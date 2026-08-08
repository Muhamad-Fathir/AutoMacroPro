package com.automacropro.ui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/**
 * The one glassmorphism primitive: a non-opaque rounded panel that
 * alpha-composites {@link UiTheme#GLASS_FILL} over whatever is behind it and
 * strokes a {@link UiTheme#GLASS_BORDER} outline.
 *
 * Wrap any existing settings group in one of these and call
 * {@link UiTheme#deopaque(java.awt.Component)} on it - Metal's child panels
 * and labels are opaque by default and would otherwise paint the flat
 * background straight over the glass.
 */
public class GlassPanel extends JPanel {

    public GlassPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
    }

    public GlassPanel(LayoutManager layout, String title) {
        this(layout);
        setBorder(UiTheme.titled(title));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int r = UiTheme.glassRadius();
        g2.setColor(UiTheme.GLASS_FILL);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
        g2.setColor(UiTheme.GLASS_BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
        g2.dispose();
        super.paintComponent(g);
    }
}
