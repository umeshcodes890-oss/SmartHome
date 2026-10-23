package com.smarthome.ui;

import com.smarthome.model.AuthenticatedUser;
import com.smarthome.service.AuthenticationService;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

public final class AuthenticationDialog extends JDialog {
    private final AuthenticationService authentication;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardHost = new JPanel(cardLayout);
    private final JTextField loginUsername = new JTextField(20);
    private final JPasswordField loginPassword = new JPasswordField(20);
    private final JTextField registerUsername = new JTextField(20);
    private final JTextField registerDisplayName = new JTextField(20);
    private final JPasswordField registerPassword = new JPasswordField(20);
    private final JPasswordField confirmPassword = new JPasswordField(20);
    private final JButton loginButton = UiStyle.button("Sign in", true);
    private final JButton registerButton = UiStyle.button("Create resident account", true);
    private AuthenticatedUser authenticatedUser;

    private AuthenticationDialog(java.awt.Window owner, AuthenticationService authentication) {
        super(owner, "Smart Living · Sign in", Dialog.ModalityType.APPLICATION_MODAL);
        this.authentication = authentication;
        setIconImage(UiStyle.applicationIcon());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        cardHost.add(loginCard(), "login");
        cardHost.add(registerCard(), "register");
        add(cardHost, BorderLayout.CENTER);
        loginButton.addActionListener(event -> login());
        registerButton.addActionListener(event -> register());
        getRootPane().setDefaultButton(loginButton);
        setMinimumSize(new java.awt.Dimension(440, 390));
        pack();
        setLocationRelativeTo(owner);
    }

    public static AuthenticatedUser show(java.awt.Window owner, AuthenticationService authentication) {
        AuthenticationDialog dialog = new AuthenticationDialog(owner, authentication);
        dialog.setVisible(true);
        return dialog.authenticatedUser;
    }

    private JPanel loginCard() {
        JPanel panel = formCard("Welcome back", "Sign in to access your smart home.");
        addField(panel, 2, "Username", loginUsername);
        addField(panel, 3, "Password", loginPassword);
        JPanel footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER));
        footer.setOpaque(false);
        JButton toRegister = link("Create a resident account");
        toRegister.addActionListener(event -> cardLayout.show(cardHost, "register"));
        footer.add(loginButton);
        footer.add(toRegister);
        panel.add(footer, border(4));
        return panel;
    }

    private JPanel registerCard() {
        JPanel panel = formCard("Create your account", "New accounts have the RESIDENT role.");
        addField(panel, 2, "Username", registerUsername);
        addField(panel, 3, "Display name", registerDisplayName);
        addField(panel, 4, "Password (12+ characters)", registerPassword);
        addField(panel, 5, "Confirm password", confirmPassword);
        JPanel footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER));
        footer.setOpaque(false);
        JButton toLogin = link("Back to sign in");
        toLogin.addActionListener(event -> cardLayout.show(cardHost, "login"));
        footer.add(registerButton);
        footer.add(toLogin);
        GridBagConstraints constraints = border(6);
        constraints.gridwidth = 2;
        panel.add(footer, constraints);
        return panel;
    }

    private void login() {
        String username = loginUsername.getText().trim();
        char[] password = loginPassword.getPassword();
        setBusy(true);
        new SwingWorker<AuthenticatedUser, Void>() {
            @Override protected AuthenticatedUser doInBackground() throws Exception {
                return authentication.login(username, password);
            }
            @Override protected void done() {
                Arrays.fill(password, '\0');
                loginPassword.setText("");
                setBusy(false);
                try {
                    authenticatedUser = get();
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Sign-in was interrupted. Please try again.");
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    showError(cause instanceof AuthenticationService.AuthenticationException
                        ? cause.getMessage() : "Sign-in is unavailable. Check the database connection and try again.");
                }
            }
        }.execute();
    }

    private void register() {
        String username = registerUsername.getText().trim();
        String displayName = registerDisplayName.getText().trim();
        char[] password = registerPassword.getPassword();
        char[] confirmation = confirmPassword.getPassword();
        if (!Arrays.equals(password, confirmation)) {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
            showError("The passwords do not match.");
            return;
        }
        setBusy(true);
        new SwingWorker<AuthenticatedUser, Void>() {
            @Override protected AuthenticatedUser doInBackground() throws Exception {
                return authentication.registerResident(username, displayName, password);
            }
            @Override protected void done() {
                Arrays.fill(password, '\0');
                Arrays.fill(confirmation, '\0');
                registerPassword.setText("");
                confirmPassword.setText("");
                setBusy(false);
                try {
                    authenticatedUser = get();
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Registration was interrupted. Please try again.");
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof IllegalArgumentException) {
                        showError(cause.getMessage());
                    } else if (cause instanceof SQLException && cause.getMessage() != null
                            && cause.getMessage().contains("already registered")) {
                        showError("That username is already registered.");
                    } else {
                        showError("Registration is unavailable. Check the database connection and try again.");
                    }
                }
            }
        }.execute();
    }

    private JPanel formCard(String title, String subtitle) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiStyle.SURFACE);
        panel.setBorder(BorderFactory.createEmptyBorder(26, 30, 24, 30));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font("SansSerif", Font.BOLD, 21));
        heading.setForeground(UiStyle.TEXT);
        GridBagConstraints headingConstraints = border(0);
        headingConstraints.gridwidth = 2;
        panel.add(heading, headingConstraints);
        JLabel explanation = new JLabel(subtitle);
        explanation.setForeground(UiStyle.MUTED);
        GridBagConstraints subtitleConstraints = border(1);
        subtitleConstraints.gridwidth = 2;
        panel.add(explanation, subtitleConstraints);
        return panel;
    }

    private void addField(JPanel panel, int row, String title, java.awt.Component field) {
        JLabel label = new JLabel(title);
        label.setForeground(UiStyle.MUTED);
        label.setLabelFor(field);
        field.getAccessibleContext().setAccessibleName(title);
        GridBagConstraints caption = border(row);
        caption.gridx = 0;
        panel.add(label, caption);
        GridBagConstraints input = border(row);
        input.gridx = 1;
        input.fill = GridBagConstraints.HORIZONTAL;
        input.weightx = 1;
        panel.add(field, input);
    }

    private GridBagConstraints border(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(row < 0 ? 4 : 8, 0, 8, 10);
        return constraints;
    }

    private JButton link(String text) {
        JButton button = new JButton(text);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setForeground(UiStyle.BLUE);
        button.setFocusPainted(false);
        return button;
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        registerButton.setEnabled(!busy);
        setCursor(busy ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR)
            : java.awt.Cursor.getDefaultCursor());
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Account access", JOptionPane.ERROR_MESSAGE);
    }

}
