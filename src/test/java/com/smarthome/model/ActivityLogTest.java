package com.smarthome.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smarthome.dao.ActivityLogDao;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActivityLogTest {
    @Test
    void successfulActionIsExplicitlyRecordedAsSuccess() {
        ActivityLog entry = ActivityLog.success("DEVICE_STATUS", 12L, "Hall Light", null,
            Instant.parse("2026-10-20T13:00:00Z"), "Status changed from OFF to ON.");
        CapturedInsert insert = capture(entry);

        assertEquals(ActivityLog.Outcome.SUCCESS, entry.outcome());
        assertEquals(12L, entry.deviceId());
        assertEquals("Hall Light", entry.deviceName());
        assertEquals("Status changed from OFF to ON.", entry.details());
        assertTrue(insert.sql().contains("INSERT INTO activity_logs"));
        assertEquals("SUCCESS", insert.parameters().get(7));
    }

    @Test
    void failedActionIsNotRepresentedAsSuccessful() {
        ActivityLog entry = ActivityLog.failure("OPERATION_FAILED", null, null, null,
            Instant.parse("2026-10-20T13:00:00Z"), "Could not update device: database unavailable.");
        CapturedInsert insert = capture(entry);

        assertEquals(ActivityLog.Outcome.FAILED, entry.outcome());
        assertEquals("Could not update device: database unavailable.", entry.details());
        assertTrue(insert.sql().contains("INSERT INTO activity_logs"));
        assertEquals("FAILED", insert.parameters().get(7));
    }

    @Test
    void failureWriterRejectsSuccessfulEntriesBeforeDatabaseAccess() {
        ActivityLogDao dao = new ActivityLogDao();
        ActivityLog success = ActivityLog.success("DEVICE_CREATED", 1L, "Lamp", null,
            Instant.now(), "Created.");

        assertThrows(IllegalArgumentException.class, () -> dao.recordFailure(success));
    }

    @Test
    void invalidDateRangeIsRejectedBeforeDatabaseAccess() {
        ActivityLogDao dao = new ActivityLogDao();

        assertThrows(IllegalArgumentException.class, () -> dao.findPage("", "ALL",
            LocalDate.parse("2026-10-21"), LocalDate.parse("2026-10-20"), 0, ActivityLogDao.PAGE_SIZE));
    }

    @Test
    void pageEntriesAreImmutable() {
        ActivityLog entry = ActivityLog.success("DEVICE_CREATED", 1L, "Lamp", null,
            Instant.now(), "Created.");
        ActivityLogDao.Page page = new ActivityLogDao.Page(java.util.List.of(entry), false);

        assertThrows(UnsupportedOperationException.class, () -> page.entries().clear());
    }

    private CapturedInsert capture(ActivityLog entry) {
        Map<Integer, Object> parameters = new HashMap<>();
        String[] sql = new String[1];
        InvocationHandler statementHandler = (proxy, method, args) -> {
            if ("executeUpdate".equals(method.getName())) return 1;
            if (method.getName().startsWith("set") && args != null && args.length >= 2
                    && args[0] instanceof Integer index) {
                parameters.put(index, args[1]);
            }
            return primitiveDefault(method.getReturnType());
        };
        PreparedStatement preparedStatement = proxy(PreparedStatement.class, statementHandler);
        Connection connection = proxy(Connection.class, (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName())) {
                sql[0] = (String) args[0];
                return preparedStatement;
            }
            return primitiveDefault(method.getReturnType());
        });

        try {
            ActivityLogDao.insert(connection, entry);
        } catch (java.sql.SQLException ex) {
            throw new AssertionError("Unexpected SQL failure in the insert binding test.", ex);
        }
        return new CapturedInsert(sql[0], parameters);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }

    private record CapturedInsert(String sql, Map<Integer, Object> parameters) {}
}
