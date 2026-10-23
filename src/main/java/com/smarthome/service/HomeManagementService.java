package com.smarthome.service;

import com.smarthome.dao.DeviceDao;
import com.smarthome.dao.RoomDao;
import com.smarthome.dao.ScheduleDao;
import com.smarthome.model.AuthenticatedUser;
import com.smarthome.model.Device;
import com.smarthome.model.DeviceSchedule;
import com.smarthome.model.Room;
import com.smarthome.model.ScheduleExecution;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

public final class HomeManagementService {
    private final AuthorizationService authorization;
    private final AuthenticationService authentication;
    private final DeviceDao devices;
    private final RoomDao rooms;
    private final ScheduleDao schedules;

    public HomeManagementService(AuthenticatedUser user, AuthenticationService authentication,
            DeviceDao devices, RoomDao rooms, ScheduleDao schedules) {
        authorization = new AuthorizationService(user);
        this.authentication = authentication;
        this.devices = devices;
        this.rooms = rooms;
        this.schedules = schedules;
    }

    public AuthenticatedUser user() { return authorization.user(); }
    public boolean isAdmin() { return authorization.isAdmin(); }
    public boolean canViewPage(String page) { return authorization.canViewPage(page); }

    public List<Device> findDevices() throws SQLException {
        authorization.requireAuthenticated();
        authentication.assertActive(user());
        return devices.findAll();
    }

    public List<Device> findDevicesByRoom(int roomId) throws SQLException {
        authorization.requireAuthenticated();
        authentication.assertActive(user());
        return devices.findByRoomId(roomId);
    }

    public List<Room> findRooms() throws SQLException {
        authorization.requireAuthenticated();
        authentication.assertActive(user());
        return rooms.findAll();
    }

    public List<DeviceSchedule> findSchedules() throws SQLException {
        authorization.requireAdmin();
        authentication.assertActive(user());
        return schedules.findAll();
    }

    public List<ScheduleExecution> findScheduleHistory(int limit) throws SQLException {
        authorization.requireAdmin();
        authentication.assertActive(user());
        return schedules.findHistory(limit);
    }

    public void setDeviceStatus(int id, String status) throws SQLException {
        authorization.requireAuthenticated();
        authentication.assertActive(user());
        devices.setStatus(id, status, authorization.user().id());
    }

    public int setAllDevicesStatus(String status) throws SQLException {
        authorization.requireAuthenticated();
        authentication.assertActive(user());
        return devices.setAllStatus(status, authorization.user().id());
    }

    public void addDevice(String name, String type, int roomId, BigDecimal watts) throws SQLException {
        authorization.requireAdmin();
        authentication.assertActive(user());
        devices.add(name, type, roomId, "OFF", watts, authorization.user().id());
    }

    public void updateDevice(int id, String name, String type, int roomId, BigDecimal watts) throws SQLException {
        authorization.requireAdmin();
        authentication.assertActive(user());
        devices.update(id, name, type, roomId, watts, authorization.user().id());
    }

    public void deleteDevice(int id) throws SQLException {
        authorization.requireAdmin();
        authentication.assertActive(user());
        devices.delete(id, authorization.user().id());
    }

    public void addRoom(String name, int floor) throws SQLException {
        authorization.requireAdmin();
        authentication.assertActive(user());
        rooms.add(name, floor);
    }

    public void updateRoom(int id, String name, int floor) throws SQLException {
        authorization.requireAdmin();
        authentication.assertActive(user());
        rooms.update(id, name, floor);
    }

    public void deleteRoom(int id) throws SQLException {
        authorization.requireAdmin();
        authentication.assertActive(user());
        rooms.delete(id);
    }

    public long createSchedule(int deviceId, String name, String action, ScheduleRecurrence.Pattern pattern,
            LocalDate date, LocalTime time, ZoneId zone, Set<DayOfWeek> weekdays, Instant now)
            throws SQLException {
        authorization.requireAdmin();
        authentication.assertActive(user());
        return schedules.create(deviceId, name, action, pattern, date, time, zone, weekdays, now,
            authorization.user().id());
    }

    public void updateSchedule(long id, int deviceId, String name, String action,
            ScheduleRecurrence.Pattern pattern, LocalDate date, LocalTime time, ZoneId zone,
            Set<DayOfWeek> weekdays, Instant now) throws SQLException {
        authorization.requireAdmin();
        authentication.assertActive(user());
        schedules.update(id, deviceId, name, action, pattern, date, time, zone, weekdays, now,
            authorization.user().id());
    }

    public void setScheduleEnabled(long id, boolean enabled, Instant now) throws SQLException {
        authorization.requireAdmin();
        authentication.assertActive(user());
        schedules.setEnabled(id, enabled, now, authorization.user().id());
    }

    public void deleteSchedule(long id) throws SQLException {
        authorization.requireAdmin();
        authentication.assertActive(user());
        schedules.delete(id, authorization.user().id());
    }
}
