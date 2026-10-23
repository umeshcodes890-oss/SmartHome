package com.smarthome.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;
import javax.swing.Icon;

final class DeviceTypeIcon implements Icon {
    private final String type;

    DeviceTypeIcon(String type) {
        this.type = type == null ? "" : type;
    }

    @Override public int getIconWidth() { return 34; }
    @Override public int getIconHeight() { return 34; }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(background(type));
        g.fillRoundRect(x, y, 32, 32, 10, 10);
        g.setColor(foreground(type));
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        String mark = symbol(type);
        int textX = x + (32 - g.getFontMetrics().stringWidth(mark)) / 2;
        int textY = y + (32 - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
        g.drawString(mark, textX, textY);
        g.dispose();
    }

    private static String symbol(String type) {
        String value = type.toLowerCase(Locale.ROOT);
        if (value.contains("light") || value.contains("lamp")) return "L";
        if (value.contains("climate") || value.contains("air")) return "AC";
        if (value.contains("tv") || value.contains("entertain")) return "TV";
        if (value.contains("lock")) return "LK";
        if (value.contains("camera")) return "CAM";
        String trimmed = type.trim();
        return trimmed.isEmpty() ? "?" : trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static Color background(String type) {
        String value = type.toLowerCase(Locale.ROOT);
        if (value.contains("light") || value.contains("lamp")) return new Color(250, 241, 218);
        if (value.contains("climate") || value.contains("air")) return new Color(224, 239, 240);
        if (value.contains("tv") || value.contains("entertain")) return new Color(239, 232, 245);
        if (value.contains("lock") || value.contains("camera")) return UiStyle.GREEN_PALE;
        return UiStyle.BLUE_PALE;
    }

    private static Color foreground(String type) {
        String value = type.toLowerCase(Locale.ROOT);
        if (value.contains("light") || value.contains("lamp")) return new Color(143, 105, 29);
        if (value.contains("climate") || value.contains("air")) return new Color(45, 109, 120);
        if (value.contains("tv") || value.contains("entertain")) return new Color(111, 83, 129);
        if (value.contains("lock") || value.contains("camera")) return UiStyle.GREEN;
        return UiStyle.BLUE;
    }
}
