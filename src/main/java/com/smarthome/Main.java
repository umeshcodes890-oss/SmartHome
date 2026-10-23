package com.smarthome;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.smarthome.ui.MainFrame;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                System.err.println("Could not apply the system look and feel: " + ex.getMessage());
            }
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}