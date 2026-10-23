package com.smarthome.ui;

import com.smarthome.dao.ActivityLogDao;
import com.smarthome.model.ActivityLog;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

final class ActivityLogPanel extends JPanel {
    private static final DateTimeFormatter DISPLAY_TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Consumer<Filter> onApply;
    private final Supplier<Filter> filterSupplier;
    private final JTextField search = new JTextField(17);
    private final JTextField fromDate = new JTextField(10);
    private final JTextField throughDate = new JTextField(10);
    private final JComboBox<String> eventType = new JComboBox<>(new String[]{
        "All event types", "DEVICE_STATUS", "DEVICE_CREATED", "DEVICE_UPDATED",
        "DEVICE_DELETED", "SCHEDULE_EXECUTED", "OPERATION_FAILED"
    });
    private final JLabel pageLabel = new JLabel("Page 1");
    private final JLabel state = new JLabel("Activity data is loaded from MySQL.");
    private final JButton previous = UiStyle.button("Previous", false);
    private final JButton next = UiStyle.button("Next", false);
    private final LogTableModel tableModel = new LogTableModel();
    private int offset;
    private boolean hasNext;

    ActivityLogPanel(Consumer<Filter> onApply, Supplier<Filter> filterSupplier) {
        this.onApply = onApply;
        this.filterSupplier = filterSupplier;
        setLayout(new BorderLayout());
        setBackground(UiStyle.WORKSPACE);
        setBorder(BorderFactory.createEmptyBorder(22, 28, 24, 28));
        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 12));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filters.setOpaque(false);
        filters.add(label("Search"));
        filters.add(search);
        filters.add(label("From (yyyy-MM-dd)"));
        filters.add(fromDate);
        filters.add(label("Through"));
        filters.add(throughDate);
        filters.add(eventType);
        JButton apply = UiStyle.button("Apply filters", true);
        JButton clear = UiStyle.button("Clear", false);
        filters.add(apply);
        filters.add(clear);
        UiStyle.styleControl(search);
        UiStyle.styleControl(fromDate);
        UiStyle.styleControl(throughDate);
        UiStyle.styleControl(eventType);

        JTable table = new JTable(tableModel);
        UiStyle.styleTable(table);
        table.getColumnModel().getColumn(3).setCellRenderer(new OutcomeRenderer());
        JPanel paging = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        paging.setOpaque(false);
        paging.add(previous);
        paging.add(pageLabel);
        paging.add(next);
        card.add(filters, BorderLayout.NORTH);
        state.setForeground(UiStyle.MUTED);
        state.setFont(new Font("SansSerif", Font.PLAIN, 11));
        JPanel data = new JPanel(new BorderLayout(0, 8));
        data.setOpaque(false);
        data.add(state, BorderLayout.NORTH);
        data.add(new JScrollPane(table), BorderLayout.CENTER);
        card.add(data, BorderLayout.CENTER);
        card.add(paging, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);

        apply.addActionListener(event -> {
            try {
                Filter filter = readFilter();
                offset = 0;
                onApply.accept(filter);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid activity filters",
                    JOptionPane.WARNING_MESSAGE);
            }
        });
        clear.addActionListener(event -> {
            search.setText("");
            fromDate.setText("");
            throughDate.setText("");
            eventType.setSelectedIndex(0);
            offset = 0;
            onApply.accept(readFilter());
        });
        previous.addActionListener(event -> {
            if (offset >= ActivityLogDao.PAGE_SIZE) {
                offset -= ActivityLogDao.PAGE_SIZE;
                onApply.accept(filterSupplier.get());
            }
        });
        next.addActionListener(event -> {
            if (hasNext) {
                offset += ActivityLogDao.PAGE_SIZE;
                onApply.accept(filterSupplier.get());
            }
        });
    }

    Filter readFilter() {
        LocalDate from = parseDate(fromDate.getText(), "start");
        LocalDate through = parseDate(throughDate.getText(), "end");
        if (from != null && through != null && through.isBefore(from)) {
            throw new IllegalArgumentException("The end date cannot be before the start date.");
        }
        String selectedType = (String) eventType.getSelectedItem();
        if ("All event types".equals(selectedType)) selectedType = "ALL";
        return new Filter(search.getText().trim(), selectedType, from, through, offset);
    }

    void setPage(ActivityLogDao.Page page) {
        tableModel.setEntries(page.entries());
        state.setText(page.entries().isEmpty() ? "No activity matches the selected filters."
            : page.entries().size() + " activity event(s) loaded.");
        hasNext = page.hasNext();
        previous.setEnabled(offset > 0);
        next.setEnabled(hasNext);
        pageLabel.setText("Page " + (offset / ActivityLogDao.PAGE_SIZE + 1));
    }

    void setLoading() { state.setText("Loading activity logs…"); }
    void setLoadError(String message) { state.setText(message); }

    private static LocalDate parseDate(String value, String description) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Enter the " + description + " date as yyyy-MM-dd.");
        }
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UiStyle.MUTED);
        label.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return label;
    }

    record Filter(String search, String eventType, LocalDate from, LocalDate through, int offset) {}

    private static final class LogTableModel extends DefaultTableModel {
        LogTableModel() { super(new Object[]{"Event", "User ID", "Device", "Timestamp", "Outcome", "Details"}, 0); }
        @Override public boolean isCellEditable(int row, int column) { return false; }

        void setEntries(List<ActivityLog> entries) {
            setRowCount(0);
            for (ActivityLog entry : entries) {
                addRow(new Object[]{entry.eventType(), entry.userId() == null ? "System" : entry.userId(),
                    entry.deviceName() == null ? "—" : entry.deviceName(),
                    DISPLAY_TIME.format(entry.occurredAt()),
                    entry.outcome().name(), entry.details() == null ? "" : entry.details()});
            }
        }
    }

    private static final class OutcomeRenderer extends DefaultTableCellRenderer {
        @Override public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                table, value, selected, focused, row, column);
            String outcome = String.valueOf(value);
            label.setText(switch (outcome) {
                case "SUCCESS" -> "✓  SUCCESS";
                case "FAILED" -> "×  FAILED";
                case "MISSED" -> "◷  MISSED";
                default -> outcome;
            });
            label.setForeground(selected ? UiStyle.TEXT : switch (outcome) {
                case "SUCCESS" -> UiStyle.GREEN;
                case "FAILED" -> new java.awt.Color(170, 55, 50);
                default -> UiStyle.MUTED;
            });
            return label;
        }
    }
}