package com.smarthome.ui;

import com.smarthome.model.Device;
import com.smarthome.model.DeviceSchedule;
import com.smarthome.service.ScheduleRecurrence;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;

final class ScheduleDialog {
    private static final DayOfWeek[] WEEK_ORDER = {
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    };

    private ScheduleDialog() {}

    static Optional<Form> show(Component parent, List<Device> devices, DeviceSchedule existing) {
        if (devices.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Add a device before creating a schedule.",
                "No devices available", JOptionPane.INFORMATION_MESSAGE);
            return Optional.empty();
        }
        ZoneId zone = existing == null ? ZoneId.systemDefault() : existing.timeZone();
        JDialog dialog = new JDialog(javax.swing.SwingUtilities.getWindowAncestor(parent),
            existing == null ? "Create schedule" : "Edit schedule", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(0, 10));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(18, 20, 8, 20));

        JComboBox<DeviceOption> device = new JComboBox<>();
        for (Device item : devices) device.addItem(new DeviceOption(item.getId(), item.getName()));
        JComboBox<String> action = new JComboBox<>(new String[]{"ON", "OFF"});
        JComboBox<ScheduleRecurrence.Pattern> repeat = new JComboBox<>(ScheduleRecurrence.Pattern.values());
        JSpinner date = new JSpinner(new SpinnerDateModel());
        date.setEditor(new JSpinner.DateEditor(date, "yyyy-MM-dd"));
        JSpinner time = new JSpinner(new SpinnerDateModel());
        time.setEditor(new JSpinner.DateEditor(time, "HH:mm"));
        ((JSpinner.DateEditor) date.getEditor()).getFormat()
            .setTimeZone(java.util.TimeZone.getTimeZone(zone));
        ((JSpinner.DateEditor) time.getEditor()).getFormat()
            .setTimeZone(java.util.TimeZone.getTimeZone(zone));
        JCheckBox[] weekdays = new JCheckBox[7];
        JPanel weekdayPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 0));
        for (int index = 0; index < WEEK_ORDER.length; index++) {
            weekdays[index] = new JCheckBox(WEEK_ORDER[index].getDisplayName(
                java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()));
            weekdayPanel.add(weekdays[index]);
        }
        javax.swing.JTextField name = new javax.swing.JTextField(existing == null ? "" : existing.scheduleName(), 22);

        addField(form, 0, "Schedule name", name);
        addField(form, 1, "Device", device);
        addField(form, 2, "Action", action);
        addField(form, 3, "Start date", date);
        addField(form, 4, "Local time", time);
        addField(form, 5, "Repeat", repeat);
        addField(form, 6, "Weekdays", weekdayPanel);
        JLabel timezone = new JLabel("Times use " + zone.getId() + " local time");
        timezone.setForeground(UiStyle.MUTED);
        timezone.setFont(new Font("SansSerif", Font.PLAIN, 11));
        GridBagConstraints note = constraints(7, 0);
        note.gridwidth = 2;
        form.add(timezone, note);

        if (existing != null) {
            for (int index = 0; index < device.getItemCount(); index++) {
                if (device.getItemAt(index).id() == existing.deviceId()) device.setSelectedIndex(index);
            }
            action.setSelectedItem(existing.action());
            repeat.setSelectedItem(existing.repeatPattern());
            date.setValue(java.util.Date.from(existing.startDate().atStartOfDay(zone).toInstant()));
            time.setValue(java.util.Date.from(LocalDate.now(zone).atTime(existing.localTime()).atZone(zone).toInstant()));
            name.setText(existing.scheduleName());
            for (int index = 0; index < WEEK_ORDER.length; index++) {
                weekdays[index].setSelected(existing.weekdays().contains(WEEK_ORDER[index]));
            }
        } else {
            java.time.LocalDateTime defaultRun = java.time.LocalDateTime.now(zone)
                .plusMinutes(5).withSecond(0).withNano(0);
            date.setValue(java.util.Date.from(defaultRun.toLocalDate().atStartOfDay(zone).toInstant()));
            time.setValue(java.util.Date.from(defaultRun.atZone(zone).toInstant()));
            repeat.setSelectedItem(ScheduleRecurrence.Pattern.ONCE);
        }

        repeat.addActionListener(event -> {
            boolean showWeekdays = repeat.getSelectedItem() == ScheduleRecurrence.Pattern.WEEKDAYS;
            weekdayPanel.setEnabled(showWeekdays);
            for (JCheckBox checkbox : weekdays) checkbox.setEnabled(showWeekdays);
        });
        repeat.setSelectedItem(existing == null ? ScheduleRecurrence.Pattern.ONCE : existing.repeatPattern());
        boolean showWeekdays = repeat.getSelectedItem() == ScheduleRecurrence.Pattern.WEEKDAYS;
        weekdayPanel.setEnabled(showWeekdays);
        for (JCheckBox checkbox : weekdays) checkbox.setEnabled(showWeekdays);

        JPanel footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 8));
        footer.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        JButton cancel = UiStyle.button("Cancel", false);
        JButton save = UiStyle.button(existing == null ? "Create schedule" : "Save changes", true);
        footer.add(cancel);
        footer.add(save);
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);

        Form[] result = new Form[1];
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            String scheduleName = name.getText().trim();
            if (scheduleName.isEmpty() || scheduleName.length() > 120) {
                validation(dialog, "Enter a schedule name of 1 to 120 characters.", name);
                return;
            }
            Set<DayOfWeek> selectedDays = EnumSet.noneOf(DayOfWeek.class);
            for (int index = 0; index < WEEK_ORDER.length; index++) {
                if (weekdays[index].isSelected()) selectedDays.add(WEEK_ORDER[index]);
            }
            ScheduleRecurrence.Pattern selectedPattern =
                (ScheduleRecurrence.Pattern) repeat.getSelectedItem();
            if (selectedPattern == ScheduleRecurrence.Pattern.WEEKDAYS && selectedDays.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Choose at least one weekday.", "Select weekdays",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            DeviceOption selectedDevice = (DeviceOption) device.getSelectedItem();
            LocalDate start = ((java.util.Date) date.getValue()).toInstant().atZone(zone).toLocalDate();
            LocalTime localTime = ((java.util.Date) time.getValue()).toInstant().atZone(zone).toLocalTime()
                .withSecond(0).withNano(0);
            if (existing == null && selectedPattern == ScheduleRecurrence.Pattern.ONCE
                    && !ScheduleRecurrence.resolve(start.atTime(localTime), zone).isAfter(java.time.Instant.now())) {
                validation(dialog, "Choose a future date and time for a one-time schedule.", date);
                return;
            }
            result[0] = new Form(selectedDevice.id(), scheduleName, (String) action.getSelectedItem(),
                selectedPattern, start, localTime, zone, selectedDays);
            dialog.dispose();
        });
        dialog.getRootPane().setDefaultButton(save);
        dialog.pack();
        dialog.setMinimumSize(new java.awt.Dimension(500, dialog.getHeight()));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return Optional.ofNullable(result[0]);
    }

    private static void addField(JPanel panel, int row, String label, Component field) {
        GridBagConstraints caption = constraints(row, 0);
        caption.anchor = GridBagConstraints.LINE_START;
        caption.insets = new Insets(7, 0, 7, 12);
        JLabel text = new JLabel(label);
        text.setForeground(UiStyle.MUTED);
        panel.add(text, caption);
        GridBagConstraints input = constraints(row, 1);
        input.weightx = 1;
        input.fill = GridBagConstraints.HORIZONTAL;
        input.insets = new Insets(7, 0, 7, 0);
        field.setFont(new Font("SansSerif", Font.PLAIN, 12));
        panel.add(field, input);
    }

    private static GridBagConstraints constraints(int row, int column) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        return constraints;
    }

    private static void validation(JDialog dialog, String message, Component component) {
        JOptionPane.showMessageDialog(dialog, message, "Check schedule details", JOptionPane.WARNING_MESSAGE);
        component.requestFocusInWindow();
    }

    record DeviceOption(int id, String name) {
        @Override public String toString() { return name; }
    }

    record Form(int deviceId, String name, String action, ScheduleRecurrence.Pattern pattern,
            LocalDate startDate, LocalTime localTime, ZoneId zone, Set<DayOfWeek> weekdays) {
        Form { weekdays = Set.copyOf(weekdays); }
    }
}
