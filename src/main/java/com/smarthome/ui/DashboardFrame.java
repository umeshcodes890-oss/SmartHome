package com.smarthome.ui;

import com.smarthome.model.AuthenticatedUser;

/** Backward-compatible name for older launch configurations. */
@Deprecated
public class DashboardFrame extends MainFrame {
    private static final long serialVersionUID = 1L;

    public DashboardFrame(AuthenticatedUser user, Runnable onLogout) {
        super(user, onLogout);
    }
}
