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
        JLabel caption = new JLabel("Schedules run while this application is open");
        caption.setForeground(UiStyle.MUTED);
        caption.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton add = UiStyle.button("＋  Create schedule", true);
        JButton refresh = UiStyle.button("Refresh", false);
        actions.add(add);
        actions.add(edit);
        actions.add(toggle);
        actions.add(delete);
        actions.add(refresh);
        toolbar.add(caption, BorderLayout.NORTH);
        toolbar.add(actions, BorderLayout.SOUTH);

        configureTable(schedules);
        configureTable(history);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Schedules", new JScrollPane(schedules));
        tabs.addTab("Execution history", new JScrollPane(history));
        card.add(toolbar, BorderLayout.NORTH);
        card.add(tabs, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        add.addActionListener(event -> onAdd.run());
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

    void setSchedules(List<DeviceSchedule> values) {
        schedules.clearSelection();
        scheduleModel.setSchedules(values);
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
        table.setRowHeight(34);
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.setForeground(UiStyle.TEXT);
        table.setBackground(UiStyle.SURFACE);
        table.setGridColor(UiStyle.BORDER);
        table.setSelectionBackground(UiStyle.BLUE_PALE);
        table.setSelectionForeground(UiStyle.TEXT);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        table.getTableHeader().setForeground(UiStyle.MUTED);
        table.getTableHeader().setBackground(UiStyle.TABLE_HEADER);
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
