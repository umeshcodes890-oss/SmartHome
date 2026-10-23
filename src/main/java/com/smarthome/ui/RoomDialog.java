package com.smarthome.ui;

import com.smarthome.model.Room;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

final class RoomDialog {
    private RoomDialog() {}

    static Optional<RoomForm> show(java.awt.Component parent, String title, Room existing) {
        JDialog dialog = new JDialog(javax.swing.SwingUtilities.getWindowAncestor(parent), title,
            Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(0, 12));
        JPanel content = UiStyle.formPanel();
        JTextField name = new JTextField(existing == null ? "" : existing.name(), 22);
        JTextField floor = new JTextField(existing == null ? "1" : Integer.toString(existing.floorNumber()), 22);
        UiStyle.addFormField(content, 0, "Room name", name);
        UiStyle.addFormField(content, 1, "Floor number", floor);

        JLabel heading = new JLabel(existing == null ? "Create a room" : "Update room details");
        heading.setFont(new Font("SansSerif", Font.BOLD, 17));
        heading.setForeground(UiStyle.TEXT);
        JPanel footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 10));
        footer.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
        JButton cancel = UiStyle.button("Cancel", false);
        JButton save = UiStyle.button(existing == null ? "Add room" : "Save changes", true);
        footer.add(cancel);
        footer.add(save);
        dialog.add(heading, BorderLayout.NORTH);
        dialog.add(content, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);

        RoomForm[] result = new RoomForm[1];
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            String roomName = name.getText().trim();
            if (roomName.isEmpty() || roomName.length() > 80) {
                validation(dialog, "Enter a room name of 1 to 80 characters.", name);
                return;
            }
            try {
                int floorNumber = Integer.parseInt(floor.getText().trim());
                if (floorNumber < 0 || floorNumber > 999) {
                    validation(dialog, "Floor number must be between 0 and 999.", floor);
                    return;
                }
                result[0] = new RoomForm(roomName, floorNumber);
                dialog.dispose();
            } catch (NumberFormatException ex) {
                validation(dialog, "Enter a whole-number floor from 0 to 999.", floor);
            }
        });
        dialog.getRootPane().setDefaultButton(save);
        dialog.pack();
        dialog.setMinimumSize(new java.awt.Dimension(410, dialog.getHeight()));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return Optional.ofNullable(result[0]);
    }

    private static void validation(JDialog dialog, String message, JTextField field) {
        JOptionPane.showMessageDialog(dialog, message, "Check room details", JOptionPane.WARNING_MESSAGE);
        field.requestFocusInWindow();
        field.selectAll();
    }

    record RoomForm(String name, int floorNumber) {}
}
