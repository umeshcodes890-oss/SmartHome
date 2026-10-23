package com.smarthome.dao;

import com.smarthome.db.Database;
import com.smarthome.model.EnergyReport;
import com.smarthome.model.PowerBreakdown;
import com.smarthome.model.UsageSession;
import com.smarthome.service.EnergyAnalytics;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class EnergyUsageDao {
    public EnergyReport loadReport(LocalDate fromDate, LocalDate toDate, EnergyAnalytics.Interval interval,
            Integer roomId) throws SQLException {
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("Select a valid date range.");
        }
        ZoneId zone = ZoneId.systemDefault();
        Instant rangeStart = fromDate.atStartOfDay(zone).toInstant();
        Instant selectedRangeEnd = toDate.plusDays(1).atStartOfDay(zone).toInstant();
        Instant rangeEnd = selectedRangeEnd.isAfter(Instant.now()) ? Instant.now() : selectedRangeEnd;

        try (Connection connection = Database.getConnection()) {
            EnergySettings settings = readSettings(connection);
            Instant historyStart = settings.historyStartedAt().toInstant(ZoneOffset.UTC);
            Instant queryStart = rangeStart.isBefore(historyStart) ? historyStart : rangeStart;
            List<UsageSession> sessions = queryStart.isBefore(rangeEnd)
                ? findSessions(connection, queryStart, rangeEnd, roomId)
                : List.of();
            List<PowerBreakdown> byRoom = findActivePower(connection, true, roomId);
            List<PowerBreakdown> byType = findActivePower(connection, false, roomId);
            return EnergyAnalytics.calculate(fromDate, toDate, historyStart, rangeStart, rangeEnd,
                zone, interval, sessions, byRoom, byType, settings.tariffPerKwh());
        }
    }

    public void updateTariff(BigDecimal tariffPerKwh) throws SQLException {
        if (tariffPerKwh == null || tariffPerKwh.signum() < 0
                || tariffPerKwh.scale() > 4 || tariffPerKwh.precision() > 10) {
            throw new IllegalArgumentException("Tariff must be between 0 and 999999.9999 per kWh, with at most four decimal places.");
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE energy_settings SET tariff_per_kwh=? WHERE setting_id=1")) {
            statement.setBigDecimal(1, tariffPerKwh);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Energy settings are not initialized. Apply the energy-history migration.");
            }
        }
    }

    private EnergySettings readSettings(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT tariff_per_kwh, history_started_at FROM energy_settings WHERE setting_id=1");
             ResultSet results = statement.executeQuery()) {
            if (!results.next()) {
                throw new SQLException("Energy settings are not initialized. Apply database/migrations/V2__energy_usage_history.sql.");
            }
            BigDecimal tariff = results.getBigDecimal("tariff_per_kwh");
            LocalDateTime historyStartedAt = results.getObject("history_started_at", LocalDateTime.class);
            if (historyStartedAt == null) throw new SQLException("Energy history start time is missing.");
            return new EnergySettings(tariff, historyStartedAt);
        }
    }

    private List<UsageSession> findSessions(Connection connection, Instant start, Instant end, Integer roomId)
            throws SQLException {
        String sql = "SELECT device_id,device_name,device_type,room_id,room_name,power_watts,started_at,ended_at " +
            "FROM energy_usage_sessions WHERE started_at < ? AND (ended_at IS NULL OR ended_at > ?)" +
            (roomId == null ? "" : " AND room_id=?") + " ORDER BY started_at";
        List<UsageSession> sessions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, LocalDateTime.ofInstant(end, ZoneOffset.UTC));
            statement.setObject(2, LocalDateTime.ofInstant(start, ZoneOffset.UTC));
            if (roomId != null) statement.setInt(3, roomId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    LocalDateTime started = results.getObject("started_at", LocalDateTime.class);
                    LocalDateTime ended = results.getObject("ended_at", LocalDateTime.class);
                    sessions.add(new UsageSession(results.getInt("device_id"), results.getString("device_name"),
                        results.getString("device_type"), results.getInt("room_id"), results.getString("room_name"),
                        results.getBigDecimal("power_watts"), started.toInstant(ZoneOffset.UTC),
                        ended == null ? null : ended.toInstant(ZoneOffset.UTC)));
                }
            }
        }
        return sessions;
    }

    private List<PowerBreakdown> findActivePower(Connection connection, boolean groupByRoom, Integer roomId)
            throws SQLException {
        String group = groupByRoom ? "r.room_name" : "d.device_type";
        String sql = groupByRoom
            ? "SELECT r.room_name AS label, COALESCE(SUM(d.power_watts),0) AS watts " +
                "FROM rooms r LEFT JOIN devices d ON d.room_id=r.room_id AND d.status='ON' " +
                (roomId == null ? "" : "WHERE r.room_id=? ") +
                "GROUP BY r.room_id,r.room_name ORDER BY r.room_name"
            : "SELECT d.device_type AS label, COALESCE(SUM(d.power_watts),0) AS watts " +
                "FROM devices d JOIN rooms r ON r.room_id=d.room_id WHERE d.status='ON'" +
                (roomId == null ? "" : " AND d.room_id=?") +
                " GROUP BY d.device_type ORDER BY d.device_type";
        List<PowerBreakdown> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (roomId != null) statement.setInt(1, roomId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    result.add(new PowerBreakdown(results.getString("label"),
                        results.getBigDecimal("watts").setScale(2, java.math.RoundingMode.HALF_UP)));
                }
            }
        }
        return result;
    }

    private record EnergySettings(BigDecimal tariffPerKwh, LocalDateTime historyStartedAt) {}
}
