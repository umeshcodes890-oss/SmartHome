package com.smarthome.service;

import com.smarthome.dao.ActivityLogDao;
import com.smarthome.model.ActivityLog;
import com.smarthome.model.AuthenticatedUser;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public final class ActivityLogService {
    private final ActivityLogDao logs;
    private final AuthenticationService authentication;

    public ActivityLogService(ActivityLogDao logs, AuthenticationService authentication) {
        this.logs = logs;
        this.authentication = authentication;
    }

    public ActivityLogDao.Page findPage(AuthenticatedUser user, String search, String eventType,
            LocalDate from, LocalDate through, int offset, int pageSize) throws SQLException {
        AuthorizationService authorization = new AuthorizationService(user);
        authorization.requireAdmin();
        authentication.assertActive(user);
        return logs.findPage(search, eventType, from, through, offset, pageSize);
    }

    public List<ActivityLog> findRecent(AuthenticatedUser user, int limit) throws SQLException {
        new AuthorizationService(user).requireAuthenticated();
        authentication.assertActive(user);
        return logs.findRecent(limit);
    }
}