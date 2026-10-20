package com.smarthome;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.smarthome.ui.DashboardFrame;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            DashboardFrame frame = new DashboardFrame();
            frame.setVisible(true);
        });
    }
}