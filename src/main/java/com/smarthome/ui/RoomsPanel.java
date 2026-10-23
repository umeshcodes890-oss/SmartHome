package com.smarthome.ui;

import com.smarthome.model.Device;
import com.smarthome.model.Room;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

final class RoomsPanel extends JPanel {
    private final JPanel roomGrid = new JPanel(new GridLayout(0, 2, 14, 14));
    private final JPanel roomCards = new JPanel(new BorderLayout());
    private final JPanel detail = UiStyle.card();
    private final JLabel detailTitle = new JLabel("Select a room to view its devices");
    private final JLabel detailStatus = new JLabel("Room device details are loaded from MySQL.");
    private final DefaultTableModel deviceModel = new DefaultTableModel(
        new Object[]{"Device", "Type", "Status", "Power (W)"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final Consumer<Room> onSelect;
    private final Consumer<Room> onEdit;
    private final Consumer<Room> onDelete;
    private final JButton addRoom = UiStyle.button("＋ Add room", true);
    private Room selectedRoom;
    private boolean adminMode = true;

    RoomsPanel(Runnable onAdd, Consumer<Room> onSelect, Consumer<Room> onEdit, Consumer<Room> onDelete) {
        this.onSelect = onSelect;
        this.onEdit = onEdit;
        this.onDelete = onDelete;
        setLayout(new BorderLayout(0, 16));
        setBackground(UiStyle.WORKSPACE);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JPanel headingText = new JPanel(new GridLayout(0, 1, 0, 5));
        headingText.setOpaque(false);
        JLabel title = new JLabel("Your rooms");
        title.setFont(new Font("SansSerif", Font.BOLD, 19));
        title.setForeground(UiStyle.TEXT);
        JLabel subtitle = new JLabel("Organize devices by room and floor.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(UiStyle.MUTED);
        headingText.add(title);
        headingText.add(subtitle);
        heading.add(headingText, BorderLayout.WEST);
        addRoom.addActionListener(event -> onAdd.run());
        heading.add(addRoom, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);

        roomGrid.setOpaque(false);
        JScrollPane roomScroll = new JScrollPane(roomGrid);
        roomScroll.setBorder(BorderFactory.createEmptyBorder());
        roomScroll.getViewport().setOpaque(false);
        roomScroll.setOpaque(false);
        roomScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        roomScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        roomCards.add(roomScroll, BorderLayout.CENTER);
        roomCards.setMinimumSize(new java.awt.Dimension(0, 170));
        roomCards.setPreferredSize(new java.awt.Dimension(600, 270));

        detail.setLayout(new BorderLayout(0, 10));
        JPanel detailHeading = new JPanel(new BorderLayout());
        detailHeading.setOpaque(false);
        detailTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        detailTitle.setForeground(UiStyle.TEXT);
        detailStatus.setFont(new Font("SansSerif", Font.PLAIN, 11));
        detailStatus.setForeground(UiStyle.MUTED);
        JPanel detailLabels = new JPanel(new GridLayout(0, 1, 0, 4));
        detailLabels.setOpaque(false);
        detailLabels.add(detailTitle);
        detailLabels.add(detailStatus);
        detailHeading.add(detailLabels, BorderLayout.WEST);
        detail.add(detailHeading, BorderLayout.NORTH);

        JTable deviceTable = new JTable(deviceModel);
        UiStyle.styleTable(deviceTable);
        JScrollPane deviceScroll = new JScrollPane(deviceTable);
        deviceScroll.setBorder(BorderFactory.createLineBorder(UiStyle.BORDER));
        deviceScroll.getViewport().setBackground(UiStyle.SURFACE);
        detail.add(deviceScroll, BorderLayout.CENTER);
        detail.setMinimumSize(new java.awt.Dimension(0, 170));
        detail.setPreferredSize(new java.awt.Dimension(600, 230));

        JSplitPane content = new JSplitPane(JSplitPane.VERTICAL_SPLIT, roomCards, detail);
        content.setResizeWeight(0.55);
        content.setDividerSize(9);
        content.setContinuousLayout(true);
        content.setBorder(null);
        content.setOpaque(false);
        content.setBackground(UiStyle.WORKSPACE);
        add(content, BorderLayout.CENTER);
        javax.swing.SwingUtilities.invokeLater(() ->
            content.setDividerLocation(Math.max(180, getHeight() * 0.54)));
    }

    void setAdminMode(boolean admin) {
        adminMode = admin;
        addRoom.setVisible(admin);
        setRooms(java.util.List.of());
    }

    void setRooms(List<Room> rooms) {
        Integer selectedId = selectedRoom == null ? null : selectedRoom.id();
        roomGrid.removeAll();
        if (rooms.isEmpty()) {
            roomGrid.add(message(adminMode ? "No rooms found. Add a room to get started."
                : "No rooms are available yet. Ask an administrator to add a room."));
            selectedRoom = null;
            setSelectedRoom(null, List.of());
        } else {
            selectedRoom = null;
            for (Room room : rooms) {
                roomGrid.add(roomCard(room));
                if (selectedId != null && selectedId == room.id()) selectedRoom = room;
            }

            if (selectedId != null && selectedRoom == null) {
                setSelectedRoom(null, List.of());
            }
        }
        roomGrid.revalidate();
        roomGrid.repaint();
    }

    void setLoading() {
        roomGrid.removeAll();
        roomGrid.add(message("Loading rooms from MySQL…"));
        selectedRoom = null;
        setSelectedRoom(null, List.of());
        roomGrid.revalidate();
        roomGrid.repaint();
    }

    void setLoadError(String details) {
        roomGrid.removeAll();
        roomGrid.add(message(details));
        selectedRoom = null;
        setSelectedRoom(null, List.of());
        roomGrid.revalidate();
        roomGrid.repaint();
    }

    void showLoadingDevices(Room room) {
        selectedRoom = room;
        detailTitle.setText(room.name() + " devices");
        detailStatus.setText("Loading devices from MySQL…");
        deviceModel.setRowCount(0);
        deviceModel.addRow(new Object[]{"Loading room devices…", "", "", ""});
    }

    void setSelectedRoom(Room room, List<Device> devices) {
        selectedRoom = room;
        deviceModel.setRowCount(0);
        if (room == null) {
            detailTitle.setText("Select a room to view its devices");
            detailStatus.setText("Room device details are loaded from MySQL.");
            deviceModel.addRow(new Object[]{"Choose a room card to view its devices.", "", "", ""});
            return;
        }
        detailTitle.setText(room.name() + " devices");
        detailStatus.setText(room.deviceCount() + " total · " + room.activeDeviceCount() + " currently on");
        if (devices.isEmpty()) {
            deviceModel.addRow(new Object[]{"No devices assigned to this room", "", "", ""});
        } else {
            for (Device device : devices) {
                deviceModel.addRow(new Object[]{device.getName(), device.getType(), device.getStatus(), device.getPowerWatts()});
            }
        }
    }

    private JPanel roomCard(Room room) {
        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 12));
        card.setMinimumSize(new java.awt.Dimension(250, 152));
        card.setPreferredSize(new java.awt.Dimension(360, 170));
        JPanel titleLine = new JPanel(new BorderLayout(8, 0));
        titleLine.setOpaque(false);
        JLabel icon = new JLabel("⌂");
        icon.setFont(new Font("SansSerif", Font.BOLD, 24));
        icon.setForeground(UiStyle.BLUE);
        JLabel name = new JLabel(room.name());
        name.setFont(new Font("SansSerif", Font.BOLD, 15));
        name.setForeground(UiStyle.TEXT);
        titleLine.add(icon, BorderLayout.WEST);
        titleLine.add(name, BorderLayout.CENTER);
        card.add(titleLine, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(1, 2, 8, 0));
        stats.setOpaque(false);
        stats.add(stat("FLOOR", room.floorNumber() == 0 ? "Ground" : Integer.toString(room.floorNumber())));
        stats.add(stat("DEVICES", room.deviceCount() + " total · " + room.activeDeviceCount() + " on"));
        card.add(stats, BorderLayout.CENTER);

        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        JButton view = UiStyle.button("View devices", true);
        JButton edit = UiStyle.button("Edit", false);
        JButton delete = UiStyle.button("Delete", false);
        edit.setVisible(adminMode);
        delete.setVisible(adminMode);
        view.addActionListener(event -> onSelect.accept(room));
        edit.addActionListener(event -> onEdit.accept(room));
        delete.addActionListener(event -> onDelete.accept(room));
        actions.add(view);
        actions.add(edit);
        actions.add(delete);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JPanel stat(String label, String value) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.setOpaque(false);
        JLabel caption = new JLabel(label);
        caption.setFont(new Font("SansSerif", Font.BOLD, 9));
        caption.setForeground(UiStyle.MUTED);
        JLabel text = new JLabel(value);
        text.setFont(new Font("SansSerif", Font.BOLD, 12));
        text.setForeground(UiStyle.TEXT);
        panel.add(caption);
        panel.add(text);
        return panel;
    }

    static JPanel message(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiStyle.BORDER), BorderFactory.createEmptyBorder(20, 22, 20, 22)));
        JLabel label = new JLabel(text);
        label.setForeground(UiStyle.MUTED);
        label.setFont(new Font("SansSerif", Font.PLAIN, 13));
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }
}
