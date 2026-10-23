package com.smarthome.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class StatisticCard extends RoundedPanel {
    private final JLabel value = new JLabel("—");

    StatisticCard(String caption, Color accent) {
        super(17, 18, 17, 18);
        setLayout(new BorderLayout(0, 10));
        JLabel label = new JLabel(caption.toUpperCase());
        label.setForeground(UiStyle.MUTED);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        value.setForeground(accent);
        value.setFont(new Font("SansSerif", Font.BOLD, 28));
        add(label, BorderLayout.NORTH);
        add(value, BorderLayout.CENTER);
    }

    void setValue(String text) {
        value.setText(text);
    }
}
