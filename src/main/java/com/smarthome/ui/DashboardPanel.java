package com.smarthome.ui;

import com.smarthome.model.Device;
import com.smarthome.model.RoomSummary;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

final class DashboardPanel extends JPanel {
    private final StatisticCard totalCard = new StatisticCard("Total devices", UiStyle.TEXT);
    private final StatisticCard activeCard = new StatisticCard("Currently on", UiStyle.GREEN);
    private final StatisticCard inactiveCard = new StatisticCard("Currently off", UiStyle.MUTED);
    private final StatisticCard powerCard = new StatisticCard("Active power", UiStyle.BLUE);
    private final JPanel quickControls = new JPanel();
    private final JPanel rooms = new JPanel();
    private final JPanel activity = new JPanel();

    DashboardPanel(Consumer<Device> onToggle) {
        setLayout(new BorderLayout(0, 18));
        setBackground(UiStyle.WORKSPACE);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setOpaque(false);

        JPanel intro = new JPanel(new BorderLayout(0, 5));
        intro.setOpaque(false);
        JLabel headline = new JLabel("Good to see you");
        headline.setFont(new Font("SansSerif", Font.BOLD, 19));
        headline.setForeground(UiStyle.TEXT);
        JLabel subtitle = new JLabel("Here is what is happening across your home.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(UiStyle.MUTED);
        intro.add(headline, BorderLayout.NORTH);
        intro.add(subtitle, BorderLayout.SOUTH);

        JPanel stats = new JPanel(new java.awt.GridLayout(1, 4, 14, 0));
        stats.setOpaque(false);
        stats.add(totalCard);
        stats.add(activeCard);
        stats.add(inactiveCard);
        stats.add(powerCard);

        JPanel summary = new JPanel(new BorderLayout(0, 16));
        summary.setOpaque(false);
        summary.add(intro, BorderLayout.NORTH);
        summary.add(stats, BorderLayout.CENTER);
        content.add(summary, BorderLayout.NORTH);

        JPanel lower = new JPanel(new java.awt.GridLayout(1, 2, 16, 0));
        lower.setOpaque(false);
        lower.add(section("Quick device controls", quickControls));
        lower.add(section("Room overview", rooms));

        JPanel activityCard = UiStyle.card();
        activityCard.setLayout(new BorderLayout(0, 10));
        JLabel activityTitle = new JLabel("Recent activity");
        activityTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        activityTitle.setForeground(UiStyle.TEXT);
        activity.setLayout(new BorderLayout());
        activity.setOpaque(false);
        activityCard.add(activityTitle, BorderLayout.NORTH);
        activityCard.add(activity, BorderLayout.CENTER);

        JPanel vertical = new JPanel(new BorderLayout(0, 16));
        vertical.setOpaque(false);
        vertical.add(lower, BorderLayout.NORTH);
        vertical.add(activityCard, BorderLayout.CENTER);
        content.add(vertical, BorderLayout.CENTER);
        add(new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        ((JScrollPane) getComponent(0)).setBorder(null);
        ((JScrollPane) getComponent(0)).getViewport().setOpaque(false);
        ((JScrollPane) getComponent(0)).setOpaque(false);
    }

    void setData(List<Device> devices, List<RoomSummary> roomSummaries, List<String> recentActivity,
            Consumer<Device> onToggle) {
        int active = 0;
        BigDecimal activePower = BigDecimal.ZERO;
        for (Device device : devices) {
            if ("ON".equals(device.getStatus())) {
                active++;
                if (device.getPowerWatts() != null) activePower = activePower.add(device.getPowerWatts());
            }
        }
        totalCard.setValue(Integer.toString(devices.size()));
        activeCard.setValue(Integer.toString(active));
        inactiveCard.setValue(Integer.toString(devices.size() - active));
        powerCard.setValue(activePower.setScale(1, java.math.RoundingMode.HALF_UP).toPlainString() + " W");

        quickControls.removeAll();
        quickControls.setLayout(new java.awt.GridLayout(0, 1, 0, 7));
        if (devices.isEmpty()) {
            quickControls.add(RoomsPanel.message("No devices found. Add a device to get started."));
        } else {
            for (Device device : devices) {
                JPanel row = new JPanel(new BorderLayout(12, 0));
                row.setOpaque(false);
                JPanel labels = new JPanel(new BorderLayout(0, 4));
                labels.setOpaque(false);
                JLabel name = new JLabel(device.getName());
                name.setFont(new Font("SansSerif", Font.BOLD, 12));
                name.setForeground(UiStyle.TEXT);
                JLabel room = new JLabel(device.getRoom() + "  ·  " + device.getType());
                room.setFont(new Font("SansSerif", Font.PLAIN, 10));
                room.setForeground(UiStyle.MUTED);
                labels.add(name, BorderLayout.NORTH);
                labels.add(room, BorderLayout.SOUTH);
                JButton state = UiStyle.button(device.getStatus(), "ON".equals(device.getStatus()));
                state.setForeground("ON".equals(device.getStatus()) ? UiStyle.GREEN : UiStyle.MUTED);
                state.setText("ON".equals(device.getStatus()) ? "Turn off" : "Turn on");
                state.addActionListener(event -> onToggle.accept(device));
                row.add(labels, BorderLayout.CENTER);
                row.add(state, BorderLayout.EAST);
                quickControls.add(row);
            }
        }
        rooms.removeAll();
        rooms.setLayout(new java.awt.GridLayout(0, 1, 0, 8));
        if (roomSummaries.isEmpty()) {
            rooms.add(RoomsPanel.message("No rooms found in the database."));
        } else {
            for (RoomSummary room : roomSummaries) {
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(false);
                JLabel name = new JLabel(room.name());
                name.setFont(new Font("SansSerif", Font.BOLD, 12));
                name.setForeground(UiStyle.TEXT);
                JLabel count = new JLabel(room.activeCount() + " on  /  " + room.deviceCount() + " devices");
                count.setFont(new Font("SansSerif", Font.PLAIN, 11));
                count.setForeground(UiStyle.MUTED);
                row.add(name, BorderLayout.WEST);
                row.add(count, BorderLayout.EAST);
                rooms.add(row);
            }
        }
        activity.removeAll();
        activity.setLayout(new java.awt.GridLayout(0, 1, 0, 7));
        if (recentActivity.isEmpty()) {
            JLabel empty = new JLabel("Changes made in this app session will appear here.");
            empty.setForeground(UiStyle.MUTED);
            empty.setFont(new Font("SansSerif", Font.PLAIN, 12));
            activity.add(empty);
        } else {
            for (String item : recentActivity) {
                JLabel entry = new JLabel("•  " + item);
                entry.setForeground(UiStyle.MUTED);
                entry.setFont(new Font("SansSerif", Font.PLAIN, 11));
                activity.add(entry);
            }
        }
        revalidate();
        repaint();
    }

    private JPanel section(String title, JPanel body) {
        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 14));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font("SansSerif", Font.BOLD, 14));
        heading.setForeground(UiStyle.TEXT);
        card.add(heading, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }
}
