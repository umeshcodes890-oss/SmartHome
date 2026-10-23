package com.smarthome.ui;

import com.smarthome.model.UserRole;
import com.smarthome.model.UserSummary;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

final class UserManagementPanel extends JPanel {
    private final Consumer<CreateUser> onCreate;
    private final Consumer<RoleChange> onRole;
    private final Consumer<UserSummary> onEnabled;
    private final Runnable onRefresh;
    private final TableModel model = new TableModel();
    private final JTable table = new JTable(model);
    private final JLabel state = new JLabel("User accounts are loaded from MySQL.");

    UserManagementPanel(Consumer<CreateUser> onCreate, Consumer<RoleChange> onRole,
            Consumer<UserSummary> onEnabled, Runnable onRefresh) {
        this.onCreate = onCreate;
        this.onRole = onRole;
        this.onEnabled = onEnabled;
        this.onRefresh = onRefresh;
        setLayout(new BorderLayout());
        setBackground(UiStyle.WORKSPACE);
        setBorder(BorderFactory.createEmptyBorder(22, 28, 24, 28));
        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 12));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel note = new JLabel("Manage account roles and access. Public registration creates residents only.");
        note.setFont(new Font("SansSerif", Font.PLAIN, 12));
        note.setForeground(UiStyle.MUTED);
        state.setForeground(UiStyle.MUTED);
        state.setFont(new Font("SansSerif", Font.PLAIN, 11));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton add = UiStyle.button("＋ Add user", true);
        JButton role = UiStyle.button("Change role", false);
        JButton enabled = UiStyle.button("Enable / disable", false);
        JButton refresh = UiStyle.button("Refresh", false);
        buttons.add(add);
        buttons.add(role);
        buttons.add(enabled);
        buttons.add(refresh);
        header.add(note, BorderLayout.NORTH);
        header.add(buttons, BorderLayout.SOUTH);
        JPanel tableArea = new JPanel(new BorderLayout(0, 8));
        tableArea.setOpaque(false);
        tableArea.add(state, BorderLayout.NORTH);
        tableArea.add(new JScrollPane(table), BorderLayout.CENTER);

        UiStyle.styleTable(table);
        card.add(header, BorderLayout.NORTH);
        card.add(tableArea, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        add.addActionListener(event -> createUser());
        role.addActionListener(event -> selected().ifPresent(this::changeRole));
        enabled.addActionListener(event -> selected().ifPresent(onEnabled));
        refresh.addActionListener(event -> onRefresh.run());
    }

    void setUsers(List<UserSummary> users) {
        model.setUsers(users);
        state.setText(users.isEmpty() ? "No user accounts were found."
            : users.size() + " user account(s) loaded.");
    }

    void setLoading() { state.setText("Loading user accounts…"); }
    void setLoadError(String message) { state.setText(message); }

    private java.util.Optional<UserSummary> selected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a user first.", "User management",
                JOptionPane.INFORMATION_MESSAGE);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(model.getUser(table.convertRowIndexToModel(row)));
    }

    private void changeRole(UserSummary user) {
        JComboBox<UserRole> roles = new JComboBox<>(UserRole.values());
        roles.setSelectedItem(user.role());
        if (JOptionPane.showConfirmDialog(this, roles, "Role for " + user.username(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            onRole.accept(new RoleChange(user, (UserRole) roles.getSelectedItem()));
        }
    }

    private void createUser() {
        JTextField username = new JTextField(18);
        JTextField displayName = new JTextField(18);
        JPasswordField password = new JPasswordField(18);
        JComboBox<UserRole> role = new JComboBox<>(UserRole.values());
        role.setSelectedItem(UserRole.RESIDENT);
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Username"));
        form.add(username);
        form.add(new JLabel("Display name"));
        form.add(displayName);
        form.add(new JLabel("Password (12+ chars)"));
        form.add(password);
        form.add(new JLabel("Role"));
        form.add(role);
        if (JOptionPane.showConfirmDialog(this, form, "Create user",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            clear(password);
            return;
        }
        char[] secret = password.getPassword();
        clear(password);
        onCreate.accept(new CreateUser(username.getText().trim(), displayName.getText().trim(),
            secret, (UserRole) role.getSelectedItem()));
    }

    private static void clear(JPasswordField field) {
        char[] value = field.getPassword();
        Arrays.fill(value, '\0');
        field.setText("");
    }

    record CreateUser(String username, String displayName, char[] password, UserRole role) {}
    record RoleChange(UserSummary user, UserRole role) {}

    private static final class TableModel extends DefaultTableModel {
        private List<UserSummary> users = List.of();
        TableModel() { super(new Object[]{"Username", "Display name", "Role", "Status"}, 0); }
        @Override public boolean isCellEditable(int row, int column) { return false; }
        void setUsers(List<UserSummary> values) {
            users = List.copyOf(values);
            setRowCount(0);
            for (UserSummary user : users) {
                addRow(new Object[]{user.username(), user.displayName(), user.role(),
                    user.enabled() ? "Enabled" : "Disabled"});
            }
        }
        UserSummary getUser(int index) { return users.get(index); }
    }
}
