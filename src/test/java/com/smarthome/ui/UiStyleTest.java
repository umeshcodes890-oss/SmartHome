package com.smarthome.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.plaf.basic.BasicButtonUI;
import org.junit.jupiter.api.Test;

class UiStyleTest {
    @Test
    void appliesThemeToExistingComponents() {
        UiStyle.Theme originalTheme = UiStyle.theme();
        try {
            UiStyle.setTheme(UiStyle.Theme.LIGHT);
            JPanel panel = new JPanel();
            panel.setBackground(UiStyle.WORKSPACE);
            JButton button = UiStyle.button("Save", false);
            panel.add(button);

            UiStyle.Palette previous = UiStyle.setTheme(UiStyle.Theme.DARK);
            UiStyle.applyTheme(panel, previous);

            assertEquals(UiStyle.WORKSPACE, panel.getBackground());
            assertEquals(UiStyle.SURFACE, button.getBackground());
            assertEquals(UiStyle.TEXT, button.getForeground());
            org.junit.jupiter.api.Assertions.assertInstanceOf(BasicButtonUI.class, button.getUI());
        } finally {
            UiStyle.setTheme(originalTheme);
        }
    }

    @Test
    void restoresAppearancePreferenceForAccount() {
        Preferences preferences = Preferences.userNodeForPackage(UiStyle.class);
        long userId = System.nanoTime();
        String key = "theme." + userId;
        String oldTheme = preferences.get("lastTheme", null);
        UiStyle.Theme originalTheme = UiStyle.theme();
        try {
            UiStyle.saveThemeForUser(userId, UiStyle.Theme.DARK);
            UiStyle.loadThemeForUser(userId);
            assertEquals(UiStyle.Theme.DARK, UiStyle.theme());
        } finally {
            preferences.remove(key);
            if (oldTheme == null) preferences.remove("lastTheme");
            else preferences.put("lastTheme", oldTheme);
            UiStyle.setTheme(originalTheme);
        }
    }
}
