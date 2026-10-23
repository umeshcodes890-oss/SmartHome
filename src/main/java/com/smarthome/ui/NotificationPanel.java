package com.smarthome.ui;

import com.smarthome.model.ActivityLog;
import java.awt.Color;
import java.awt.Font;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.plaf.basic.BasicButtonUI;

final class NotificationPanel extends JButton {
    private static final int MAX_NOTIFICATIONS = 30;
    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Deque<Notification> notifications = new ArrayDeque<>();

    NotificationPanel() {
        super("Notifications");
        setFont(new Font("SansSerif", Font.BOLD, 11));
        setForeground(UiStyle.TEXT);
        setBackground(UiStyle.SURFACE);
        setOpaque(true);
        setContentAreaFilled(true);
        setUI(new BasicButtonUI());
        setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(UiStyle.BORDER),
            javax.swing.BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        setFocusPainted(false);
        addActionListener(event -> showRecent());
    }

    void addAction(String message) {
        add(new Notification(message, false, java.time.Instant.now(), false));
    }

    void addError(String message) {
        add(new Notification(message, true, java.time.Instant.now(), false));
    }

    void setRecentLogs(List<ActivityLog> logs) {
        notifications.removeIf(Notification::persistent);
        for (ActivityLog log : logs) {
            boolean failed = log.outcome() == ActivityLog.Outcome.FAILED;
            String subject = log.deviceName() == null ? log.eventType() : log.deviceName();
            notifications.addLast(new Notification(subject + " · " + log.outcome().name(), failed,
                log.occurredAt(), true));
        }
        while (notifications.size() > MAX_NOTIFICATIONS) notifications.removeLast();
        updateText();
    }

    private void add(Notification notification) {
        notifications.addFirst(notification);
        while (notifications.size() > MAX_NOTIFICATIONS) notifications.removeLast();
        updateText();
    }

    private void updateText() {
        long errors = notifications.stream().filter(Notification::error).count();
        setText(errors == 0 ? "Notifications (" + notifications.size() + ")"
            : "Notifications (" + notifications.size() + ", " + errors + " errors)");
        setForeground(errors == 0 ? UiStyle.TEXT : new Color(170, 55, 50));
    }

    private void showRecent() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem heading = new JMenuItem("Recent actions and errors");
        heading.setEnabled(false);
        menu.add(heading);
        menu.addSeparator();
        if (notifications.isEmpty()) {
            JMenuItem empty = new JMenuItem("No recent notifications");
            empty.setEnabled(false);
            menu.add(empty);
        } else {
            for (Notification notification : notifications) {
                JMenuItem item = new JMenuItem(TIME.format(notification.time()) + "  " + notification.message());
                item.setForeground(notification.error() ? new Color(170, 55, 50) : UiStyle.TEXT);
                item.setEnabled(false);
                menu.add(item);
            }
        }
        menu.show(this, 0, getHeight());
    }

    private record Notification(String message, boolean error, java.time.Instant time, boolean persistent) {}
}