package com.smarthome.ui;

import com.smarthome.model.AuthenticatedUser;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

final class ProfilePanel extends JPanel {
    private final AuthenticatedUser user;
    private final Consumer<String> onDisplayName;
    private final Consumer<PasswordChange> onPassword;
    private final Consumer<UiStyle.Theme> onTheme;
    private final Runnable onLogout;
    private final JLabel displayName;

    ProfilePanel(AuthenticatedUser user, Consumer<String> onDisplayName,
            Consumer<PasswordChange> onPassword, Consumer<UiStyle.Theme> onTheme, Runnable onLogout) {
        this.user = user;
        this.onDisplayName = onDisplayName;
        this.onPassword = onPassword;
        this.onTheme = onTheme;
        this.onLogout = onLogout;
        setLayout(new BorderLayout());
        setBackground(UiStyle.WORKSPACE);
        setBorder(BorderFactory.createEmptyBorder(32, 36, 32, 36));
        JPanel card = UiStyle.card();
        card.setLayout(new java.awt.GridLayout(0, 1, 0, 14));
        JLabel title = new JLabel("Profile & settings");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(UiStyle.TEXT);
        JLabel username = new JLabel("Username: " + user.username());
        username.setForeground(UiStyle.MUTED);
        displayName = new JLabel("Display name: " + user.displayName());
        displayName.setForeground(UiStyle.MUTED);
        JLabel role = new JLabel("Role: " + user.role().name());
        role.setForeground(UiStyle.MUTED);
        JButton changeName = UiStyle.button("Update display name", false);
        JButton changePassword = UiStyle.button("Change password", false);
        JButton logout = UiStyle.button("Log out", true);
        JComboBox<UiStyle.Theme> theme = new JComboBox<>(UiStyle.Theme.values());
        theme.setSelectedItem(UiStyle.theme());
        theme.getAccessibleContext().setAccessibleName("Application color theme");
        JLabel appearance = new JLabel("Appearance");
        appearance.setFont(new Font("SansSerif", Font.BOLD, 13));
        appearance.setForeground(UiStyle.TEXT);
        theme.addActionListener(event -> {
            UiStyle.Theme selected = (UiStyle.Theme) theme.getSelectedItem();
            if (selected != null && selected != UiStyle.theme()) onTheme.accept(selected);
        });
        changeName.addActionListener(event -> updateName());
        changePassword.addActionListener(event -> updatePassword());
        logout.addActionListener(event -> onLogout.run());
        card.add(title);
        card.add(username);
        card.add(displayName);
        card.add(role);
        card.add(appearance);
        card.add(theme);
        card.add(changeName);
        card.add(changePassword);
        card.add(logout);
        add(card, BorderLayout.NORTH);
    }

    void setDisplayName(String name) {
        displayName.setText("Display name: " + name);
    }

    private void updateName() {
        JTextField input = new JTextField(user.displayName(), 22);
        if (JOptionPane.showConfirmDialog(this, input, "Update display name",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            onDisplayName.accept(input.getText().trim());
        }
    }

    private void updatePassword() {
        JPasswordField current = new JPasswordField(22);
        JPasswordField next = new JPasswordField(22);
        JPasswordField confirm = new JPasswordField(22);
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Current password"));
        form.add(current);
        form.add(new JLabel("New password (12+ chars)"));
        form.add(next);
        form.add(new JLabel("Confirm new password"));
        form.add(confirm);
        if (JOptionPane.showConfirmDialog(this, form, "Change password",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            clear(current, next, confirm);
            return;
        }
        char[] currentValue = current.getPassword();
        char[] nextValue = next.getPassword();
        char[] confirmValue = confirm.getPassword();
        clear(current, next, confirm);
        if (!Arrays.equals(nextValue, confirmValue)) {
            Arrays.fill(currentValue, '\0');
            Arrays.fill(nextValue, '\0');
            Arrays.fill(confirmValue, '\0');
            JOptionPane.showMessageDialog(this, "The new passwords do not match.", "Change password",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        Arrays.fill(confirmValue, '\0');
        onPassword.accept(new PasswordChange(currentValue, nextValue));
    }

    private static void clear(JPasswordField... fields) {
        for (JPasswordField field : fields) {
            char[] value = field.getPassword();
            Arrays.fill(value, '\0');
            field.setText("");
        }
    }

    record PasswordChange(char[] current, char[] next) {}
}
