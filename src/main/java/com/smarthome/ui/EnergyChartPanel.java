package com.smarthome.ui;

import com.smarthome.model.EnergyBucket;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JPanel;

final class EnergyChartPanel extends JPanel {
    private List<EnergyBucket> buckets = List.of();
    private boolean historyAvailable;

    EnergyChartPanel() {
        setBackground(UiStyle.SURFACE);
        setPreferredSize(new java.awt.Dimension(700, 280));
        setMinimumSize(new java.awt.Dimension(320, 220));
    }

    void setBuckets(List<EnergyBucket> values, boolean available) {
        buckets = List.copyOf(values);
        historyAvailable = available;
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int left = 62;
        int right = getWidth() - 20;
        int top = 24;
        int bottom = getHeight() - 54;
        if (!historyAvailable || buckets.isEmpty()) {
            g.setColor(UiStyle.MUTED);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            String message = historyAvailable ? "No usage sessions in this date range."
                : "No tracked history is available for the selected date range.";
            int x = Math.max(12, (getWidth() - g.getFontMetrics().stringWidth(message)) / 2);
            g.drawString(message, x, getHeight() / 2);
            g.dispose();
            return;
        }

        BigDecimal max = buckets.stream().map(EnergyBucket::kilowattHours)
            .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        double maxValue = max.doubleValue();
        if (maxValue <= 0) maxValue = 1.0;
        int plotWidth = Math.max(1, right - left);
        int plotHeight = Math.max(1, bottom - top);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(UiStyle.MUTED);
        for (int line = 0; line <= 4; line++) {
            int y = bottom - line * plotHeight / 4;
            double value = maxValue * line / 4.0;
            g.setColor(UiStyle.BORDER);
            g.setStroke(new BasicStroke(1f));
            g.drawLine(left, y, right, y);
            g.setColor(UiStyle.MUTED);
            g.drawString(String.format("%.2f", value), 8, y + 4);
        }

        int count = buckets.size();
        double slotWidth = plotWidth / (double) count;
        int barWidth = Math.max(2, (int) Math.min(30, slotWidth * 0.58));
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        for (int index = 0; index < count; index++) {
            EnergyBucket bucket = buckets.get(index);
            int height = (int) Math.round(bucket.kilowattHours().doubleValue() / maxValue * (plotHeight - 2));
            int x = (int) Math.round(left + index * slotWidth + (slotWidth - barWidth) / 2);
            int y = bottom - height;
            g.setColor(new Color(220, 238, 233));
            g.fillRoundRect(x, y, barWidth, Math.max(2, height), 6, 6);
            g.setColor(UiStyle.BLUE);
            g.fillRoundRect(x, y, barWidth, Math.max(2, Math.min(height, 5)), 6, 6);
            if (count <= 14 || index % Math.max(1, count / 10) == 0) {
                g.setColor(UiStyle.MUTED);
                String label = bucket.label().replace("Week of ", "");
                if (label.length() > 10) label = label.substring(5);
                int labelX = (int) (left + index * slotWidth + (slotWidth - g.getFontMetrics().stringWidth(label)) / 2);
                g.drawString(label, labelX, bottom + 17);
            }
        }
        g.setColor(UiStyle.TEXT);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        AffineTransform transform = g.getTransform();
        g.rotate(-Math.PI / 2);
        g.drawString("Estimated energy (kWh)", -top - 4, 15);
        g.setTransform(transform);
        g.dispose();
    }
}
