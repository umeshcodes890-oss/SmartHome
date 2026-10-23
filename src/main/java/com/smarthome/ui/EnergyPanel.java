package com.smarthome.ui;

import com.smarthome.model.EnergyReport;
import com.smarthome.model.PowerBreakdown;
import com.smarthome.model.Room;
import com.smarthome.service.EnergyAnalytics;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;

final class EnergyPanel extends JPanel {
    private final JSpinner fromDate = new JSpinner(new SpinnerDateModel());
    private final JSpinner toDate = new JSpinner(new SpinnerDateModel());
    private final JComboBox<EnergyAnalytics.Interval> interval =
        new JComboBox<>(EnergyAnalytics.Interval.values());
    private final JComboBox<RoomOption> room = new JComboBox<>();
    private final JTextField tariff = new JTextField(9);
    private final JLabel historyNote = new JLabel("Energy is estimated from rated watts and tracked ON time, not meter readings.");
    private final JLabel reportState = new JLabel("Energy report is based on tracked runtime estimates.");
    private final StatisticCard activePower = new StatisticCard("Active rated power", UiStyle.BLUE);
    private final StatisticCard usage = new StatisticCard("Estimated energy", UiStyle.GREEN);
    private final StatisticCard cost = new StatisticCard("Estimated cost", UiStyle.TEXT);
    private final EnergyChartPanel chart = new EnergyChartPanel();
    private final DefaultTableModel roomModel = breakdownModel();
    private final DefaultTableModel typeModel = breakdownModel();
    private final Consumer<EnergyFilter> onFilter;
    private final Consumer<BigDecimal> onSaveTariff;
    private EnergyReport report;
    private boolean initializing;

    EnergyPanel(Consumer<EnergyFilter> onFilter, Consumer<BigDecimal> onSaveTariff, Runnable onExport) {
        this.onFilter = onFilter;
        this.onSaveTariff = onSaveTariff;
        setLayout(new BorderLayout());
        setBackground(UiStyle.WORKSPACE);
        setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setOpaque(false);
        JPanel titleArea = new JPanel(new BorderLayout(0, 5));
        titleArea.setOpaque(false);
        JLabel title = new JLabel("Energy usage");
        title.setForeground(UiStyle.TEXT);
        title.setFont(new Font("SansSerif", Font.BOLD, 19));
        JLabel subtitle = new JLabel("Monitor active rated load and estimate energy from recorded runtime.");
        subtitle.setForeground(UiStyle.MUTED);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        titleArea.add(title, BorderLayout.NORTH);
        titleArea.add(subtitle, BorderLayout.SOUTH);
        reportState.setForeground(UiStyle.MUTED);
        reportState.setFont(new Font("SansSerif", Font.PLAIN, 11));
        JPanel titleStack = new JPanel(new BorderLayout(0, 4));
        titleStack.setOpaque(false);
        titleStack.add(titleArea, BorderLayout.NORTH);
        titleStack.add(reportState, BorderLayout.SOUTH);
        content.add(titleStack, BorderLayout.NORTH);

        JPanel main = new JPanel(new BorderLayout(0, 14));
        main.setOpaque(false);
        JPanel controls = new JPanel(new java.awt.GridLayout(2, 1, 0, 8));
        controls.setOpaque(false);
        JPanel filters = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        styleDate(fromDate);
        styleDate(toDate);
        styleControl(interval);
        styleControl(room);
        filters.add(caption("From"));
        filters.add(fromDate);
        filters.add(caption("To"));
        filters.add(toDate);
        filters.add(interval);
        filters.add(room);
        JButton export = UiStyle.button("Export CSV", false);
        export.addActionListener(event -> onExport.run());
        filters.add(export);
        JPanel tariffControls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        tariffControls.setOpaque(false);
        styleControl(tariff);
        tariffControls.add(caption("Electricity tariff / kWh"));
        tariffControls.add(tariff);
        tariffControls.add(new JLabel("Currency is set by your local tariff"));
        JButton saveTariff = UiStyle.button("Save tariff", false);
        saveTariff.addActionListener(event -> saveTariff());
        tariffControls.add(saveTariff);
        controls.add(filters);
        controls.add(tariffControls);

        JPanel cards = new JPanel(new java.awt.GridLayout(1, 3, 12, 0));
        cards.setOpaque(false);
        cards.add(activePower);
        cards.add(usage);
        cards.add(cost);
        JPanel chartCard = UiStyle.card();
        chartCard.setLayout(new BorderLayout(0, 8));
        JLabel chartTitle = new JLabel("Estimated energy consumption");
        chartTitle.setForeground(UiStyle.TEXT);
        chartTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        chartCard.add(chartTitle, BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);
        historyNote.setForeground(UiStyle.MUTED);
        historyNote.setFont(new Font("SansSerif", Font.PLAIN, 11));
        chartCard.add(historyNote, BorderLayout.SOUTH);

        JPanel breakdown = new JPanel(new java.awt.GridLayout(1, 2, 12, 0));
        breakdown.setOpaque(false);
        breakdown.add(breakdownCard("Active rated power by room", roomModel));
        breakdown.add(breakdownCard("Active rated power by device type", typeModel));
        main.add(controls, BorderLayout.NORTH);
        JPanel results = new JPanel(new BorderLayout(0, 14));
        results.setOpaque(false);
        results.add(cards, BorderLayout.NORTH);
        results.add(chartCard, BorderLayout.CENTER);
        results.add(breakdown, BorderLayout.SOUTH);
        main.add(results, BorderLayout.CENTER);
        content.add(main, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        ChangeListener dateListener = event -> changed();
        fromDate.addChangeListener(dateListener);
        toDate.addChangeListener(dateListener);
        interval.addActionListener(event -> changed());
        room.addActionListener(event -> { if (!initializing) changed(); });
        setDefaultDates();
    }

    void setRooms(List<Room> rooms) {
        initializing = true;
        RoomOption previous = (RoomOption) room.getSelectedItem();
        room.removeAllItems();
        room.addItem(new RoomOption(null, "All rooms"));
        for (Room item : rooms) room.addItem(new RoomOption(item.id(), item.name()));
        if (previous != null) {
            for (int i = 0; i < room.getItemCount(); i++) {
                if (java.util.Objects.equals(room.getItemAt(i).id(), previous.id())) {
                    room.setSelectedIndex(i);
                    break;
                }
            }
        }
        initializing = false;
    }

    void setReport(EnergyReport value) {
        report = value;
        reportState.setText(value.historyAvailable() ? "Report loaded · energy values are estimates."
            : "No historical runtime is available for this range.");
        activePower.setValue(value.activeRatedWatts().stripTrailingZeros().toPlainString() + " W");
        usage.setValue(value.historyAvailable()
            ? value.estimatedKilowattHours().stripTrailingZeros().toPlainString() + " kWh" : "No history");
        cost.setValue(!value.historyAvailable() ? "No history" : value.estimatedCost() == null ? "Set tariff"
            : value.estimatedCost().toPlainString());
        if (value.tariffPerKwh() != null) tariff.setText(value.tariffPerKwh().stripTrailingZeros().toPlainString());
        historyNote.setText(value.historyAvailable()
            ? "Estimate from rated watts × tracked ON runtime; history starts " + value.historyStartedAt()
            : "No tracked runtime in this date range. Estimates are not measured meter consumption.");
        chart.setBuckets(value.consumption(), value.historyAvailable());
        setBreakdown(roomModel, value.activeByRoom());
        setBreakdown(typeModel, value.activeByType());
    }

    void setLoading() { reportState.setText("Loading energy usage report…"); }
    void setLoadError(String message) { reportState.setText(message); }

    EnergyReport report() { return report; }

    EnergyFilter filter() {
        RoomOption selected = (RoomOption) room.getSelectedItem();
        return new EnergyFilter(toLocalDate(fromDate), toLocalDate(toDate),
            (EnergyAnalytics.Interval) interval.getSelectedItem(), selected == null ? null : selected.id());
    }

    private void saveTariff() {
        try {
            BigDecimal value = new BigDecimal(tariff.getText().trim());
            if (value.signum() < 0 || value.scale() > 4 || value.precision() > 10) {
                throw new NumberFormatException();
            }
            onSaveTariff.accept(value);
        } catch (NumberFormatException ex) {
            javax.swing.JOptionPane.showMessageDialog(this,
                "Enter a tariff from 0 to 999999.9999 per kWh, with at most four decimal places.",
                "Invalid tariff", javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    private void changed() {
        if (!initializing && onFilter != null) onFilter.accept(filter());
    }

    private void setDefaultDates() {
        initializing = true;
        LocalDate today = LocalDate.now();
        fromDate.setValue(Date.from(today.minusDays(29).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        toDate.setValue(Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        initializing = false;
    }

    private LocalDate toLocalDate(JSpinner spinner) {
        return ((Date) spinner.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void styleDate(JSpinner spinner) {
        spinner.setEditor(new JSpinner.DateEditor(spinner, "yyyy-MM-dd"));
        spinner.setPreferredSize(new java.awt.Dimension(125, 32));
    }

    private void styleControl(JComponent component) {
        UiStyle.styleControl(component);
    }

    private JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        label.setForeground(UiStyle.MUTED);
        return label;
    }

    private JPanel breakdownCard(String title, DefaultTableModel model) {
        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 10));
        JLabel label = new JLabel(title);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setForeground(UiStyle.TEXT);
        JTable table = new JTable(model);
        UiStyle.styleTable(table);
        card.add(label, BorderLayout.NORTH);
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        return card;
    }

    private void setBreakdown(DefaultTableModel model, List<PowerBreakdown> values) {
        model.setRowCount(0);
        if (values.isEmpty()) model.addRow(new Object[]{"No devices currently ON", "0 W"});
        else for (PowerBreakdown item : values) model.addRow(new Object[]{item.label(), item.watts() + " W"});
    }

    private static DefaultTableModel breakdownModel() {
        return new DefaultTableModel(new Object[]{"Name", "Rated power"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    record RoomOption(Integer id, String name) {
        @Override public String toString() { return name; }
    }

    record EnergyFilter(LocalDate fromDate, LocalDate toDate, EnergyAnalytics.Interval interval, Integer roomId) {}
}
