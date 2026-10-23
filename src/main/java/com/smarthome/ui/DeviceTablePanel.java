package com.smarthome.ui;

import com.smarthome.model.Device;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

final class DeviceTablePanel extends JPanel {
    private final DefaultTableModel model = new DefaultTableModel(
        new Object[]{"ID", "Icon", "Device", "Type", "Room", "Status", "Power (W)"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return column == 5; }
        @Override public Class<?> getColumnClass(int column) {
            return column == 1 ? DeviceTypeIcon.class : Object.class;
        }
    };
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = new JTextField(18);
    private final JComboBox<String> roomFilter = new JComboBox<>(new String[]{"All rooms"});
    private final JComboBox<String> typeFilter = new JComboBox<>(new String[]{"All types"});
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[]{"All statuses", "ON", "OFF"});
    private final JLabel emptyMessage = new JLabel("No devices match these filters.", SwingConstants.CENTER);
    private String loadState;
    private final java.awt.CardLayout tableLayout = new java.awt.CardLayout();
    private final JPanel tableContainer = new JPanel(tableLayout);
    private final Consumer<Device> onToggle;
    private final Consumer<Device> onEdit;
    private final Consumer<Device> onDelete;
    private final JButton addButton = UiStyle.button("＋ Add device", true);
    private final JButton editButton = UiStyle.button("Edit selected", false);
    private final JButton deleteButton = UiStyle.button("Delete selected", false);
    private List<Device> devices = List.of();
    private boolean updatingOptions;

    DeviceTablePanel(Runnable onRefresh, Runnable onAdd, Consumer<Device> onToggle,
            Consumer<Device> onEdit, Consumer<Device> onDelete, Consumer<String> onBulkStatus) {
        this.onToggle = onToggle;
        this.onEdit = onEdit;
        this.onDelete = onDelete;
        setLayout(new BorderLayout(0, 14));
        setBackground(UiStyle.WORKSPACE);

        JPanel heading = new JPanel(new BorderLayout(12, 10));
        heading.setOpaque(false);
        JPanel titleBlock = new JPanel(new GridLayout(0, 1, 0, 5));
        titleBlock.setOpaque(false);
        JLabel title = new JLabel("Your devices");
        title.setFont(new Font("SansSerif", Font.BOLD, 19));
        title.setForeground(UiStyle.TEXT);
        JLabel subtitle = new JLabel("Search, filter, and manage connected devices.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(UiStyle.MUTED);
        titleBlock.add(title);
        titleBlock.add(subtitle);
        heading.add(titleBlock, BorderLayout.WEST);

        JPanel actions = new JPanel(new GridLayout(2, 0, 7, 7));
        actions.setOpaque(false);
        JButton refresh = UiStyle.button("Refresh", false);
        JButton allOn = UiStyle.button("All ON", false);
        JButton allOff = UiStyle.button("All OFF", false);
        refresh.addActionListener(event -> onRefresh.run());
        allOn.addActionListener(event -> onBulkStatus.accept("ON"));
        allOff.addActionListener(event -> onBulkStatus.accept("OFF"));
        editButton.addActionListener(event -> selectedDevice().ifPresent(onEdit));
        deleteButton.addActionListener(event -> selectedDevice().ifPresent(onDelete));
        addButton.addActionListener(event -> onAdd.run());
        actions.add(refresh);
        actions.add(allOn);
        actions.add(allOff);
        actions.add(editButton);
        actions.add(deleteButton);
        actions.add(addButton);
        heading.add(actions, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        JPanel filters = new JPanel(new GridLayout(1, 4, 10, 0));
        filters.setOpaque(false);
        styleFilter(search);
        search.setToolTipText("Search device name");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { applyFilters(); }
            @Override public void removeUpdate(DocumentEvent event) { applyFilters(); }
            @Override public void changedUpdate(DocumentEvent event) { applyFilters(); }
        });
        styleFilter(roomFilter);
        styleFilter(typeFilter);
        styleFilter(statusFilter);
        roomFilter.addActionListener(event -> { if (!updatingOptions) applyFilters(); });
        typeFilter.addActionListener(event -> { if (!updatingOptions) applyFilters(); });
        statusFilter.addActionListener(event -> applyFilters());
        filters.add(search);
        filters.add(roomFilter);
        filters.add(typeFilter);
        filters.add(statusFilter);
        body.add(filters, BorderLayout.NORTH);

        table.setRowSorter(sorter);
        UiStyle.styleTable(table);
        table.setRowHeight(48);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        table.getColumnModel().getColumn(0).setPreferredWidth(0);
        table.getColumnModel().getColumn(1).setMaxWidth(58);
        table.getColumnModel().getColumn(1).setCellRenderer(new IconRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(new StatusButtonRenderer());
        table.getColumnModel().getColumn(5).setCellEditor(new StatusButtonEditor());
        table.getColumnModel().getColumn(6).setCellRenderer(new RightAlignedRenderer());

        tableContainer.setBackground(UiStyle.SURFACE);
        tableContainer.setBorder(BorderFactory.createLineBorder(UiStyle.BORDER));
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        emptyMessage.setFont(new Font("SansSerif", Font.PLAIN, 13));
        emptyMessage.setForeground(UiStyle.MUTED);
        emptyMessage.setBorder(BorderFactory.createEmptyBorder(30, 12, 30, 12));
        JPanel emptyPanel = new JPanel(new BorderLayout());
        emptyPanel.setBackground(UiStyle.SURFACE);
        emptyPanel.add(emptyMessage, BorderLayout.CENTER);
        tableContainer.add(scrollPane, "results");
        tableContainer.add(emptyPanel, "empty");
        body.add(tableContainer, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    void setAdminMode(boolean admin) {
        editButton.setVisible(admin);
        deleteButton.setVisible(admin);
        addButton.setVisible(admin);
    }

    void setDevices(List<Device> newDevices) {
        loadState = null;
        devices = List.copyOf(newDevices);
        model.setRowCount(0);
        for (Device device : devices) {
            model.addRow(new Object[]{device.getId(), new DeviceTypeIcon(device.getType()), device.getName(),
                device.getType(), device.getRoom(), device.getStatus(), device.getPowerWatts()});
        }
        applyFilters();
    }

    void setLoading() {
        loadState = "Loading devices…";
        applyFilters();
    }

    void setLoadError(String message) {
        loadState = message;
        applyFilters();
    }

    void setRoomOptions(List<String> rooms) {
        String selected = (String) roomFilter.getSelectedItem();
        updatingOptions = true;
        roomFilter.removeAllItems();
        roomFilter.addItem("All rooms");
        for (String room : rooms) roomFilter.addItem(room);
        if (selected != null) roomFilter.setSelectedItem(selected);
        if (roomFilter.getSelectedIndex() < 0) roomFilter.setSelectedIndex(0);
        updatingOptions = false;
        applyFilters();
    }

    private void updateTypeOptions() {
        String selected = (String) typeFilter.getSelectedItem();
        updatingOptions = true;
        typeFilter.removeAllItems();
        typeFilter.addItem("All types");
        TreeSet<String> types = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Device device : devices) types.add(device.getType());
        for (String type : types) typeFilter.addItem(type);
        if (selected != null) typeFilter.setSelectedItem(selected);
        if (typeFilter.getSelectedIndex() < 0) typeFilter.setSelectedIndex(0);
        updatingOptions = false;
    }

    private void applyFilters() {
        updateTypeOptions();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        String room = (String) roomFilter.getSelectedItem();
        String type = (String) typeFilter.getSelectedItem();
        String status = (String) statusFilter.getSelectedItem();
        sorter.setRowFilter(new RowFilter<>() {
            @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                String name = String.valueOf(entry.getValue(2)).toLowerCase(Locale.ROOT);
                return name.contains(query)
                    && ("All rooms".equals(room) || room == null || room.equals(entry.getValue(4)))
                    && ("All types".equals(type) || type == null || type.equalsIgnoreCase(String.valueOf(entry.getValue(3))))
                    && ("All statuses".equals(status) || status == null || status.equals(entry.getValue(5)));
            }
        });
        emptyMessage.setText(loadState != null ? loadState : devices.isEmpty()
            ? "No devices found. Add a device to get started."
            : "No devices match the selected search and filters.");
        tableLayout.show(tableContainer, table.getRowCount() == 0 ? "empty" : "results");
    }

    private void styleFilter(java.awt.Component component) {
        component.setFont(new Font("SansSerif", Font.PLAIN, 12));
        component.setForeground(UiStyle.TEXT);
        component.setBackground(UiStyle.SURFACE);
        if (component instanceof JTextField field) {
            field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyle.BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        } else if (component instanceof JComboBox<?> combo) {
            combo.setBorder(BorderFactory.createLineBorder(UiStyle.BORDER));
        }
    }

    private static final class IconRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        @Override public java.awt.Component getTableCellRendererComponent(JTable source, Object value,
                boolean selected, boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(source, "", selected, focused, row, column);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setIcon(value instanceof Icon icon ? icon : null);
            label.setBorder(null);
            return label;
        }
    }

    private java.util.Optional<Device> selectedDevice() {
        int row = table.getSelectedRow();
        if (row < 0) {
            javax.swing.JOptionPane.showMessageDialog(this, "Select a device first.", "No device selected",
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return java.util.Optional.empty();
        }
        int modelRow = table.convertRowIndexToModel(row);
        int id = (int) model.getValueAt(modelRow, 0);
        return devices.stream().filter(device -> device.getId() == id).findFirst();
    }

    private Device deviceAtViewRow(int viewRow) {
        if (viewRow < 0 || viewRow >= table.getRowCount()) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        int id = (int) model.getValueAt(modelRow, 0);
        return devices.stream().filter(device -> device.getId() == id).findFirst().orElse(null);
    }

    private final class StatusButtonRenderer extends JButton implements TableCellRenderer {
        private static final long serialVersionUID = 1L;
        StatusButtonRenderer() {
            setOpaque(true);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
            setFont(new Font("SansSerif", Font.BOLD, 11));
        }
        @Override public java.awt.Component getTableCellRendererComponent(JTable source, Object value,
                boolean selected, boolean focused, int row, int column) {
            boolean isOn = "ON".equals(value);
            setText(isOn ? "ON  ·  Turn off" : "OFF  ·  Turn on");
            setForeground(isOn ? UiStyle.GREEN : UiStyle.MUTED);
            setBackground(isOn ? UiStyle.GREEN_PALE : UiStyle.TABLE_HEADER);
            return this;
        }
    }

    private final class StatusButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private static final long serialVersionUID = 1L;
        private final JButton button = UiStyle.button("Turn on", false);
        private Device editingDevice;

        StatusButtonEditor() {
            button.addActionListener((ActionEvent event) -> {
                Device target = editingDevice;
                fireEditingStopped();
                if (target != null) onToggle.accept(target);
            });
        }

        @Override public java.awt.Component getTableCellEditorComponent(JTable source, Object value,
                boolean selected, int row, int column) {
            editingDevice = deviceAtViewRow(row);
            boolean isOn = "ON".equals(value);
            button.setText(isOn ? "ON  ·  Turn off" : "OFF  ·  Turn on");
            button.setForeground(isOn ? UiStyle.GREEN : UiStyle.MUTED);
            button.setBackground(isOn ? UiStyle.GREEN_PALE : UiStyle.TABLE_HEADER);
            return button;
        }

        @Override public Object getCellEditorValue() {
            return editingDevice == null ? "OFF" : editingDevice.getStatus();
        }
    }

    private static final class RightAlignedRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        RightAlignedRenderer() { setHorizontalAlignment(SwingConstants.RIGHT); }
    }
}
