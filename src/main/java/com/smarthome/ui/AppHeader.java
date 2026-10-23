package com.smarthome.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class AppHeader extends JPanel {
    private final JLabel title = new JLabel("Dashboard");

    AppHeader(ActionListener onRefresh, NotificationPanel notifications) {
        setLayout(new BorderLayout());
        setBackground(UiStyle.SURFACE);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UiStyle.BORDER),
            BorderFactory.createEmptyBorder(16, 28, 16, 28)));
        title.setForeground(UiStyle.TEXT);
        title.setFont(new Font("SansSerif", Font.BOLD, 21));
        add(title, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.setOpaque(false);
        JButton refresh = UiStyle.button("Refresh", false);
        refresh.addActionListener(onRefresh);
        JLabel profile = new JLabel("  Home Admin  ");
        profile.setForeground(UiStyle.MUTED);
        profile.setFont(new Font("SansSerif", Font.BOLD, 12));
        right.add(refresh);
        right.add(notifications);
        right.add(profile);
        add(right, BorderLayout.EAST);
    }

    void setTitle(String pageTitle) {
        title.setText(pageTitle);
    }
}
