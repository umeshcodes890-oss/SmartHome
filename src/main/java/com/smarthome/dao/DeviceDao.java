package com.smarthome.dao;

import com.smarthome.db.Database;
import com.smarthome.model.Device;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;

public class DeviceDao {
    public List<Device> findAll() throws SQLException {
        String sql = "SELECT d.device_id,d.device_name,d.device_type,r.room_name,d.status,d.power_watts " +
                     "FROM devices d JOIN rooms r ON r.room_id=d.room_id ORDER BY d.device_id DESC";
        List<Device> result = new ArrayList<>();
        try (Connection c=Database.getConnection(); PreparedStatement ps=c.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
            while(rs.next()) result.add(new Device(rs.getInt(1),rs.getString(2),rs.getString(3),
                rs.getString(4),rs.getString(5),rs.getBigDecimal(6)));
        }
        return result;
    }

    public void setStatus(int id, String status) throws SQLException {
        if (!"ON".equals(status) && !"OFF".equals(status)) throw new IllegalArgumentException("Invalid status");
        try (Connection c=Database.getConnection();
             PreparedStatement ps=c.prepareStatement("UPDATE devices SET status=? WHERE device_id=?")) {
            ps.setString(1,status); ps.setInt(2,id);
            if(ps.executeUpdate()!=1) throw new SQLException("Device not found.");
        }
    }

    public void add(String name, String type, int roomId, String status, BigDecimal watts) throws SQLException {
        String sql="INSERT INTO devices(device_name,device_type,room_id,status,power_watts) VALUES(?,?,?,?,?)";
        try(Connection c=Database.getConnection(); PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setString(1,name); ps.setString(2,type); ps.setInt(3,roomId); ps.setString(4,status); ps.setBigDecimal(5,watts);
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        try(Connection c=Database.getConnection(); PreparedStatement ps=c.prepareStatement("DELETE FROM devices WHERE device_id=?")) {
            ps.setInt(1,id);
            if(ps.executeUpdate()!=1) throw new SQLException("Device not found.");
        }
    }

    public Map<String,Integer> getRoomIds() throws SQLException {
        Map<String,Integer> rooms=new LinkedHashMap<>();
        try(Connection c=Database.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT room_id,room_name FROM rooms ORDER BY room_name");
            ResultSet rs=ps.executeQuery()) {
            while(rs.next()) rooms.put(rs.getString("room_name"),rs.getInt("room_id"));
        }
        return rooms;
    }
}