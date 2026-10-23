package com.smarthome;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.smarthome.dao.UserDao;
import com.smarthome.model.AuthenticatedUser;
import com.smarthome.service.AuthenticationService;
import com.smarthome.ui.AuthenticationDialog;
import com.smarthome.ui.MainFrame;
import com.smarthome.ui.UiStyle;
import java.io.Console;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && "--create-admin".equals(args[0])) {
            createFirstAdmin();
            return;
        }
        if (args.length > 0) {
            System.err.println("Supported command: --create-admin");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                System.err.println("Could not apply the system look and feel: " + ex.getMessage());
            }
            showLogin();
        });
    }

    private static void showLogin() {
        UiStyle.restoreLastTheme();
        AuthenticationService authentication = new AuthenticationService(new UserDao());
        AuthenticatedUser user = AuthenticationDialog.show(null, authentication);
        if (user == null) return;
        UiStyle.loadThemeForUser(user.id());
        MainFrame frame = new MainFrame(user, Main::showLogin);
        frame.setVisible(true);
    }

    private static void createFirstAdmin() {
        Console console = System.console();
        if (console == null) {
            System.err.println("Run this command from a terminal with console input to securely create the first administrator.");
            return;
        }
        String username = console.readLine("Administrator username: ");
        String displayName = console.readLine("Administrator display name: ");
        char[] password = console.readPassword("Administrator password (12+ characters): ");
        char[] confirmation = console.readPassword("Confirm administrator password: ");
        try {
            if (password == null || confirmation == null) {
                System.err.println("Administrator setup was cancelled before the password was entered.");
                return;
            }
            if (!Arrays.equals(password, confirmation)) {
                System.err.println("Passwords do not match.");
                return;
            }
            new AuthenticationService(new UserDao()).bootstrapFirstAdmin(username, displayName, password);
            System.out.println("First administrator created. Sign in through the application.");
        } catch (Exception ex) {
            System.err.println("Could not create the first administrator: " + ex.getMessage());
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}