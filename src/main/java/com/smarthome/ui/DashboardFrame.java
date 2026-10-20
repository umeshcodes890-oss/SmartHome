package com.smarthome.ui;

import com.smarthome.dao.DeviceDao;
import com.smarthome.model.Device;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class DashboardFrame extends JFrame {
    private final DeviceDao dao = new DeviceDao();
    private final DefaultTableModel model = new DefaultTableModel(
        new Object[]{"ID","Device","Type","Room","Status","Power (W)"},0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel summary = new JLabel("Loading devices…");

    public DashboardFrame() {
        setTitle("Smart Home Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900,560); setMinimumSize(new Dimension(760,460)); setLocationRelativeTo(null);
        getContentPane().setLayout(new BorderLayout(12,12));
        getContentPane().setBackground(new Color(245,247,250));
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(18,20,10,20));
        header.setBackground(new Color(24,42,67));
        JLabel title = new JLabel("Smart Home Management");
        title.setForeground(Color.WHITE); title.setFont(new Font("SansSerif",Font.BOLD,23));
        JLabel subtitle = new JLabel("Monitor and control your connected devices");
        subtitle.setForeground(new Color(205,218,232));
        JPanel texts=new JPanel(new GridLayout(2,1)); texts.setOpaque(false); texts.add(title); texts.add(subtitle);
        header.add(texts,BorderLayout.WEST); getContentPane().add(header,BorderLayout.NORTH);

        table.setRowHeight(30); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setFont(new Font("SansSerif",Font.BOLD,13));
        table.setFillsViewportHeight(true);
        JPanel center=new JPanel(new BorderLayout(8,8));
        center.setBorder(BorderFactory.createEmptyBorder(4,20,8,20)); center.setOpaque(false);
        center.add(new JScrollPane(table),BorderLayout.CENTER);
        summary.setFont(new Font("SansSerif",Font.BOLD,13)); summary.setBorder(BorderFactory.createEmptyBorder(4,2,4,2));
        center.add(summary,BorderLayout.SOUTH); getContentPane().add(center,BorderLayout.CENTER);

        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,8,12)); actions.setOpaque(false);
        JButton refresh=button("Refresh"), toggle=button("Turn ON / OFF"), add=button("Add Device"), delete=button("Delete Device");
        actions.add(refresh); actions.add(toggle); actions.add(add); actions.add(delete);
        JPanel footer=new JPanel(new BorderLayout()); footer.setBorder(BorderFactory.createEmptyBorder(0,16,8,16)); footer.setOpaque(false);
        footer.add(actions,BorderLayout.WEST); getContentPane().add(footer,BorderLayout.SOUTH);
        refresh.addActionListener(e->loadDevices());
        toggle.addActionListener(e->toggleSelected());
        add.addActionListener(e->addDevice());
        delete.addActionListener(e->deleteSelected());
        loadDevices();
    }

    private JButton button(String text) {
        JButton b=new JButton(text); b.setFocusPainted(false); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }
    private int selectedId() {
        int row=table.getSelectedRow();
        if(row<0) { JOptionPane.showMessageDialog(this,"Select a device first."); return -1; }
        return (int)model.getValueAt(table.convertRowIndexToModel(row),0);
    }
    private void loadDevices() {
        try {
            List<Device> devices=dao.findAll(); model.setRowCount(0);
            int on=0; double watts=0;
            for(Device d:devices) {
                model.addRow(new Object[]{d.getId(),d.getName(),d.getType(),d.getRoom(),d.getStatus(),d.getPowerWatts()});
                if("ON".equals(d.getStatus())) { on++; watts+=d.getPowerWatts().doubleValue(); }
            }
            summary.setText("Devices: "+devices.size()+"    |    Active: "+on+"    |    Estimated active load: "+String.format("%.1f W",watts));
        } catch(SQLException ex) { showError("Could not load devices. Check MySQL settings and schema.",ex); }
    }
    private void toggleSelected() {
        int id=selectedId(); if(id<0)return;
        int row=table.convertRowIndexToModel(table.getSelectedRow());
        String next="ON".equals(model.getValueAt(row,4))?"OFF":"ON";
        try { dao.setStatus(id,next); loadDevices(); }
        catch(SQLException ex) { showError("Could not update device status.",ex); }
    }
    private void addDevice() {
        try {
            Map<String,Integer> rooms=dao.getRoomIds();
            if(rooms.isEmpty()) { JOptionPane.showMessageDialog(this,"Add a room to the rooms table first."); return; }
            JTextField name=new JTextField(); JTextField type=new JTextField("Light");
            JComboBox<String> room=new JComboBox<>(rooms.keySet().toArray(new String[0]));
            JComboBox<String> status=new JComboBox<>(new String[]{"OFF","ON"});
            JTextField watts=new JTextField("10");
            JPanel form=new JPanel(new GridLayout(0,2,8,8));
            form.add(new JLabel("Device name:"));form.add(name); form.add(new JLabel("Device type:"));form.add(type);
            form.add(new JLabel("Room:"));form.add(room);form.add(new JLabel("Initial status:"));form.add(status);
            form.add(new JLabel("Power in watts:"));form.add(watts);
            if(JOptionPane.showConfirmDialog(this,form,"Add Smart Device",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
            String n=name.getText().trim(), t=type.getText().trim();
            if(n.isEmpty()||t.isEmpty()) { JOptionPane.showMessageDialog(this,"Device name and type are required."); return; }
            BigDecimal w=new BigDecimal(watts.getText().trim());
            if(w.signum()<0) throw new IllegalArgumentException("Power must be zero or greater.");
            dao.add(n,t,rooms.get((String)room.getSelectedItem()),(String)status.getSelectedItem(),w);
            loadDevices();
        } catch(NumberFormatException ex) { JOptionPane.showMessageDialog(this,"Enter a valid number for power (watts)."); }
          catch(IllegalArgumentException ex) { JOptionPane.showMessageDialog(this,ex.getMessage()); }
          catch(SQLException ex) { showError("Could not add device.",ex); }
    }
    private void deleteSelected() {
        int id=selectedId(); if(id<0)return;
        if(JOptionPane.showConfirmDialog(this,"Delete the selected device?","Confirm deletion",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try { dao.delete(id); loadDevices(); }
        catch(SQLException ex) { showError("Could not delete device.",ex); }
    }
    private void showError(String message, Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this,message+"\nDetails: "+ex.getMessage(),"Database error",JOptionPane.ERROR_MESSAGE);
    }
}