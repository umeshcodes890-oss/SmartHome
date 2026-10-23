package com.smarthome.ui;

import com.smarthome.dao.AutomationDao.AutomationEvaluation;
import com.smarthome.model.AutomationRule;
import java.awt.BorderLayout;
import java.awt.Font;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

final class AutomationPanel extends JPanel {
    private final DefaultTableModel ruleModel = new DefaultTableModel(
        new Object[]{"Name", "Condition", "Sensor room", "Target", "Action", "State", "Last triggered"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final DefaultTableModel historyModel = new DefaultTableModel(
        new Object[]{"Evaluated", "Rule", "Condition", "Action", "Outcome", "Details"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable rulesTable = new JTable(ruleModel);
    private final JLabel loadState = new JLabel("Automation rules and evaluations are stored in MySQL.");
    private final JButton edit = UiStyle.button("Edit", false);
    private final JButton toggle = UiStyle.button("Disable", false);
    private final JButton delete = UiStyle.button("Delete", false);
    private final Consumer<AutomationRule> onEdit;
    private final Consumer<AutomationRule> onToggle;
    private final Consumer<AutomationRule> onDelete;
    private List<AutomationRule> rules = List.of();

    AutomationPanel(Runnable onAdd, Runnable onRefresh, Consumer<AutomationRule> onEdit,
            Consumer<AutomationRule> onToggle, Consumer<AutomationRule> onDelete) {
        this.onEdit = onEdit;
        this.onToggle = onToggle;
        this.onDelete = onDelete;
        setLayout(new BorderLayout(0, 14));
        setBackground(UiStyle.WORKSPACE);
        setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setOpaque(false);
        JPanel headingText = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        headingText.setOpaque(false);
        JLabel title = new JLabel("Automation rules");
        title.setFont(new Font("SansSerif", Font.BOLD, 19));
        title.setForeground(UiStyle.TEXT);
        JLabel subtitle = new JLabel("Rules evaluate sensor readings or local time and only issue stored device ON/OFF actions.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(UiStyle.MUTED);
        headingText.add(title);
        headingText.add(subtitle);
        heading.add(headingText, BorderLayout.WEST);
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        JButton add = UiStyle.button("＋ Add rule", true);
        JButton refresh = UiStyle.button("Refresh", false);
        actions.add(add);
        actions.add(edit);
        actions.add(toggle);
        actions.add(delete);
        actions.add(refresh);
        heading.add(actions, BorderLayout.EAST);
        add.addActionListener(event -> onAdd.run());
        refresh.addActionListener(event -> onRefresh.run());
        edit.addActionListener(event -> selected().ifPresent(onEdit));
        toggle.addActionListener(event -> selected().ifPresent(onToggle));
        delete.addActionListener(event -> selected().ifPresent(onDelete));

        UiStyle.styleTable(rulesTable);
        rulesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rulesTable.getSelectionModel().addListSelectionListener(event -> updateActions());
        JTable historyTable = new JTable(historyModel);
        UiStyle.styleTable(historyTable);
        JScrollPane ruleScroll = new JScrollPane(rulesTable);
        ruleScroll.setBorder(BorderFactory.createLineBorder(UiStyle.BORDER));
        JScrollPane historyScroll = new JScrollPane(historyTable);
        historyScroll.setBorder(BorderFactory.createLineBorder(UiStyle.BORDER));

        JPanel rulesCard = card("Configured rules", ruleScroll);
        JPanel historyCard = card("Recent rule evaluations and actions", historyScroll);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rulesCard, historyCard);
        split.setResizeWeight(0.55);
        split.setBorder(null);
        split.setOpaque(false);
        loadState.setForeground(UiStyle.MUTED);
        loadState.setFont(new Font("SansSerif", Font.PLAIN, 11));
        add(heading, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(loadState, BorderLayout.SOUTH);
        updateActions();
    }

    void setRules(List<AutomationRule> value) {
        rules = List.copyOf(value);
        ruleModel.setRowCount(0);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
        for (AutomationRule rule : rules) {
            String condition = switch (rule.condition()) {
                case TEMPERATURE_ABOVE -> "Temperature > " + rule.threshold() + " °C";
                case HUMIDITY_ABOVE -> "Humidity > " + rule.threshold() + " %";
                case MOTION_DETECTED -> "Motion detected";
                case SCHEDULED_TIME -> "Daily at " + rule.scheduledTime();
            };
            ruleModel.addRow(new Object[]{rule.name(), condition,
                rule.sensorRoomName() == null ? "—" : rule.sensorRoomName(),
                rule.targetDeviceName(), rule.action(), rule.enabled() ? "Enabled" : "Disabled",
                rule.lastTriggeredAt() == null ? "Never" : formatter.format(rule.lastTriggeredAt())});
        }
        loadState.setText(rules.isEmpty() ? "No rules configured." : rules.size() + " rule(s) loaded.");
        updateActions();
    }

    void setEvaluations(List<AutomationEvaluation> evaluations) {
        historyModel.setRowCount(0);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
        for (AutomationEvaluation evaluation : evaluations) {
            historyModel.addRow(new Object[]{formatter.format(evaluation.evaluatedAt()),
                evaluation.ruleName(), evaluation.conditionMet() ? "Met" : "Not met",
                evaluation.actionExecuted() ? "Executed" : "—", evaluation.outcome(), evaluation.details()});
        }
    }

    void setLoading() { loadState.setText("Loading automation rules and history…"); }
    void setLoadError(String message) { loadState.setText(message); }

    private JPanel card(String title, java.awt.Component component) {
        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 10));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font("SansSerif", Font.BOLD, 13));
        heading.setForeground(UiStyle.TEXT);
        card.add(heading, BorderLayout.NORTH);
        card.add(component, BorderLayout.CENTER);
        return card;
    }

    private java.util.Optional<AutomationRule> selected() {
        int row = rulesTable.getSelectedRow();
        return row < 0 || row >= rules.size() ? java.util.Optional.empty()
            : java.util.Optional.of(rules.get(rulesTable.convertRowIndexToModel(row)));
    }

    private void updateActions() {
        AutomationRule selected = selected().orElse(null);
        edit.setEnabled(selected != null);
        toggle.setEnabled(selected != null);
        delete.setEnabled(selected != null);
        if (selected != null) toggle.setText(selected.enabled() ? "Disable" : "Enable");
    }
}
