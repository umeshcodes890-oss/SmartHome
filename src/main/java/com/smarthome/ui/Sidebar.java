package com.smarthome.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.plaf.basic.BasicButtonUI;

final class Sidebar extends JPanel {
    private final Map<String, JButton> navigation = new LinkedHashMap<>();
    private final Consumer<String> onNavigate;
    private boolean adminMode;

    Sidebar(Consumer<String> onNavigate) {
        this.onNavigate = onNavigate;
        setLayout(new BorderLayout(0, 28));
        setBackground(UiStyle.NAVY);
        setBorder(BorderFactory.createEmptyBorder(26, 16, 20, 16));
        setPreferredSize(new Dimension(232, 0));

        JPanel brand = new JPanel(new BorderLayout(0, 5));
        brand.setOpaque(false);
        JLabel mark = new JLabel("HOME /");
        mark.setForeground(new Color(127, 211, 194));
        mark.setFont(new Font("SansSerif", Font.BOLD, 11));
        JLabel title = new JLabel("Smart Living");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        JLabel subtitle = new JLabel("CONTROL CENTER");
        subtitle.setForeground(new Color(161, 184, 181));
        subtitle.setFont(new Font("SansSerif", Font.BOLD, 9));
        JPanel brandText = new JPanel(new GridLayout(0, 1, 0, 4));
        brandText.setOpaque(false);
        brandText.add(mark);
        brandText.add(title);
        brandText.add(subtitle);
        brand.add(brandText, BorderLayout.CENTER);
        add(brand, BorderLayout.NORTH);

        JPanel links = new JPanel(new GridLayout(0, 1, 0, 7));
        links.setOpaque(false);
        String[] pages = {"Dashboard", "Devices", "Rooms", "Energy Usage", "Sensor Monitoring", "Schedules",
            "Automation Rules", "Activity Logs", "User Management", "Settings"};
        for (int index = 0; index < pages.length; index++) {
            String page = pages[index];
            JButton button = new JButton(page);
            button.setUI(new BasicButtonUI());
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            button.setHorizontalAlignment(JButton.LEFT);
            button.setFont(new Font("SansSerif", Font.PLAIN, 13));
            button.setForeground(new Color(210, 220, 233));
            button.setBackground(UiStyle.NAVY);
            button.setFocusPainted(true);
            button.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 10));
            String shortcut = index < 9 ? Integer.toString(index + 1) : "0";
            button.setToolTipText("Open " + page + " (Alt+" + shortcut + ")");
            button.getAccessibleContext().setAccessibleName(page);
            button.addActionListener(event -> onNavigate.accept(page));
            navigation.put(page, button);
            links.add(button);
        }
        add(links, BorderLayout.CENTER);

        JLabel footer = new JLabel("SMART HOME  •  LOCAL");
        footer.setForeground(new Color(145, 171, 166));
        footer.setFont(new Font("SansSerif", Font.BOLD, 9));
        add(footer, BorderLayout.SOUTH);
        select("Dashboard");
    }

    void setAdminMode(boolean admin) {
        adminMode = admin;
        navigation.get("Schedules").setVisible(admin);
        navigation.get("Automation Rules").setVisible(admin);
        navigation.get("Activity Logs").setVisible(admin);
        navigation.get("User Management").setVisible(admin);
        revalidate();
    }

    void select(String page) {
        navigation.forEach((name, button) -> {
            boolean selected = name.equals(page);
            button.setBackground(selected ? UiStyle.NAVY_LIGHT : UiStyle.NAVY);
            button.setForeground(selected ? Color.WHITE : new Color(210, 220, 233));
            button.setFont(new Font("SansSerif", selected ? Font.BOLD : Font.PLAIN, 13));
            button.setBorder(selected
                ? BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, UiStyle.BLUE),
                    BorderFactory.createEmptyBorder(12, 13, 12, 10))
                : BorderFactory.createEmptyBorder(12, 16, 12, 10));
        });
    }

    void refreshTheme(String selectedPage) {
        setBackground(UiStyle.NAVY);
        select(selectedPage);
        repaint();
    }
}
