package com.smarthome.ui;

import com.smarthome.model.Device;
import com.smarthome.model.RoomSummary;
import com.smarthome.model.SensorReading;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.EnumMap;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
    private final JPanel sensorReadings = new JPanel();
    private final JLabel dataState = new JLabel("Dashboard data is loaded from MySQL.");
    private final JLabel lastRefresh = new JLabel("Last refreshed: not yet");
    private final JLabel sensorState = new JLabel("No sensor readings are available.");
    private final JComboBox<Integer> refreshInterval = new JComboBox<>(new Integer[]{5, 10, 15, 30, 60});
    private final JCheckBox simulatorEnabled = new JCheckBox("Generate simulated readings");
    private final JComboBox<Integer> simulatorInterval = new JComboBox<>(new Integer[]{15, 30, 60, 120});
    private final Consumer<MonitoringSettings> onMonitoringSettings;
    private boolean updatingMonitoringControls;

    DashboardPanel(Consumer<Device> onToggle, Consumer<MonitoringSettings> onMonitoringSettings) {
        this.onMonitoringSettings = onMonitoringSettings;
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
        dataState.setFont(new Font("SansSerif", Font.PLAIN, 11));
        dataState.setForeground(UiStyle.MUTED);
        intro.add(headline, BorderLayout.NORTH);
        JPanel introDetails = new JPanel(new java.awt.GridLayout(0, 1, 0, 3));
        introDetails.setOpaque(false);
        introDetails.add(subtitle);
        introDetails.add(dataState);
        lastRefresh.setForeground(UiStyle.MUTED);
        lastRefresh.setFont(new Font("SansSerif", Font.PLAIN, 11));
        introDetails.add(lastRefresh);
        intro.add(introDetails, BorderLayout.SOUTH);
        JPanel controls = new JPanel(new java.awt.GridLayout(0, 1, 0, 5));
        controls.setOpaque(false);
        JPanel refreshControl = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        refreshControl.setOpaque(false);
        JLabel refreshLabel = new JLabel("Refresh (sec)");
        refreshLabel.setForeground(UiStyle.MUTED);
        refreshControl.add(refreshLabel);
        UiStyle.styleControl(refreshInterval);
        refreshControl.add(refreshInterval);
        controls.add(refreshControl);
        JPanel simulatorControl = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        simulatorControl.setOpaque(false);
        simulatorEnabled.setOpaque(false);
        simulatorEnabled.setForeground(UiStyle.TEXT);
        simulatorEnabled.setToolTipText("Creates clearly labelled simulated readings; no physical sensors are connected.");
        simulatorControl.add(simulatorEnabled);
        JLabel every = new JLabel("every");
        every.setForeground(UiStyle.MUTED);
        simulatorControl.add(every);
        UiStyle.styleControl(simulatorInterval);
        simulatorControl.add(simulatorInterval);
        JLabel seconds = new JLabel("sec");
        seconds.setForeground(UiStyle.MUTED);
        simulatorControl.add(seconds);
        controls.add(simulatorControl);
        intro.add(controls, BorderLayout.EAST);
        refreshInterval.addActionListener(event -> publishMonitoringSettings());
        simulatorEnabled.addActionListener(event -> publishMonitoringSettings());
        simulatorInterval.addActionListener(event -> publishMonitoringSettings());

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

        JPanel sensorCard = UiStyle.card();
        sensorCard.setLayout(new BorderLayout(0, 10));
        JLabel sensorTitle = new JLabel("Room sensors · simulated data is labelled");
        sensorTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        sensorTitle.setForeground(UiStyle.TEXT);
        sensorReadings.setLayout(new java.awt.GridLayout(0, 1, 0, 8));
        sensorReadings.setOpaque(false);
        sensorState.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sensorState.setForeground(UiStyle.MUTED);
        JPanel sensorBody = new JPanel(new BorderLayout(0, 8));
        sensorBody.setOpaque(false);
        sensorBody.add(sensorState, BorderLayout.NORTH);
        sensorBody.add(sensorReadings, BorderLayout.CENTER);
        sensorCard.add(sensorTitle, BorderLayout.NORTH);
        sensorCard.add(sensorBody, BorderLayout.CENTER);

        JPanel vertical = new JPanel();
        vertical.setLayout(new javax.swing.BoxLayout(vertical, javax.swing.BoxLayout.Y_AXIS));
        vertical.setOpaque(false);
        lower.setAlignmentX(LEFT_ALIGNMENT);
        sensorCard.setAlignmentX(LEFT_ALIGNMENT);
        activityCard.setAlignmentX(LEFT_ALIGNMENT);
        lower.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 280));
        vertical.add(lower);
        vertical.add(javax.swing.Box.createVerticalStrut(14));
        vertical.add(sensorCard);
        vertical.add(javax.swing.Box.createVerticalStrut(14));
        vertical.add(activityCard);
        content.add(vertical, BorderLayout.CENTER);
        add(new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        ((JScrollPane) getComponent(0)).setBorder(null);
        ((JScrollPane) getComponent(0)).getViewport().setOpaque(false);
        ((JScrollPane) getComponent(0)).setOpaque(false);
    }

    void setMonitoringSettings(MonitoringSettings settings) {
        updatingMonitoringControls = true;
        try {
            refreshInterval.setSelectedItem(settings.refreshSeconds());
            simulatorEnabled.setSelected(settings.simulatorEnabled());
            simulatorInterval.setSelectedItem(settings.simulatorIntervalSeconds());
        } finally {
            updatingMonitoringControls = false;
        }
    }

    private void publishMonitoringSettings() {
        if (updatingMonitoringControls) return;
        Integer refresh = (Integer) refreshInterval.getSelectedItem();
        Integer sample = (Integer) simulatorInterval.getSelectedItem();
        if (refresh != null && sample != null) {
            onMonitoringSettings.accept(new MonitoringSettings(refresh, simulatorEnabled.isSelected(), sample));
        }
    }

    void setData(List<Device> devices, List<RoomSummary> roomSummaries, List<String> recentActivity,
            Consumer<Device> onToggle) {
        dataState.setText(devices.isEmpty() ? "No devices are registered yet."
            : "Live summary · " + devices.size() + " device(s)");
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

    void setLoading() { dataState.setText("Loading dashboard data…"); }
    void setLoadError(String message) { dataState.setText(message); }
    void setSensorError(String message) {
        sensorReadings.removeAll();
        sensorState.setText(message);
        sensorReadings.revalidate();
        sensorReadings.repaint();
    }
    void setSensors(List<SensorReading> readings) {
        sensorReadings.removeAll();
        if (readings.isEmpty()) {
            sensorState.setText("No readings yet. Enable the simulator or connect a sensor source.");
        } else {
            Map<Integer, RoomSensorSummary> byRoom = new LinkedHashMap<>();
            for (SensorReading reading : readings) {
                RoomSensorSummary room = byRoom.computeIfAbsent(reading.roomId(),
                    ignored -> new RoomSensorSummary(reading.roomName()));
                room.values.put(reading.type(), reading);
            }
            sensorState.setText(byRoom.size() + " room(s) with recent readings; generated values are clearly labelled.");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
            for (RoomSensorSummary room : byRoom.values()) {
                JPanel item = new JPanel(new BorderLayout(0, 7));
                item.setBackground(UiStyle.SURFACE);
                item.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiStyle.BORDER),
                    BorderFactory.createEmptyBorder(9, 12, 9, 12)));
                Instant last = null;
                boolean simulated = false;
                for (SensorReading reading : room.values.values()) {
                    if (last == null || reading.recordedAt().isAfter(last)) last = reading.recordedAt();
                    simulated |= reading.simulated();
                }
                JLabel title = new JLabel(room.name + "  ·  Last reading " + formatter.format(last)
                    + (simulated ? "  ·  SIMULATED" : ""));
                title.setFont(new Font("SansSerif", Font.BOLD, 11));
                title.setForeground(simulated ? UiStyle.BLUE : UiStyle.TEXT);
                JPanel values = new JPanel(new java.awt.GridLayout(1, 3, 8, 0));
                values.setOpaque(false);
                values.add(sensorValue(room, SensorReading.Type.TEMPERATURE, "Temperature"));
                values.add(sensorValue(room, SensorReading.Type.HUMIDITY, "Humidity"));
                values.add(sensorValue(room, SensorReading.Type.MOTION, "Motion"));
                item.add(title, BorderLayout.NORTH);
                item.add(values, BorderLayout.CENTER);
                sensorReadings.add(item);
            }
        }
        sensorReadings.revalidate();
        sensorReadings.repaint();
    }

    private static JLabel sensorValue(RoomSensorSummary room, SensorReading.Type type, String label) {
        SensorReading reading = room.values.get(type);
        String source = reading != null && reading.simulated() ? " · SIMULATED" : "";
        JLabel value = new JLabel(label + ": " + (reading == null ? "—" : reading.formattedValue() + source));
        value.setFont(new Font("SansSerif", Font.PLAIN, 11));
        value.setForeground(reading != null && reading.simulated() ? UiStyle.BLUE : UiStyle.TEXT);
        return value;
    }

    private static final class RoomSensorSummary {
        private final String name;
        private final EnumMap<SensorReading.Type, SensorReading> values =
            new EnumMap<>(SensorReading.Type.class);
        RoomSensorSummary(String name) { this.name = name; }
    }
    void setLastRefreshed(java.time.Instant instant) {
        lastRefresh.setText("Last refreshed: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault()).format(instant));
    }

    record MonitoringSettings(int refreshSeconds, boolean simulatorEnabled, int simulatorIntervalSeconds) {}

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
