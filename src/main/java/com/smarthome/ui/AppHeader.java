package com.smarthome.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;

final class AppHeader extends JPanel {
    private final JLabel title = new JLabel("Dashboard");
    private final JLabel profile = new JLabel();

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
        profile.setForeground(UiStyle.MUTED);
        profile.setFont(new Font("SansSerif", Font.BOLD, 12));
        JButton about = UiStyle.button("About", false);
        about.addActionListener(event -> JOptionPane.showMessageDialog(this,
            "Smart Living\nSmart Home Management System\n\nJava Swing · JDBC · MySQL\n" + profile.getText().trim(),
            "About Smart Living", JOptionPane.INFORMATION_MESSAGE));
        right.add(refresh);
        right.add(notifications);
        right.add(profile);
        right.add(about);
        add(right, BorderLayout.EAST);
    }

    void setTitle(String pageTitle) {
        title.setText(pageTitle);
    }

    void setProfileName(String displayName) {
        profile.setText("  " + displayName + "  ");
    }
}
