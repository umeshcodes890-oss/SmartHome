package com.smarthome.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

final class UiStyle {
    static final Color NAVY = new Color(24, 43, 49);
    static final Color NAVY_LIGHT = new Color(40, 67, 72);
    static final Color BLUE = new Color(25, 132, 125);
    static final Color BLUE_HOVER = new Color(19, 112, 107);
    static final Color BLUE_PALE = new Color(226, 242, 239);
    static final Color GREEN = new Color(35, 129, 91);
    static final Color GREEN_PALE = new Color(229, 243, 235);
    static final Color TEXT = new Color(37, 51, 54);
    static final Color MUTED = new Color(105, 122, 123);
    static final Color WORKSPACE = new Color(244, 247, 245);
    static final Color SURFACE = new Color(255, 255, 255);
    static final Color TABLE_HEADER = new Color(239, 245, 242);
    static final Color ROW_ALT = new Color(249, 251, 250);
    static final Color BORDER = new Color(221, 231, 226);

    private UiStyle() {}

    static JPanel card() {
        return new RoundedPanel(18, 20, 18, 20);
    }

    static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setForeground(primary ? Color.WHITE : TEXT);
        button.setBackground(primary ? BLUE : SURFACE);
        button.setOpaque(true);
        button.setBorderPainted(true);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(primary ? BLUE : BORDER),
            BorderFactory.createEmptyBorder(9, 14, 9, 14)));
        return button;
    }
}
