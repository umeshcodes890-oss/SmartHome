package com.smarthome.ui;

import com.smarthome.model.Room;
import com.smarthome.model.SensorAlert;
import com.smarthome.model.SensorAlertSettings;
import com.smarthome.model.SensorReading;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;

final class SensorMonitoringPanel extends JPanel {
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final JComboBox<RoomOption> roomFilter = new JComboBox<>();
    private final JComboBox<TypeOption> typeFilter = new JComboBox<>();
    private final JComboBox<Integer> interval = new JComboBox<>(new Integer[]{15, 30, 60, 120});
    private final JButton start = UiStyle.button("Start Simulation", true);
    private final JButton stop = UiStyle.button("Stop Simulation", false);
    private final JTextField temperatureThreshold = new JTextField(6);
    private final JTextField humidityThreshold = new JTextField(6);
    private final JLabel thresholdState = new JLabel(" ");
    private final JPanel currentReadings = new JPanel();
    private final JPanel alerts = new JPanel();
    private final ReadingTableModel historyModel = new ReadingTableModel();
    private final JTable history = new JTable(historyModel);
    private final TrendChart trend = new TrendChart();
    private final Consumer<Filter> onFilterChange;
    private final Consumer<SimulationSettings> onSimulationChange;
    private final Consumer<SensorAlertSettings> onSaveThresholds;
    private boolean updatingControls;
    private SensorReading.Type selectedType = SensorReading.Type.TEMPERATURE;

    SensorMonitoringPanel(Consumer<Filter> onFilterChange,
            Consumer<SimulationSettings> onSimulationChange,
            Consumer<SensorAlertSettings> onSaveThresholds) {
        this.onFilterChange = onFilterChange;
        this.onSimulationChange = onSimulationChange;
        this.onSaveThresholds = onSaveThresholds;
        setLayout(new BorderLayout(0, 14));
        setBackground(UiStyle.WORKSPACE);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
        content.add(buildToolbar());
        content.add(javax.swing.Box.createVerticalStrut(14));
        content.add(buildThresholdCard());
        content.add(javax.swing.Box.createVerticalStrut(14));
        content.add(buildCurrentCard());
        content.add(javax.swing.Box.createVerticalStrut(14));
        content.add(buildChartCard());
        content.add(javax.swing.Box.createVerticalStrut(14));
        content.add(buildHistoryCard());
        content.add(javax.swing.Box.createVerticalStrut(14));
        content.add(buildAlertsCard());
        JScrollPane scroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        roomFilter.addActionListener(event -> publishFilter());
        typeFilter.addActionListener(event -> publishFilter());
        start.addActionListener(event -> publishSimulation(true));
        stop.addActionListener(event -> publishSimulation(false));
        history.setFillsViewportHeight(true);
        history.setRowHeight(30);
        history.setBackground(UiStyle.SURFACE);
        history.setForeground(UiStyle.TEXT);
        history.setGridColor(UiStyle.BORDER);
        history.setSelectionBackground(UiStyle.BLUE_PALE);
        history.setSelectionForeground(UiStyle.TEXT);
        history.getTableHeader().setBackground(UiStyle.TABLE_HEADER);
        history.getTableHeader().setForeground(UiStyle.MUTED);
        history.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        history.getTableHeader().setReorderingAllowed(false);
    }

    private JPanel buildToolbar() {
        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 12));
        JLabel heading = heading("Sensor monitoring");
        JLabel subtitle = new JLabel("Room temperature, humidity, motion, recent readings and alerts");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(UiStyle.MUTED);
        JPanel title = new JPanel(new BorderLayout(0, 4));
        title.setOpaque(false);
        title.add(heading, BorderLayout.NORTH);
        title.add(subtitle, BorderLayout.SOUTH);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        filters.setOpaque(false);
        addControl(filters, "Room", roomFilter);
        addControl(filters, "Sensor", typeFilter);
        typeFilter.addItem(new TypeOption("All types", null));
        typeFilter.addItem(new TypeOption("Temperature", SensorReading.Type.TEMPERATURE));
        typeFilter.addItem(new TypeOption("Humidity", SensorReading.Type.HUMIDITY));
        typeFilter.addItem(new TypeOption("Motion", SensorReading.Type.MOTION));
        typeFilter.setSelectedIndex(1);
        JPanel simulation = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 2));
        simulation.setOpaque(false);
        simulation.add(new JLabel("Interval (sec)"));
        UiStyle.styleControl(interval);
        simulation.add(interval);
        simulation.add(start);
        simulation.add(stop);
        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.add(filters, BorderLayout.WEST);
        controls.add(simulation, BorderLayout.EAST);
        card.add(title, BorderLayout.NORTH);
        card.add(controls, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildThresholdCard() {
        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 10));
        card.add(heading("Alert thresholds"), BorderLayout.NORTH);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        controls.add(label("Temperature above (°C)"));
        UiStyle.styleControl(temperatureThreshold);
        controls.add(temperatureThreshold);
        controls.add(label("Humidity above (%)"));
        UiStyle.styleControl(humidityThreshold);
        controls.add(humidityThreshold);
        JButton save = UiStyle.button("Save thresholds", true);
        save.addActionListener(event -> saveThresholds());
        controls.add(save);
        thresholdState.setFont(new Font("SansSerif", Font.PLAIN, 11));
        thresholdState.setForeground(UiStyle.MUTED);
        controls.add(thresholdState);
        card.add(controls, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildCurrentCard() {
        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 10));
        card.add(heading("Current readings"), BorderLayout.NORTH);
        currentReadings.setOpaque(false);
        currentReadings.setLayout(new javax.swing.BoxLayout(currentReadings, javax.swing.BoxLayout.Y_AXIS));
        card.add(currentReadings, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildChartCard() {
        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 10));
        card.add(heading("Recent trend"), BorderLayout.NORTH);
        trend.setPreferredSize(new Dimension(300, 190));
        card.add(trend, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildHistoryCard() {
        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 10));
        card.add(heading("Recent history"), BorderLayout.NORTH);
        history.setPreferredScrollableViewportSize(new Dimension(700, 210));
        card.add(new JScrollPane(history), BorderLayout.CENTER);
        return card;
    }

    private JPanel buildAlertsCard() {
        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 10));
        card.add(heading("Recent threshold alerts"), BorderLayout.NORTH);
        alerts.setOpaque(false);
        alerts.setLayout(new javax.swing.BoxLayout(alerts, javax.swing.BoxLayout.Y_AXIS));
        card.add(alerts, BorderLayout.CENTER);
        return card;
    }

    void setRooms(List<Room> rooms) {
        RoomOption previous = (RoomOption) roomFilter.getSelectedItem();
        updatingControls = true;
        try {
            roomFilter.removeAllItems();
            roomFilter.addItem(new RoomOption("All rooms", null));
            for (Room room : rooms) roomFilter.addItem(new RoomOption(room.name(), room.id()));
            if (previous != null && previous.id() != null) {
                for (int i = 0; i < roomFilter.getItemCount(); i++) {
                    RoomOption option = roomFilter.getItemAt(i);
                    if (previous.id().equals(option.id())) {
                        roomFilter.setSelectedIndex(i);
                        break;
                    }
                }
            }
        } finally {
            updatingControls = false;
        }
    }

    Filter filter() {
        RoomOption room = (RoomOption) roomFilter.getSelectedItem();
        TypeOption type = (TypeOption) typeFilter.getSelectedItem();
        return new Filter(room == null ? null : room.id(), type == null ? null : type.type());
    }

    void setSimulationSettings(SimulationSettings settings) {
        updatingControls = true;
        try {
            interval.setSelectedItem(settings.intervalSeconds());
            start.setEnabled(!settings.enabled());
            stop.setEnabled(settings.enabled());
            thresholdState.setText(settings.enabled()
                ? "Simulation running · readings are labelled SIMULATED"
                : "Simulation stopped");
        } finally {
            updatingControls = false;
        }
    }

    void setThresholdSettings(SensorAlertSettings settings) {
        temperatureThreshold.setText(settings.temperatureThreshold().stripTrailingZeros().toPlainString());
        humidityThreshold.setText(settings.humidityThreshold().stripTrailingZeros().toPlainString());
        thresholdState.setForeground(UiStyle.MUTED);
    }

    void setThresholdError(String message) {
        thresholdState.setText(message);
        thresholdState.setForeground(UiStyle.DANGER);
    }

    void setThresholdMessage(String message) {
        thresholdState.setText(message);
        thresholdState.setForeground(UiStyle.GREEN);
    }

    void setLoading() {
        currentReadings.removeAll();
        currentReadings.add(message("Loading sensor readings…"));
        currentReadings.revalidate();
        historyModel.setReadings(List.of());
    }

    void setLoadError(String error) {
        currentReadings.removeAll();
        currentReadings.add(message(error));
        currentReadings.revalidate();
    }

    void setData(List<SensorReading> latest, List<SensorReading> historyReadings, List<SensorAlert> alertRows) {
        renderCurrentReadings(latest);
        historyModel.setReadings(historyReadings);
        trend.setReadings(historyReadings, selectedType);
        renderAlerts(alertRows);
    }

    private void renderCurrentReadings(List<SensorReading> readings) {
        Map<Integer, RoomReadings> byRoom = new LinkedHashMap<>();
        for (SensorReading reading : readings) {
            RoomReadings room = byRoom.computeIfAbsent(reading.roomId(),
                ignored -> new RoomReadings(reading.roomId(), reading.roomName()));
            room.readings.put(reading.type(), reading);
        }
        currentReadings.removeAll();
        if (byRoom.isEmpty()) {
            currentReadings.add(message("No readings match these filters. Start simulation to generate labelled sample data."));
        } else {
            DateTimeFormatter time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
            for (RoomReadings room : byRoom.values()) {
                JPanel row = new JPanel(new BorderLayout(12, 0));
                row.setBackground(UiStyle.SURFACE);
                row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiStyle.BORDER),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)));
                JLabel name = new JLabel(room.name);
                name.setFont(new Font("SansSerif", Font.BOLD, 12));
                name.setForeground(UiStyle.TEXT);
                InstantHolder last = new InstantHolder();
                boolean simulated = false;
                for (SensorReading reading : room.readings.values()) {
                    if (last.value == null || reading.recordedAt().isAfter(last.value)) last.value = reading.recordedAt();
                    simulated |= reading.simulated();
                }
                JLabel timestamp = new JLabel("Last reading · " + time.format(last.value)
                    + (simulated ? "  ·  SIMULATED" : ""));
                timestamp.setFont(new Font("SansSerif", Font.PLAIN, 10));
                timestamp.setForeground(simulated ? UiStyle.BLUE : UiStyle.MUTED);
                JPanel roomTitle = new JPanel(new BorderLayout(0, 4));
                roomTitle.setOpaque(false);
                roomTitle.add(name, BorderLayout.NORTH);
                roomTitle.add(timestamp, BorderLayout.SOUTH);
                JPanel values = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
                values.setOpaque(false);
                values.add(readingValue(room, SensorReading.Type.TEMPERATURE, "Temperature"));
                values.add(readingValue(room, SensorReading.Type.HUMIDITY, "Humidity"));
                values.add(readingValue(room, SensorReading.Type.MOTION, "Motion"));
                row.add(roomTitle, BorderLayout.CENTER);
                row.add(values, BorderLayout.EAST);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
                currentReadings.add(row);
                currentReadings.add(javax.swing.Box.createVerticalStrut(7));
            }
        }
        currentReadings.revalidate();
        currentReadings.repaint();
    }

    private JLabel readingValue(RoomReadings room, SensorReading.Type type, String label) {
        SensorReading reading = room.readings.get(type);
        String value = reading == null ? "—" : reading.formattedValue();
        JLabel text = new JLabel(label + ": " + value);
        text.setFont(new Font("SansSerif", Font.PLAIN, 11));
        text.setForeground(reading != null && reading.simulated() ? UiStyle.BLUE : UiStyle.TEXT);
        return text;
    }

    private void renderAlerts(List<SensorAlert> alertRows) {
        alerts.removeAll();
        if (alertRows.isEmpty()) {
            alerts.add(message("No threshold alerts have been recorded."));
        } else {
            for (SensorAlert alert : alertRows) {
                String status = alert.active() ? "ACTIVE" : "RESOLVED";
                String measurement = alert.readingValue().stripTrailingZeros().toPlainString();
                String threshold = alert.thresholdValue().stripTrailingZeros().toPlainString();
                JLabel row = new JLabel(alert.roomName() + " · " + alert.type() + " " + measurement
                    + " > " + threshold + " · " + status + " · " + TIME_FORMAT.format(alert.triggeredAt())
                    + (alert.simulated() ? " · SIMULATED" : ""));
                row.setFont(new Font("SansSerif", Font.PLAIN, 11));
                row.setForeground(alert.active() ? UiStyle.DANGER : UiStyle.MUTED);
                alerts.add(row);
            }
        }
        alerts.revalidate();
        alerts.repaint();
    }

    private void publishFilter() {
        if (updatingControls) return;
        TypeOption type = (TypeOption) typeFilter.getSelectedItem();
        selectedType = type == null ? null : type.type();
        onFilterChange.accept(filter());
    }

    private void publishSimulation(boolean enabled) {
        Integer seconds = (Integer) interval.getSelectedItem();
        if (seconds != null) onSimulationChange.accept(new SimulationSettings(enabled, seconds));
    }

    private void saveThresholds() {
        try {
            SensorAlertSettings settings = new SensorAlertSettings(
                new BigDecimal(temperatureThreshold.getText().trim()),
                new BigDecimal(humidityThreshold.getText().trim()));
            thresholdState.setText("Saving thresholds…");
            thresholdState.setForeground(UiStyle.MUTED);
            onSaveThresholds.accept(settings);
        } catch (IllegalArgumentException ex) {
            thresholdState.setText(ex.getMessage() == null ? "Enter valid numeric thresholds." : ex.getMessage());
            thresholdState.setForeground(UiStyle.DANGER);
        }
    }

    private static void addControl(JPanel parent, String name, JComboBox<?> control) {
        parent.add(label(name));
        UiStyle.styleControl(control);
        parent.add(control);
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UiStyle.MUTED);
        return label;
    }

    private static JLabel heading(String text) {
        JLabel heading = new JLabel(text);
        heading.setFont(new Font("SansSerif", Font.BOLD, 14));
        heading.setForeground(UiStyle.TEXT);
        return heading;
    }

    private static JLabel message(String text) {
        JLabel message = label(text);
        message.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return message;
    }

    record Filter(Integer roomId, SensorReading.Type type) {}
    record SimulationSettings(boolean enabled, int intervalSeconds) {}

    private record RoomOption(String name, Integer id) {
        @Override public String toString() { return name; }
    }

    private record TypeOption(String name, SensorReading.Type type) {
        @Override public String toString() { return name; }
    }

    private static final class RoomReadings {
        private final String name;
        private final EnumMap<SensorReading.Type, SensorReading> readings =
            new EnumMap<>(SensorReading.Type.class);
        RoomReadings(int id, String name) { this.name = name; }
    }

    private static final class InstantHolder {
        private java.time.Instant value;
    }

    private static final class ReadingTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Room", "Sensor", "Reading", "Source", "Recorded at"};
        private List<SensorReading> readings = List.of();
        void setReadings(List<SensorReading> values) {
            readings = values.stream().sorted(Comparator.comparing(SensorReading::recordedAt).reversed()).toList();
            fireTableDataChanged();
        }
        @Override public int getRowCount() { return readings.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }
        @Override public Object getValueAt(int row, int column) {
            SensorReading reading = readings.get(row);
            return switch (column) {
                case 0 -> reading.roomName();
                case 1 -> reading.type();
                case 2 -> reading.formattedValue();
                case 3 -> reading.simulated() ? "SIMULATED" : "SENSOR";
                case 4 -> TIME_FORMAT.format(reading.recordedAt());
                default -> throw new IndexOutOfBoundsException("Invalid reading table column.");
            };
        }
    }

    private static final class TrendChart extends JPanel {
        private List<SensorReading> readings = List.of();
        private SensorReading.Type type;
        private static final Color[] SERIES_COLORS = {
            UiStyle.BLUE, UiStyle.GREEN, new Color(225, 154, 61), new Color(163, 104, 211)
        };

        TrendChart() {
            setOpaque(true);
            setBackground(UiStyle.SURFACE);
            setBorder(BorderFactory.createLineBorder(UiStyle.BORDER));
        }

        void setReadings(List<SensorReading> values, SensorReading.Type selectedType) {
            type = selectedType;
            readings = values.stream()
                .filter(reading -> selectedType != null && reading.type() == selectedType)
                .sorted(Comparator.comparing(SensorReading::recordedAt))
                .toList();
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left = 54, right = getWidth() - 18, top = 20, bottom = getHeight() - 48;
            g.setColor(UiStyle.BORDER);
            g.drawLine(left, top, left, bottom);
            g.drawLine(left, bottom, right, bottom);
            if (type == null) {
                drawMessage(g, "Choose a sensor type to view its trend.");
            } else if (readings.isEmpty()) {
                drawMessage(g, "No recent " + type.name().toLowerCase() + " readings.");
            } else {
                double min = readings.stream().mapToDouble(reading -> reading.value().doubleValue()).min().orElse(0);
                double max = readings.stream().mapToDouble(reading -> reading.value().doubleValue()).max().orElse(0);
                if (min == max) { min -= 1; max += 1; }
                long startMillis = readings.getFirst().recordedAt().toEpochMilli();
                long endMillis = readings.getLast().recordedAt().toEpochMilli();
                Map<Integer, List<SensorReading>> byRoom = new LinkedHashMap<>();
                for (SensorReading reading : readings) {
                    byRoom.computeIfAbsent(reading.roomId(), ignored -> new ArrayList<>()).add(reading);
                }
                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g.setColor(UiStyle.MUTED);
                g.drawString(String.format(java.util.Locale.ROOT, "%.1f", max), 8, top + 4);
                g.drawString(String.format(java.util.Locale.ROOT, "%.1f", min), 8, bottom);
                g.setStroke(new BasicStroke(2f));
                int series = 0;
                int legendX = left;
                for (Map.Entry<Integer, List<SensorReading>> entry : byRoom.entrySet()) {
                    List<SensorReading> roomReadings = entry.getValue();
                    g.setColor(SERIES_COLORS[series % SERIES_COLORS.length]);
                    int previousX = 0, previousY = 0;
                    for (int i = 0; i < roomReadings.size(); i++) {
                        SensorReading reading = roomReadings.get(i);
                        double value = reading.value().doubleValue();
                        long elapsed = reading.recordedAt().toEpochMilli() - startMillis;
                        long duration = endMillis - startMillis;
                        int x = duration == 0 ? (left + right) / 2
                            : left + (int) ((right - left) * (elapsed / (double) duration));
                        int y = bottom - (int) ((value - min) / (max - min) * (bottom - top));
                        if (i > 0) g.drawLine(previousX, previousY, x, y);
                        g.fillOval(x - 3, y - 3, 6, 6);
                        previousX = x;
                        previousY = y;
                    }
                    g.fillRect(legendX, getHeight() - 20, 9, 9);
                    g.setColor(UiStyle.MUTED);
                    String roomName = roomReadings.getFirst().roomName();
                    g.drawString(roomName, legendX + 13, getHeight() - 12);
                    legendX += 24 + g.getFontMetrics().stringWidth(roomName);
                    series++;
                }
            }
            g.dispose();
        }

        private void drawMessage(Graphics2D g, String text) {
            g.setColor(UiStyle.MUTED);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString(text, 22, Math.max(40, getHeight() / 2));
        }
    }
}
