package com.smarthome.ui;

import com.smarthome.model.Device;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

final class DeviceDialog {
    private DeviceDialog() {}

    static Optional<DeviceForm> show(Component parent, String title, List<String> roomNames, Device existing) {
        if (roomNames.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Add a room to the database before managing devices.",
                "No rooms available", JOptionPane.INFORMATION_MESSAGE);
            return Optional.empty();
        }

        JDialog dialog = new JDialog(javax.swing.SwingUtilities.getWindowAncestor(parent), title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(0, 14));
        JPanel content = UiStyle.formPanel();
        JTextField name = new JTextField(existing == null ? "" : existing.getName(), 24);
        JTextField type = new JTextField(existing == null ? "" : existing.getType(), 24);
        JComboBox<String> room = new JComboBox<>(roomNames.toArray(String[]::new));
        if (existing != null) room.setSelectedItem(existing.getRoom());
        JTextField power = new JTextField(existing == null ? "10" : existing.getPowerWatts().toPlainString(), 24);
        UiStyle.addFormField(content, 0, "Device name", name);
        UiStyle.addFormField(content, 1, "Device type", type);
        UiStyle.addFormField(content, 2, "Room", room);
        UiStyle.addFormField(content, 3, "Rated power (W)", power);

        JLabel heading = new JLabel(existing == null ? "Connect a device" : "Edit device details");
        heading.setFont(new Font("SansSerif", Font.BOLD, 17));
        heading.setForeground(UiStyle.TEXT);
        JPanel footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 10));
        footer.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
        javax.swing.JButton cancel = UiStyle.button("Cancel", false);
        javax.swing.JButton save = UiStyle.button(existing == null ? "Add device" : "Save changes", true);
        footer.add(cancel);
        footer.add(save);
        dialog.add(heading, BorderLayout.NORTH);
        dialog.add(content, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);

        DeviceForm[] result = new DeviceForm[1];
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            String deviceName = name.getText().trim();
            String deviceType = type.getText().trim();
            String selectedRoom = (String) room.getSelectedItem();
            if (deviceName.isEmpty() || deviceName.length() > 100) {
                showValidation(dialog, "Enter a device name of 1 to 100 characters.", name);
                return;
            }
            if (deviceType.isEmpty() || deviceType.length() > 40) {
                showValidation(dialog, "Enter a device type of 1 to 40 characters.", type);
                return;
            }
            if (selectedRoom == null) {
                showValidation(dialog, "Select a room.", room);
                return;
            }
            try {
                BigDecimal watts = new BigDecimal(power.getText().trim());
                if (watts.signum() < 0 || watts.scale() > 2 || watts.precision() > 8) {
                    showValidation(dialog, "Power must be between 0 and 999999.99 watts with at most two decimal places.", power);
                    return;
                }
                result[0] = new DeviceForm(deviceName, deviceType, selectedRoom, watts);
                dialog.dispose();
            } catch (NumberFormatException ex) {
                showValidation(dialog, "Enter a valid power value in watts.", power);
            }
        });
        dialog.getRootPane().setDefaultButton(save);
        dialog.pack();
        dialog.setMinimumSize(new java.awt.Dimension(430, dialog.getHeight()));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return Optional.ofNullable(result[0]);
    }

    private static void showValidation(JDialog dialog, String message, Component field) {
        JOptionPane.showMessageDialog(dialog, message, "Check device details", JOptionPane.WARNING_MESSAGE);
        field.requestFocusInWindow();
    }

    record DeviceForm(String name, String type, String roomName, BigDecimal powerWatts) {}
}
