package com.smarthome.ui;

import com.smarthome.dao.AutomationDao;
import com.smarthome.model.AutomationRule;
import com.smarthome.model.Device;
import com.smarthome.model.Room;
import com.smarthome.service.AutomationRuleService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Font;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

final class AutomationDialog {
    private AutomationDialog() {}

    static Optional<AutomationRuleService.Form> show(Component parent, List<Room> rooms,
            List<Device> devices, AutomationRule existing) {
        if (devices.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Create a device before defining an automation rule.",
                "No target devices", JOptionPane.INFORMATION_MESSAGE);
            return Optional.empty();
        }
        if (rooms.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Create a room before defining a sensor-based rule.",
                "No rooms", JOptionPane.INFORMATION_MESSAGE);
            return Optional.empty();
        }

        JDialog dialog = new JDialog(javax.swing.SwingUtilities.getWindowAncestor(parent),
            existing == null ? "Create automation rule" : "Edit automation rule",
            Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(0, 12));
        JPanel form = UiStyle.formPanel();
        JTextField name = new JTextField(existing == null ? "" : existing.name(), 22);
        JComboBox<AutomationRule.Condition> condition =
            new JComboBox<>(AutomationRule.Condition.values());
        if (existing != null) condition.setSelectedItem(existing.condition());
        JComboBox<RoomChoice> room = new JComboBox<>(rooms.stream().map(RoomChoice::new).toArray(RoomChoice[]::new));
        if (existing != null && existing.sensorRoomId() != null) {
            selectRoom(room, existing.sensorRoomId());
        }
        JComboBox<DeviceChoice> target =
            new JComboBox<>(devices.stream().map(DeviceChoice::new).toArray(DeviceChoice[]::new));
        if (existing != null) selectDevice(target, existing.targetDeviceId());
        JTextField threshold = new JTextField(existing == null || existing.threshold() == null
            ? "28" : existing.threshold().toPlainString(), 22);
        JComboBox<String> action = new JComboBox<>(new String[]{"ON", "OFF"});
        if (existing != null) action.setSelectedItem(existing.action());
        JTextField scheduledTime = new JTextField(existing == null || existing.scheduledTime() == null
            ? "20:00" : existing.scheduledTime().toString(), 22);
        JTextField cooldown = new JTextField(existing == null ? "300"
            : Integer.toString(existing.cooldownSeconds()), 22);
        JCheckBox enabled = new JCheckBox("Rule enabled", existing == null || existing.enabled());
        enabled.setOpaque(false);
        enabled.setForeground(UiStyle.TEXT);

        UiStyle.addFormField(form, 0, "Rule name", name);
        UiStyle.addFormField(form, 1, "Condition", condition);
        UiStyle.addFormField(form, 2, "Sensor room", room);
        UiStyle.addFormField(form, 3, "Threshold (temperature °C / humidity %)", threshold);
        UiStyle.addFormField(form, 4, "Target device", target);
        UiStyle.addFormField(form, 5, "Action", action);
        UiStyle.addFormField(form, 6, "Local time (HH:mm)", scheduledTime);
        UiStyle.addFormField(form, 7, "Cooldown (seconds)", cooldown);
        UiStyle.addFormField(form, 8, "Timezone", new JLabel(ZoneId.systemDefault().getId()));
        UiStyle.addFormField(form, 9, "Enabled", enabled);

        Runnable updateFields = () -> {
            AutomationRule.Condition selected = (AutomationRule.Condition) condition.getSelectedItem();
            boolean sensor = selected != null && selected.usesSensor();
            boolean thresholdCondition = selected == AutomationRule.Condition.TEMPERATURE_ABOVE
                || selected == AutomationRule.Condition.HUMIDITY_ABOVE;
            room.setEnabled(sensor);
            threshold.setEnabled(thresholdCondition);
            scheduledTime.setEnabled(selected == AutomationRule.Condition.SCHEDULED_TIME);
            action.setEnabled(selected != AutomationRule.Condition.MOTION_DETECTED);
            if (selected == AutomationRule.Condition.MOTION_DETECTED) action.setSelectedItem("ON");
        };
        condition.addActionListener(event -> updateFields.run());
        updateFields.run();

        JLabel title = new JLabel(existing == null ? "Define a safe, typed device rule"
            : "Update the rule and its trigger condition");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(UiStyle.TEXT);
        JPanel footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 8));
        footer.setOpaque(false);
        javax.swing.JButton cancel = UiStyle.button("Cancel", false);
        javax.swing.JButton save = UiStyle.button(existing == null ? "Create rule" : "Save rule", true);
        footer.add(cancel);
        footer.add(save);
        dialog.add(title, BorderLayout.NORTH);
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);

        AutomationRuleService.Form[] result = new AutomationRuleService.Form[1];
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                String ruleName = name.getText().trim();
                if (ruleName.isEmpty() || ruleName.length() > 100) {
                    throw new IllegalArgumentException("Rule name is required and must be at most 100 characters.");
                }
                AutomationRule.Condition selected = (AutomationRule.Condition) condition.getSelectedItem();
                if (selected == null) throw new IllegalArgumentException("Choose a condition.");
                RoomChoice selectedRoom = (RoomChoice) room.getSelectedItem();
                DeviceChoice selectedDevice = (DeviceChoice) target.getSelectedItem();
                if (selectedDevice == null) throw new IllegalArgumentException("Choose a target device.");
                BigDecimal selectedThreshold = null;
                if (selected == AutomationRule.Condition.TEMPERATURE_ABOVE
                        || selected == AutomationRule.Condition.HUMIDITY_ABOVE) {
                    selectedThreshold = new BigDecimal(threshold.getText().trim());
                }
                Integer roomId = selected.usesSensor()
                    ? selectedRoom == null ? null : selectedRoom.room().id() : null;
                LocalTime time = selected == AutomationRule.Condition.SCHEDULED_TIME
                    ? LocalTime.parse(scheduledTime.getText().trim()) : null;
                int cooldownSeconds = Integer.parseInt(cooldown.getText().trim());
                String selectedAction = (String) action.getSelectedItem();
                result[0] = new AutomationRuleService.Form(ruleName, enabled.isSelected(), selected,
                    selectedThreshold, roomId, selectedDevice.device().getId(), selectedAction, time,
                    ZoneId.systemDefault(), cooldownSeconds);
                dialog.dispose();
            } catch (NumberFormatException | DateTimeParseException ex) {
                validation(dialog, "Enter a valid numeric threshold/cooldown and time in HH:mm format.", threshold);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Check rule details",
                    JOptionPane.WARNING_MESSAGE);
            }
        });
        dialog.getRootPane().setDefaultButton(save);
        dialog.pack();
        dialog.setMinimumSize(new java.awt.Dimension(520, dialog.getHeight()));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return Optional.ofNullable(result[0]);
    }

    private static void selectRoom(JComboBox<RoomChoice> combo, int id) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).room().id() == id) combo.setSelectedIndex(i);
        }
    }

    private static void selectDevice(JComboBox<DeviceChoice> combo, int id) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).device().getId() == id) combo.setSelectedIndex(i);
        }
    }

    private static void validation(JDialog dialog, String message, Component focus) {
        JOptionPane.showMessageDialog(dialog, message, "Check rule details", JOptionPane.WARNING_MESSAGE);
        focus.requestFocusInWindow();
    }

    private record RoomChoice(Room room) {
        @Override public String toString() { return room.name(); }
    }

    private record DeviceChoice(Device device) {
        @Override public String toString() { return device.getName() + " · " + device.getType(); }
    }
}
