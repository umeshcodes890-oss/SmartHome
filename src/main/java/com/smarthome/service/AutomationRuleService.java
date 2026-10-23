package com.smarthome.service;

import com.smarthome.dao.AutomationDao;
import com.smarthome.dao.AutomationDao.AutomationEvaluation;
import com.smarthome.model.AutomationRule;
import com.smarthome.model.AuthenticatedUser;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

public final class AutomationRuleService {
    public record Form(String name, boolean enabled, AutomationRule.Condition condition, BigDecimal threshold,
            Integer sensorRoomId, int targetDeviceId, String action, LocalTime scheduledTime,
            ZoneId timeZone, int cooldownSeconds) {}

    private final AuthorizationService authorization;
    private final AuthenticationService authentication;
    private final AutomationDao dao;

    public AutomationRuleService(AuthenticatedUser user, AuthenticationService authentication, AutomationDao dao) {
        this.authorization = new AuthorizationService(user);
        this.authentication = java.util.Objects.requireNonNull(authentication);
        this.dao = java.util.Objects.requireNonNull(dao);
    }

    public List<AutomationRule> findAll() throws SQLException {
        authorize();
        return dao.findAll();
    }

    public List<AutomationEvaluation> findRecentEvaluations(int limit) throws SQLException {
        authorize();
        return dao.findRecentEvaluations(limit);
    }

    public long create(Form form) throws SQLException {
        authorize();
        return dao.create(form.name(), form.enabled(), form.condition(), form.threshold(), form.sensorRoomId(),
            form.targetDeviceId(), form.action(), form.scheduledTime(), form.timeZone(), form.cooldownSeconds(),
            authorization.user().id());
    }

    public void update(long id, Form form) throws SQLException {
        authorize();
        dao.update(id, form.name(), form.enabled(), form.condition(), form.threshold(), form.sensorRoomId(),
            form.targetDeviceId(), form.action(), form.scheduledTime(), form.timeZone(), form.cooldownSeconds());
    }

    public void setEnabled(long id, boolean enabled) throws SQLException {
        authorize();
        dao.setEnabled(id, enabled);
    }

    public void delete(long id) throws SQLException {
        authorize();
        dao.delete(id);
    }

    private void authorize() throws SQLException {
        authorization.requireAdmin();
        authentication.assertActive(authorization.user());
    }
}
