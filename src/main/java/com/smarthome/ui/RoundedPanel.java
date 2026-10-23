package com.smarthome.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

class RoundedPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    RoundedPanel(int top, int left, int bottom, int right) {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(UiStyle.SURFACE);
        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        g.setColor(UiStyle.BORDER);
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        g.dispose();
        super.paintComponent(graphics);
    }
}
