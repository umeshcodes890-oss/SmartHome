package com.smarthome.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.text.JTextComponent;

public final class UiStyle {
    public enum Theme { LIGHT, DARK }

    record Palette(Color navy, Color navyLight, Color blue, Color blueHover, Color bluePale,
            Color green, Color greenPale, Color text, Color muted, Color workspace, Color surface,
            Color tableHeader, Color rowAlt, Color border, Color danger, Color dangerPale) {}

    private static final Palette LIGHT = new Palette(
        new Color(29, 45, 58), new Color(43, 65, 81), new Color(38, 111, 191),
        new Color(30, 91, 158), new Color(228, 239, 251), new Color(31, 112, 74),
        new Color(229, 244, 236), new Color(31, 41, 55), new Color(98, 111, 128),
        new Color(244, 247, 251), Color.WHITE, new Color(237, 242, 248),
        new Color(249, 251, 253), new Color(220, 228, 237), new Color(180, 58, 58),
        new Color(252, 235, 235));
    private static final Palette DARK = new Palette(
        new Color(17, 25, 39), new Color(31, 42, 60), new Color(45, 112, 183),
        new Color(103, 174, 255), new Color(37, 57, 82), new Color(66, 185, 127),
        new Color(32, 64, 54), new Color(232, 238, 247), new Color(166, 179, 198),
        new Color(18, 25, 38), new Color(28, 38, 54), new Color(38, 51, 70),
        new Color(32, 43, 60), new Color(53, 68, 89), new Color(255, 123, 123),
        new Color(74, 43, 49));
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(UiStyle.class);
    private static Palette palette = LIGHT;
    private static Theme theme = Theme.LIGHT;

    static Color NAVY = LIGHT.navy();
    static Color NAVY_LIGHT = LIGHT.navyLight();
    static Color BLUE = LIGHT.blue();
    static Color BLUE_HOVER = LIGHT.blueHover();
    static Color BLUE_PALE = LIGHT.bluePale();
    static Color GREEN = LIGHT.green();
    static Color GREEN_PALE = LIGHT.greenPale();
    static Color TEXT = LIGHT.text();
    static Color MUTED = LIGHT.muted();
    static Color WORKSPACE = LIGHT.workspace();
    static Color SURFACE = LIGHT.surface();
    static Color TABLE_HEADER = LIGHT.tableHeader();
    static Color ROW_ALT = LIGHT.rowAlt();
    static Color BORDER = LIGHT.border();
    static Color DANGER = LIGHT.danger();
    static Color DANGER_PALE = LIGHT.dangerPale();

    private UiStyle() {}

    static Theme theme() { return theme; }

    public static void restoreLastTheme() {
        setTheme(parseTheme(PREFERENCES.get("lastTheme", Theme.LIGHT.name())));
    }

    public static void loadThemeForUser(long userId) {
        Theme selected = parseTheme(PREFERENCES.get("theme." + userId,
            PREFERENCES.get("lastTheme", Theme.LIGHT.name())));
        setTheme(selected);
    }

    static java.awt.Image applicationIcon() {
        BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(NAVY);
        graphics.fillRoundRect(1, 1, 46, 46, 13, 13);
        graphics.setColor(BLUE);
        graphics.fillRoundRect(7, 7, 34, 34, 10, 10);
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 24));
        graphics.drawString("⌂", 12, 34);
        graphics.dispose();
        return image;
    }

    static void saveThemeForUser(long userId, Theme selected) {
        PREFERENCES.put("theme." + userId, selected.name());
        PREFERENCES.put("lastTheme", selected.name());
    }

    static Palette setTheme(Theme selected) {
        Palette previous = palette;
        theme = selected == null ? Theme.LIGHT : selected;
        palette = theme == Theme.DARK ? DARK : LIGHT;
        NAVY = palette.navy();
        NAVY_LIGHT = palette.navyLight();
        BLUE = palette.blue();
        BLUE_HOVER = palette.blueHover();
        BLUE_PALE = palette.bluePale();
        GREEN = palette.green();
        GREEN_PALE = palette.greenPale();
        TEXT = palette.text();
        MUTED = palette.muted();
        WORKSPACE = palette.workspace();
        SURFACE = palette.surface();
        TABLE_HEADER = palette.tableHeader();
        ROW_ALT = palette.rowAlt();
        BORDER = palette.border();
        DANGER = palette.danger();
        DANGER_PALE = palette.dangerPale();
        applyDefaults();
        return previous;
    }

    static void applyTheme(Component component, Palette previous) {
        if (component == null) return;
        if (component instanceof Sidebar) return;
        component.setBackground(replace(component.getBackground(), previous));
        component.setForeground(replace(component.getForeground(), previous));
        if (component instanceof JTextComponent text) {
            text.setBackground(SURFACE);
            text.setForeground(TEXT);
            text.setCaretColor(TEXT);
        } else if (component instanceof JComboBox<?> combo) {
            combo.setBackground(SURFACE);
            combo.setForeground(TEXT);
        } else if (component instanceof JLabel label
                && (Color.BLACK.equals(label.getForeground()) || Color.DARK_GRAY.equals(label.getForeground())
                    || new Color(51, 51, 51).equals(label.getForeground()))) {
            label.setForeground(TEXT);
        }
        if (component instanceof JComponent swingComponent) {
            swingComponent.setBorder(replaceBorder(swingComponent.getBorder(), previous));
        }
        if (component instanceof JButton button) {
            button.setOpaque(true);
            button.setContentAreaFilled(true);
        }
        if (component instanceof JTable table) {
            table.setGridColor(BORDER);
            table.setSelectionBackground(BLUE_PALE);
            table.setSelectionForeground(TEXT);
            table.getTableHeader().setBackground(TABLE_HEADER);
            table.getTableHeader().setForeground(MUTED);
            table.getTableHeader().setOpaque(true);
            table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
            applyTheme(table.getTableHeader(), previous);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) applyTheme(child, previous);
        }
    }

    static JPanel card() {
        return new RoundedPanel(18, 20, 18, 20);
    }

    static JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 22, 10, 22));
        return panel;
    }

    static void addFormField(JPanel form, int row, String title, Component field) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(8, 0, 8, 14);
        JLabel caption = new JLabel(title);
        caption.setForeground(MUTED);
        caption.setFont(new Font("SansSerif", Font.PLAIN, 12));
        caption.setLabelFor(field);
        field.getAccessibleContext().setAccessibleName(title);
        form.add(caption, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(8, 0, 8, 0);
        field.setFont(new Font("SansSerif", Font.PLAIN, 13));
        form.add(field, constraints);
        if (field instanceof JComponent control) styleControl(control);
    }

    static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setForeground(primary ? Color.WHITE : TEXT);
        button.setBackground(primary ? BLUE : SURFACE);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setUI(new BasicButtonUI());
        button.setBorderPainted(true);
        button.setFocusPainted(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(primary ? BLUE : BORDER),
            BorderFactory.createEmptyBorder(9, 14, 9, 14)));
        button.getAccessibleContext().setAccessibleName(text);
        return button;
    }

    static void styleControl(JComponent control) {
        control.setFont(new Font("SansSerif", Font.PLAIN, 12));
        control.setForeground(TEXT);
        control.setBackground(SURFACE);
        control.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        if (control instanceof JComboBox<?> combo) {
            combo.setOpaque(true);
            combo.setUI(new BasicComboBoxUI());
        }
    }

    static void styleTable(JTable table) {
        table.setRowHeight(36);
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.setForeground(TEXT);
        table.setBackground(SURFACE);
        table.setGridColor(BORDER);
        table.setSelectionBackground(BLUE_PALE);
        table.setSelectionForeground(TEXT);
        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setBackground(TABLE_HEADER);
        table.getTableHeader().setOpaque(true);
        table.getAccessibleContext().setAccessibleDescription("Smart Home data table");
    }

    private static Color replace(Color color, Palette old) {
        if (color == null || old == null) return color;
        if (color.equals(old.navy())) return NAVY;
        if (color.equals(old.navyLight())) return NAVY_LIGHT;
        if (color.equals(old.blue())) return BLUE;
        if (color.equals(old.blueHover())) return BLUE_HOVER;
        if (color.equals(old.bluePale())) return BLUE_PALE;
        if (color.equals(old.green())) return GREEN;
        if (color.equals(old.greenPale())) return GREEN_PALE;
        if (color.equals(old.text())) return TEXT;
        if (color.equals(old.muted())) return MUTED;
        if (color.equals(old.workspace())) return WORKSPACE;
        if (color.equals(old.surface())) return SURFACE;
        if (color.equals(old.tableHeader())) return TABLE_HEADER;
        if (color.equals(old.rowAlt())) return ROW_ALT;
        if (color.equals(old.border())) return BORDER;
        if (color.equals(old.danger())) return DANGER;
        if (color.equals(old.dangerPale())) return DANGER_PALE;
        return color;
    }

    private static Border replaceBorder(Border border, Palette old) {
        if (border instanceof CompoundBorder compound) {
            return BorderFactory.createCompoundBorder(
                replaceBorder(compound.getOutsideBorder(), old),
                replaceBorder(compound.getInsideBorder(), old));
        }
        if (border instanceof MatteBorder matte) {
            return new MatteBorder(matte.getBorderInsets(), replace(matte.getMatteColor(), old));
        }
        if (border instanceof LineBorder line) {
            return new LineBorder(replace(line.getLineColor(), old), line.getThickness(), line.getRoundedCorners());
        }
        if (border instanceof EmptyBorder empty) return new EmptyBorder(empty.getBorderInsets());
        return border;
    }

    private static Theme parseTheme(String value) {
        try {
            return Theme.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return Theme.LIGHT;
        }
    }

    private static void applyDefaults() {
        UIManager.put("Panel.background", WORKSPACE);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Button.background", SURFACE);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("PasswordField.background", SURFACE);
        UIManager.put("PasswordField.foreground", TEXT);
        UIManager.put("ComboBox.background", SURFACE);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.selectionBackground", BLUE_PALE);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TableHeader.background", TABLE_HEADER);
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("TabbedPane.background", WORKSPACE);
        UIManager.put("TabbedPane.foreground", TEXT);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", new Font("SansSerif", Font.PLAIN, 13));
        UIManager.put("ScrollPane.background", WORKSPACE);
    }
}
