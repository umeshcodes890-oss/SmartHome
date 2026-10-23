package com.smarthome.ui;

import com.smarthome.model.DeviceSchedule;
import com.smarthome.model.ScheduleExecution;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

final class SchedulePanel extends JPanel {
    private static final DateTimeFormatter DATE_TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());
    private final Runnable onAdd;
    private final Consumer<DeviceSchedule> onEdit;
    private final Consumer<DeviceSchedule> onToggle;
    private final Consumer<DeviceSchedule> onDelete;
    private final Runnable onRefresh;
    private final ScheduleTableModel scheduleModel = new ScheduleTableModel();
    private final DefaultTableModel historyModel = new DefaultTableModel(
        new Object[]{"Schedule", "Device", "Action", "Occurrence", "Result", "Details"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable schedules = new JTable(scheduleModel);
    private final JTable history = new JTable(historyModel);
    private final JButton edit = UiStyle.button("Edit", false);
    private final JButton toggle = UiStyle.button("Disable", false);
    private final JButton delete = UiStyle.button("Delete", false);
    private final JButton addButton = UiStyle.button("＋  Create schedule", true);
    private final JLabel state = new JLabel("Schedules run while this application is open");
    private final JLabel scheduleTableState = new JLabel("Schedule data is loaded from MySQL.");
    private final JLabel historyState = new JLabel("Execution history is loaded from MySQL.");

    SchedulePanel(Runnable onAdd, Consumer<DeviceSchedule> onEdit,
            Consumer<DeviceSchedule> onToggle, Consumer<DeviceSchedule> onDelete, Runnable onRefresh) {
        this.onAdd = onAdd;
        this.onEdit = onEdit;
        this.onToggle = onToggle;
        this.onDelete = onDelete;
        this.onRefresh = onRefresh;
        setLayout(new BorderLayout());
        setBackground(UiStyle.WORKSPACE);
        setBorder(BorderFactory.createEmptyBorder(22, 28, 24, 28));

        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 12));
        JPanel toolbar = new JPanel(new BorderLayout(0, 8));
        toolbar.setOpaque(false);
        state.setForeground(UiStyle.MUTED);
        state.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton refresh = UiStyle.button("Refresh", false);
        actions.add(addButton);
        actions.add(edit);
        actions.add(toggle);
        actions.add(delete);
        actions.add(refresh);
        toolbar.add(state, BorderLayout.NORTH);
        toolbar.add(actions, BorderLayout.SOUTH);

        configureTable(schedules);
        configureTable(history);
        JTabbedPane tabs = new JTabbedPane();
        scheduleTableState.setForeground(UiStyle.MUTED);
        scheduleTableState.setFont(new Font("SansSerif", Font.PLAIN, 11));
        tabs.addTab("Schedules", tableTab(schedules, scheduleTableState));
        historyState.setForeground(UiStyle.MUTED);
        historyState.setFont(new Font("SansSerif", Font.PLAIN, 11));
        tabs.addTab("Execution history", tableTab(history, historyState));
        card.add(toolbar, BorderLayout.NORTH);
        card.add(tabs, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        addButton.addActionListener(event -> onAdd.run());
        edit.addActionListener(event -> {
            DeviceSchedule selected = selectedSchedule();
            if (selected != null) onEdit.accept(selected);
        });
        toggle.addActionListener(event -> {
            DeviceSchedule selected = selectedSchedule();
            if (selected != null) onToggle.accept(selected);
        });
        delete.addActionListener(event -> {
            DeviceSchedule selected = selectedSchedule();
            if (selected != null) onDelete.accept(selected);
        });
        refresh.addActionListener(event -> onRefresh.run());
        schedules.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateActions();
        });
        schedules.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        updateActions();
    }

    void setAdminMode(boolean admin) {
        addButton.setVisible(admin);
        edit.setVisible(admin);
        toggle.setVisible(admin);
        delete.setVisible(admin);
    }

    void setSchedules(List<DeviceSchedule> values) {
        schedules.clearSelection();
        scheduleModel.setSchedules(values);
        state.setText(values.isEmpty() ? "No schedules yet. Create a schedule to automate a device."
            : values.size() + " schedule(s) loaded.");
        scheduleTableState.setText(state.getText());
        updateActions();
    }

    void setHistory(List<ScheduleExecution> values) {
        historyModel.setRowCount(0);
        for (ScheduleExecution execution : values) {
            historyModel.addRow(new Object[]{
                execution.scheduleName(), execution.deviceName(), execution.action(),
                format(execution.occurrenceAt()), execution.result(),
                execution.details() == null ? "" : execution.details()
            });
        }
        historyState.setText(values.isEmpty() ? "No schedule executions have been recorded yet."
            : values.size() + " execution record(s) loaded.");
    }

    void setLoading() {
        state.setText("Loading schedules…");
        scheduleTableState.setText("Loading schedules…");
        historyState.setText("Loading execution history…");
    }

    void setLoadError(String message) {
        state.setText(message);
        scheduleTableState.setText(message);
        historyState.setText(message);
    }

    private static JPanel tableTab(JTable table, JLabel state) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.add(state, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private DeviceSchedule selectedSchedule() {
        int row = schedules.getSelectedRow();
        return row < 0 ? null : scheduleModel.getSchedule(schedules.convertRowIndexToModel(row));
    }

    private void updateActions() {
        DeviceSchedule selected = selectedSchedule();
        edit.setEnabled(selected != null);
        toggle.setEnabled(selected != null);
        delete.setEnabled(selected != null);
        if (selected != null) toggle.setText(selected.enabled() ? "Disable" : "Enable");
    }

    private static String format(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant);
    }

    private static void configureTable(JTable table) {
        UiStyle.styleTable(table);
        table.setAutoCreateRowSorter(true);
    }

    private static final class ScheduleTableModel extends DefaultTableModel {
        private List<DeviceSchedule> values = List.of();

        ScheduleTableModel() {
            super(new Object[]{"Schedule", "Device", "Action", "Repeat", "Next execution", "Timezone", "Status"}, 0);
        }

        void setSchedules(List<DeviceSchedule> schedules) {
            values = List.copyOf(schedules);
            setRowCount(0);
            for (DeviceSchedule schedule : values) {
                setRowCount(getRowCount() + 1);
                int row = getRowCount() - 1;
                setValueAt(schedule.scheduleName(), row, 0);
                setValueAt(schedule.deviceName(), row, 1);
                setValueAt(schedule.action(), row, 2);
                setValueAt(repeatLabel(schedule), row, 3);
                setValueAt(format(schedule.nextExecution()), row, 4);
                setValueAt(schedule.timeZone().getId(), row, 5);
                setValueAt(schedule.enabled() ? "Enabled" : "Disabled", row, 6);
            }
        }

        DeviceSchedule getSchedule(int row) { return values.get(row); }
        @Override public boolean isCellEditable(int row, int column) { return false; }

        private String repeatLabel(DeviceSchedule schedule) {
            if (schedule.repeatPattern() != com.smarthome.service.ScheduleRecurrence.Pattern.WEEKDAYS) {
                return schedule.repeatPattern().toString();
            }
            return schedule.weekdays().stream().sorted()
                .map(day -> day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()))
                .collect(java.util.stream.Collectors.joining(", "));
        }
    }
}
