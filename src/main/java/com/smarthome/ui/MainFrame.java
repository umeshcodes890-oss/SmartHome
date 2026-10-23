package com.smarthome.ui;

import com.smarthome.dao.DeviceDao;
import com.smarthome.dao.EnergyUsageDao;
import com.smarthome.dao.ActivityLogDao;
import com.smarthome.dao.AutomationDao;
import com.smarthome.dao.RoomDao;
import com.smarthome.dao.ScheduleDao;
import com.smarthome.dao.SensorDao;
import com.smarthome.dao.UserDao;
import com.smarthome.model.Device;
import com.smarthome.model.ActivityLog;
import com.smarthome.model.AutomationRule;
import com.smarthome.model.AuthenticatedUser;
import com.smarthome.model.DeviceSchedule;
import com.smarthome.model.EnergyReport;
import com.smarthome.model.Room;
import com.smarthome.model.RoomSummary;
import com.smarthome.model.ScheduleExecution;
import com.smarthome.model.SensorAlert;
import com.smarthome.model.SensorAlertSettings;
import com.smarthome.model.SensorReading;
import com.smarthome.model.UserRole;
import com.smarthome.model.UserSummary;
import com.smarthome.service.AuthenticationService;
import com.smarthome.service.AutomationEngine;
import com.smarthome.service.AutomationRuleService;
import com.smarthome.service.ActivityLogService;
import com.smarthome.service.AuthorizationService;
import com.smarthome.service.HomeManagementService;
import com.smarthome.service.ScheduleService;
import com.smarthome.service.SensorSimulator;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class MainFrame extends JFrame {
    private final DeviceDao dao = new DeviceDao();
    private final UserDao userDao = new UserDao();
    private final AuthenticationService authenticationService = new AuthenticationService(userDao);
    private final ActivityLogDao activityLogDao = new ActivityLogDao();
    private final ActivityLogService activityLogService =
        new ActivityLogService(activityLogDao, authenticationService);
    private final RoomDao roomDao = new RoomDao();
    private final SensorDao sensorDao = new SensorDao();
    private final AutomationDao automationDao = new AutomationDao(dao);
    private final EnergyUsageDao energyDao = new EnergyUsageDao();
    private final ScheduleDao scheduleDao = new ScheduleDao(dao);
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pageHost = new JPanel(pageLayout);
    private final Sidebar sidebar = new Sidebar(this::showPage);
    private final NotificationPanel notifications = new NotificationPanel();
    private final AppHeader header = new AppHeader(event -> refreshData(), notifications);
    private final DashboardPanel dashboard = new DashboardPanel(this::toggleDevice, this::updateMonitoringSettings);
    private final SensorMonitoringPanel sensorMonitoringPanel = new SensorMonitoringPanel(
        this::loadSensorMonitoring, this::updateSensorSimulation, this::saveSensorAlertSettings);
    private final DeviceTablePanel devicesPanel = new DeviceTablePanel(this::refreshData, this::showAddDeviceDialog,
        this::toggleDevice, this::showEditDeviceDialog, this::deleteDevice, this::setAllDevicesStatus);
    private final RoomsPanel roomsPanel = new RoomsPanel(this::showAddRoomDialog,
        this::selectRoom, this::showEditRoomDialog, this::deleteRoom);
    private final EnergyPanel energyPanel = new EnergyPanel(this::loadEnergyReport, this::saveEnergyTariff,
        this::exportEnergyReport);
    private final SchedulePanel schedulesPanel = new SchedulePanel(this::showAddScheduleDialog,
        this::showEditScheduleDialog, this::toggleSchedule, this::deleteSchedule, this::loadSchedules);
    private final ActivityLogPanel activityLogPanel =
        new ActivityLogPanel(this::loadActivityPage, this::currentActivityFilter);
    private final AutomationPanel automationPanel = new AutomationPanel(this::showAddAutomationDialog,
        this::loadAutomationRules, this::showEditAutomationDialog, this::toggleAutomationRule,
        this::deleteAutomationRule);
    private ProfilePanel profilePanel;
    private UserManagementPanel userManagementPanel;
    private final JLabel status = new JLabel("Connecting to your home database…");
    private final Deque<String> activity = new ArrayDeque<>();
    private List<Device> devices = List.of();
    private List<RoomSummary> rooms = List.of();
    private List<String> roomNames = List.of();
    private List<Room> managedRooms = List.of();
    private List<SensorReading> sensorReadings = List.of();
    private Room selectedRoom;
    private String currentPage = "Dashboard";
    private ActivityLogPanel.Filter activityFilter =
        new ActivityLogPanel.Filter("", "ALL", null, null, 0);
    private final ScheduleService scheduleService;
    private final AutomationEngine automationEngine;
    private final SensorSimulator sensorSimulator;
    private final Timer dashboardRefreshTimer = new Timer(15_000, event -> {
        if ("Dashboard".equals(currentPage) || "Sensor Monitoring".equals(currentPage)) refreshData(true);
    });
    private final AtomicBoolean refreshInProgress = new AtomicBoolean();
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final Preferences monitoringPreferences;
    private SwingWorker<AppData, Void> dashboardRefreshWorker;
    private final Runnable onLogout;
    private AuthenticatedUser authenticatedUser;
    private HomeManagementService homeService;

    public MainFrame(AuthenticatedUser user, Runnable onLogout) {
        super("Smart Living | Smart Home Management");
        authenticatedUser = java.util.Objects.requireNonNull(user);
        this.onLogout = onLogout;
        homeService = new HomeManagementService(user, authenticationService, dao, roomDao, scheduleDao);
        sidebar.setAdminMode(homeService.isAdmin());
        devicesPanel.setAdminMode(homeService.isAdmin());
        roomsPanel.setAdminMode(homeService.isAdmin());
        schedulesPanel.setAdminMode(homeService.isAdmin());
        profilePanel = new ProfilePanel(user, this::updateProfile, this::changePassword,
            this::changeTheme, this::logout);
        userManagementPanel = new UserManagementPanel(this::createManagedUser,
            this::changeManagedUserRole, this::toggleManagedUser, this::loadUsers);
        monitoringPreferences = Preferences.userNodeForPackage(MainFrame.class)
            .node("monitoring").node("user-" + user.id());
        dashboard.setMonitoringSettings(readMonitoringSettings());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        scheduleService = new ScheduleService(scheduleDao,
            () -> SwingUtilities.invokeLater(this::refreshAfterBackgroundActivity), this::onScheduleServiceError);
        automationEngine = new AutomationEngine(automationDao,
            () -> SwingUtilities.invokeLater(this::refreshAfterBackgroundActivity), this::onAutomationServiceError);
        sensorSimulator = new SensorSimulator(roomDao, sensorDao, this::onSensorSimulatorError);
        DashboardPanel.MonitoringSettings settings = readMonitoringSettings();
        automationEngine.setSimulatedReadingsEnabled(settings.simulatorEnabled());
        dashboardRefreshTimer.setDelay(settings.refreshSeconds() * 1000);
        dashboardRefreshTimer.setInitialDelay(settings.refreshSeconds() * 1000);
        sensorSimulator.configure(settings.simulatorEnabled(), settings.simulatorIntervalSeconds());
        automationEngine.setSimulatedReadingsEnabled(settings.simulatorEnabled());
        automationEngine.setSimulatedReadingsEnabled(settings.simulatorEnabled());
        sensorMonitoringPanel.setSimulationSettings(new SensorMonitoringPanel.SimulationSettings(
            settings.simulatorEnabled(), settings.simulatorIntervalSeconds()));
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                status.setText("Stopping background services…");
                cancelMonitoringTasks();
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        stopBackgroundServices();
                        return null;
                    }
                    @Override protected void done() {
                        try {
                            get();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException ex) {
                            showError("One or more background services did not stop cleanly.", ex.getCause());
                        }
                        dispose();
                    }
                }.execute();
            }
        });
        setMinimumSize(new java.awt.Dimension(900, 600));
        setSize(1280, 820);
        setLocationRelativeTo(null);
        setIconImage(UiStyle.applicationIcon());
        header.setProfileName(user.displayName());
        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(UiStyle.WORKSPACE);
        getContentPane().add(sidebar, BorderLayout.WEST);

        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(UiStyle.WORKSPACE);
        workspace.add(header, BorderLayout.NORTH);
        pageHost.setBackground(UiStyle.WORKSPACE);
        pageHost.add(dashboard, "Dashboard");
        pageHost.add(devicesPanel, "Devices");
        pageHost.add(roomsPanel, "Rooms");
        pageHost.add(energyPanel, "Energy Usage");
        pageHost.add(sensorMonitoringPanel, "Sensor Monitoring");
        pageHost.add(schedulesPanel, "Schedules");
        pageHost.add(automationPanel, "Automation Rules");
        pageHost.add(activityLogPanel, "Activity Logs");
        pageHost.add(profilePanel, "Settings");
        pageHost.add(userManagementPanel, "User Management");
        workspace.add(pageHost, BorderLayout.CENTER);
        status.setForeground(UiStyle.MUTED);
        status.setFont(new Font("SansSerif", Font.PLAIN, 11));
        status.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UiStyle.BORDER),
            BorderFactory.createEmptyBorder(9, 28, 9, 28)));
        status.setBackground(UiStyle.SURFACE);
        status.setOpaque(true);
        workspace.add(status, BorderLayout.SOUTH);
        getContentPane().add(workspace, BorderLayout.CENTER);
        installNavigationShortcuts();
        scheduleService.start();
        automationEngine.start();
        dashboardRefreshTimer.start();
        refreshData();
    }

    private void changeTheme(UiStyle.Theme theme) {
        UiStyle.Palette previous = UiStyle.setTheme(theme);
        UiStyle.saveThemeForUser(authenticatedUser.id(), theme);
        UiStyle.applyTheme(getContentPane(), previous);
        sidebar.refreshTheme(currentPage);
        getContentPane().setBackground(UiStyle.WORKSPACE);
        repaintTree(getContentPane());
        status.setText(theme == UiStyle.Theme.DARK ? "Dark appearance enabled" : "Light appearance enabled");
    }

    private void installNavigationShortcuts() {
        String[] pages = {"Dashboard", "Devices", "Rooms", "Energy Usage", "Sensor Monitoring",
            "Schedules", "Automation Rules", "Activity Logs", "User Management", "Settings"};
        for (int index = 0; index < pages.length; index++) {
            String page = pages[index];
            int keyCode = index < 9 ? KeyEvent.VK_1 + index : KeyEvent.VK_0;
            KeyStroke shortcut = KeyStroke.getKeyStroke(keyCode, InputEvent.ALT_DOWN_MASK);
            getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(shortcut, page);
            getRootPane().getActionMap().put(page, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { showPage(page); }
            });
        }
    }

    private static void repaintTree(java.awt.Component component) {
        component.repaint();
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) repaintTree(child);
        }
    }

    private JPanel placeholder(String page) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UiStyle.WORKSPACE);
        panel.setBorder(BorderFactory.createEmptyBorder(40, 42, 40, 42));
        JPanel card = UiStyle.card();
        card.setLayout(new BorderLayout(0, 10));
        JLabel title = new JLabel(page + " is not implemented yet");
        title.setFont(new Font("SansSerif", Font.BOLD, 17));
        title.setForeground(UiStyle.TEXT);
        JLabel explanation = new JLabel("This section is a placeholder. The current MySQL schema does not contain data or functionality for this feature.");
        explanation.setFont(new Font("SansSerif", Font.PLAIN, 12));
        explanation.setForeground(UiStyle.MUTED);
        card.add(title, BorderLayout.NORTH);
        card.add(explanation, BorderLayout.CENTER);
        panel.add(card, BorderLayout.NORTH);
        return panel;
    }

    private void showPage(String page) {
        if (!homeService.canViewPage(page)) {
            showError("Your account does not have access to that section.",
                new SecurityException("Administrator permission is required."));
            return;
        }
        currentPage = page;
        sidebar.select(page);
        header.setTitle(page);
        pageLayout.show(pageHost, page);
        if ("Energy Usage".equals(page)) loadEnergyReport(energyPanel.filter());
        if ("Sensor Monitoring".equals(page)) {
            loadSensorMonitoring(sensorMonitoringPanel.filter());
            loadSensorAlertSettings();
        }
        if ("Schedules".equals(page)) loadSchedules();
        if ("Automation Rules".equals(page)) loadAutomationRules();
        if ("Activity Logs".equals(page)) loadActivityPage(activityFilter);
        if ("User Management".equals(page)) loadUsers();
    }

    private DashboardPanel.MonitoringSettings readMonitoringSettings() {
        int refresh = Math.max(5, Math.min(60, monitoringPreferences.getInt("refreshSeconds", 15)));
        int sample = Math.max(SensorSimulator.MIN_INTERVAL_SECONDS,
            Math.min(SensorSimulator.MAX_INTERVAL_SECONDS, monitoringPreferences.getInt("sensorIntervalSeconds", 30)));
        return new DashboardPanel.MonitoringSettings(refresh,
            monitoringPreferences.getBoolean("simulatorEnabled", false), sample);
    }

    private void updateMonitoringSettings(DashboardPanel.MonitoringSettings settings) {
        monitoringPreferences.putInt("refreshSeconds", settings.refreshSeconds());
        monitoringPreferences.putBoolean("simulatorEnabled", settings.simulatorEnabled());
        monitoringPreferences.putInt("sensorIntervalSeconds", settings.simulatorIntervalSeconds());
        dashboardRefreshTimer.setDelay(settings.refreshSeconds() * 1000);
        dashboardRefreshTimer.setInitialDelay(settings.refreshSeconds() * 1000);
        sensorSimulator.configure(settings.simulatorEnabled(), settings.simulatorIntervalSeconds());
        dashboard.setMonitoringSettings(settings);
        sensorMonitoringPanel.setSimulationSettings(new SensorMonitoringPanel.SimulationSettings(
            settings.simulatorEnabled(), settings.simulatorIntervalSeconds()));
        status.setText(settings.simulatorEnabled()
            ? "Simulated sensor readings enabled · every " + settings.simulatorIntervalSeconds() + " seconds"
            : "Sensor simulation disabled · physical sensors are not connected");
        if (settings.simulatorEnabled()) refreshData();
    }

    private void updateSensorSimulation(SensorMonitoringPanel.SimulationSettings settings) {
        DashboardPanel.MonitoringSettings current = readMonitoringSettings();
        updateMonitoringSettings(new DashboardPanel.MonitoringSettings(current.refreshSeconds(),
            settings.enabled(), settings.intervalSeconds()));
    }

    private void loadSensorMonitoring(SensorMonitoringPanel.Filter filter) {
        sensorMonitoringPanel.setLoading();
        new SwingWorker<SensorMonitoringData, Void>() {
            @Override protected SensorMonitoringData doInBackground() throws SQLException {
                return new SensorMonitoringData(sensorDao.findLatest(filter.roomId(), filter.type()),
                    sensorDao.findHistory(filter.roomId(), filter.type(), 100),
                    sensorDao.findAlerts(filter.roomId(), 50));
            }

            @Override protected void done() {
                if (!filter.equals(sensorMonitoringPanel.filter())) return;
                try {
                    SensorMonitoringData data = get();
                    sensorMonitoringPanel.setData(data.latest(), data.history(), data.alerts());
                    status.setText("Sensor readings and alert history loaded from MySQL");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    if (!filter.equals(sensorMonitoringPanel.filter())) return;
                    sensorMonitoringPanel.setLoadError("Loading sensor monitoring was interrupted.");
                    showError("Loading sensor monitoring was interrupted.", ex);
                } catch (ExecutionException ex) {
                    if (!filter.equals(sensorMonitoringPanel.filter())) return;
                    sensorMonitoringPanel.setLoadError("Sensor data is unavailable. Check MySQL and apply V8.");
                    status.setText("Sensor monitoring unavailable — check MySQL and apply V8");
                    showError("Could not load sensor monitoring data. Apply the V8 sensor alerts migration.",
                        ex.getCause());
                }
            }
        }.execute();
    }

    private void loadSensorAlertSettings() {
        new SwingWorker<SensorAlertSettings, Void>() {
            @Override protected SensorAlertSettings doInBackground() throws SQLException {
                return sensorDao.findAlertSettings();
            }
            @Override protected void done() {
                try {
                    sensorMonitoringPanel.setThresholdSettings(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    sensorMonitoringPanel.setThresholdError("Loading thresholds was interrupted.");
                } catch (ExecutionException ex) {
                    sensorMonitoringPanel.setThresholdError("Alert thresholds could not be loaded. Apply V8.");
                    showError("Could not load sensor alert thresholds. Apply the V8 sensor alerts migration.",
                        ex.getCause());
                }
            }
        }.execute();
    }

    private void saveSensorAlertSettings(SensorAlertSettings settings) {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws SQLException {
                sensorDao.saveAlertSettings(settings);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    sensorMonitoringPanel.setThresholdSettings(settings);
                    sensorMonitoringPanel.setThresholdMessage("Alert thresholds saved.");
                    status.setText("Sensor alert thresholds saved to MySQL");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    sensorMonitoringPanel.setThresholdError("Saving thresholds was interrupted.");
                    showError("Saving sensor alert thresholds was interrupted.", ex);
                } catch (ExecutionException ex) {
                    sensorMonitoringPanel.setThresholdError("Thresholds could not be saved.");
                    showError("Could not save sensor alert thresholds.", ex.getCause());
                }
            }
        }.execute();
    }

    private void onSensorSimulatorError(Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            status.setText("Sensor simulation failed — check MySQL and apply the V8 sensor alerts migration");
            notifications.addError("Sensor simulation or alert processing could not store sensor data.");
        });
    }

    private void onAutomationServiceError(Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            status.setText("Automation engine error — check MySQL and apply the V7 migration");
            notifications.addError("Automation rules could not be evaluated.");
            if ("Automation Rules".equals(currentPage)) {
                automationPanel.setLoadError("Automation engine unavailable. Check MySQL and the V7 migration.");
            }
        });
    }

    private void stopBackgroundServices() {
        if (!shutdownStarted.compareAndSet(false, true)) return;
        RuntimeException failure = null;
        for (AutoCloseable service : List.of(automationEngine, sensorSimulator, scheduleService)) {
            try {
                service.close();
            } catch (Exception ex) {
                if (failure == null) failure = new IllegalStateException("One or more background services failed to stop.");
                failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
    }

    private void cancelMonitoringTasks() {
        dashboardRefreshTimer.stop();
        if (dashboardRefreshWorker != null) dashboardRefreshWorker.cancel(true);
    }

    private void loadUsers() {
        userManagementPanel.setLoading();
        new SwingWorker<List<UserSummary>, Void>() {
            @Override protected List<UserSummary> doInBackground() throws Exception {
                return authenticationService.listUsers(new AuthorizationService(authenticatedUser));
            }
            @Override protected void done() {
                try {
                    userManagementPanel.setUsers(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    userManagementPanel.setLoadError("Loading user accounts was interrupted.");
                    showError("Loading user accounts was interrupted.", ex);
                } catch (ExecutionException ex) {
                    userManagementPanel.setLoadError("User accounts could not be loaded.");
                    showError("Could not load user accounts.", ex.getCause());
                }
            }
        }.execute();
    }

    private void createManagedUser(UserManagementPanel.CreateUser form) {
        runDatabaseAction("Creating account…", "Could not create account.",
            () -> {
                try {
                    authenticationService.createUser(new AuthorizationService(authenticatedUser),
                        form.username(), form.displayName(), form.password(), form.role());
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                } finally {
                    java.util.Arrays.fill(form.password(), '\0');
                }
            }, form.username() + " account created");
    }

    private void changeManagedUserRole(UserManagementPanel.RoleChange change) {
        runDatabaseAction("Updating account role…", "Could not update account role.",
            () -> {
                try {
                    authenticationService.updateUserRole(new AuthorizationService(authenticatedUser),
                        change.user().id(), change.role());
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, change.user().username() + " role changed to " + change.role());
    }

    private void toggleManagedUser(UserSummary user) {
        boolean enabled = !user.enabled();
        runDatabaseAction((enabled ? "Enabling " : "Disabling ") + user.username() + "…",
            "Could not change account access.",
            () -> {
                try {
                    authenticationService.setUserEnabled(new AuthorizationService(authenticatedUser),
                        user.id(), enabled);
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, user.username() + (enabled ? " enabled" : " disabled"));
    }

    private void updateProfile(String displayName) {
        new SwingWorker<AuthenticatedUser, Void>() {
            @Override protected AuthenticatedUser doInBackground() throws SQLException {
                return authenticationService.updateProfile(authenticatedUser, displayName);
            }
            @Override protected void done() {
                try {
                    authenticatedUser = get();
                    homeService = new HomeManagementService(authenticatedUser, authenticationService,
                        dao, roomDao, scheduleDao);
                    profilePanel.setDisplayName(authenticatedUser.displayName());
                    header.setProfileName(authenticatedUser.displayName());
                    notifications.addAction("Profile updated");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Updating your profile was interrupted.", ex);
                } catch (ExecutionException ex) {
                    showError("Could not update your profile.", ex.getCause());
                }
            }
        }.execute();
    }

    private void changePassword(ProfilePanel.PasswordChange password) {
        new SwingWorker<AuthenticatedUser, Void>() {
            @Override protected AuthenticatedUser doInBackground() throws SQLException {
                try {
                    return authenticationService.changePassword(authenticatedUser,
                        password.current(), password.next());
                } finally {
                    java.util.Arrays.fill(password.current(), '\0');
                    java.util.Arrays.fill(password.next(), '\0');
                }
            }
            @Override protected void done() {
                try {
                    authenticatedUser = get();
                    homeService = new HomeManagementService(authenticatedUser, authenticationService,
                        dao, roomDao, scheduleDao);
                    notifications.addAction("Password changed");
                    JOptionPane.showMessageDialog(MainFrame.this, "Password changed successfully.",
                        "Profile", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Changing your password was interrupted.", ex);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    showError(cause instanceof AuthenticationService.AuthenticationException
                        ? cause.getMessage() : "Could not change your password.", cause);
                }
            }
        }.execute();
    }

    private void logout() {
        status.setText("Signing out…");
        cancelMonitoringTasks();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                stopBackgroundServices();
                return null;
            }
            @Override protected void done() {
                Throwable shutdownFailure = null;
                try {
                    get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    shutdownFailure = ex;
                } catch (ExecutionException ex) {
                    shutdownFailure = ex.getCause();
                }
                dispose();
                if (shutdownFailure != null) {
                    JOptionPane.showMessageDialog(null, "Background services did not stop cleanly: "
                        + shutdownFailure.getMessage(), "Shutdown warning", JOptionPane.WARNING_MESSAGE);
                }
                onLogout.run();
            }
        }.execute();
    }

    private ActivityLogPanel.Filter currentActivityFilter() {
        return activityFilter;
    }

    private void loadActivityPage(ActivityLogPanel.Filter filter) {
        activityFilter = filter;
        activityLogPanel.setLoading();
        new SwingWorker<ActivityLogDao.Page, Void>() {
            @Override protected ActivityLogDao.Page doInBackground() throws SQLException {
                return activityLogService.findPage(authenticatedUser, filter.search(), filter.eventType(),
                    filter.from(), filter.through(), filter.offset(), ActivityLogDao.PAGE_SIZE);
            }

            @Override protected void done() {
                try {
                    activityLogPanel.setPage(get());
                    if ("Activity Logs".equals(currentPage)) status.setText("Activity log page loaded");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    activityLogPanel.setLoadError("Loading activity logs was interrupted.");
                    showError("Loading activity logs was interrupted.", ex);
                } catch (ExecutionException ex) {
                    activityLogPanel.setLoadError("Activity logs could not be loaded.");
                    status.setText("Activity logs unavailable — apply the V4 migration");
                    if ("Activity Logs".equals(currentPage)) {
                        showError("Could not load activity logs. Apply database/migrations/V4__activity_logs.sql.",
                            ex.getCause());
                    }
                }
            }
        }.execute();
    }

    private void loadNotifications() {
        new SwingWorker<List<ActivityLog>, Void>() {
            @Override protected List<ActivityLog> doInBackground() throws SQLException {
                return activityLogService.findRecent(authenticatedUser, 10);
            }

            @Override protected void done() {
                try {
                    notifications.setRecentLogs(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    status.setText("Notifications unavailable — apply the V4 activity-log migration");
                }
            }
        }.execute();
    }

    private void refreshAfterBackgroundActivity() {
        if (shutdownStarted.get()) return;
        refreshData();
        if ("Activity Logs".equals(currentPage)) loadActivityPage(activityFilter);
    }

    private void refreshData() {
        refreshData(false);
    }

    private void refreshData(boolean periodic) {
        if (!refreshInProgress.compareAndSet(false, true)) return;
        status.setText("Loading devices and room data…");
        dashboard.setLoading();
        devicesPanel.setLoading();
        roomsPanel.setLoading();
        SwingWorker<AppData, Void> worker = new SwingWorker<>() {
            @Override protected AppData doInBackground() throws SQLException {
                List<Room> roomList = homeService.findRooms();
                List<RoomSummary> summaries = roomList.stream()
                    .map(room -> new RoomSummary(room.id(), room.name(), room.deviceCount(), room.activeDeviceCount()))
                    .toList();
                List<String> names = roomList.stream().map(Room::name).toList();
                List<Device> loadedDevices = homeService.findDevices();
                List<SensorReading> sensorReadings = sensorDao.findLatest();
                return new AppData(loadedDevices, summaries, names, roomList, sensorReadings);
            }

            @Override protected void done() {
                try {
                    if (isCancelled()) return;
                    AppData data = get();
                    devices = data.devices();
                    rooms = data.rooms();
                    roomNames = data.roomNames();
                    managedRooms = data.managedRooms();
                    sensorReadings = data.sensorReadings();
                    sensorMonitoringPanel.setRooms(managedRooms);
                    updateViews();
                    if ("Sensor Monitoring".equals(currentPage)) {
                        loadSensorMonitoring(sensorMonitoringPanel.filter());
                    }
                    if (homeService.isAdmin() && !periodic) loadSchedules();
                    loadNotifications();
                    if ("Activity Logs".equals(currentPage)) loadActivityPage(activityFilter);
                    status.setText("Dashboard and device states refreshed from MySQL");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    dashboard.setLoadError("Dashboard refresh was interrupted.");
                    devicesPanel.setLoadError("Device refresh was interrupted.");
                    roomsPanel.setLoadError("Room refresh was interrupted.");
                    dashboard.setSensorError("Sensor readings unavailable because refresh was interrupted.");
                    status.setText("Dashboard refresh was interrupted");
                    if (!periodic) showError("Refresh was interrupted.", ex);
                } catch (ExecutionException ex) {
                    dashboard.setLoadError("Dashboard data is unavailable. Check MySQL and the V7 monitoring migration.");
                    dashboard.setSensorError("Sensor readings unavailable. Check the V7 migration and MySQL.");
                    devicesPanel.setLoadError("Device data is unavailable.");
                    roomsPanel.setLoadError("Room data is unavailable.");
                    status.setText("Live refresh failed — check MySQL and apply database/migrations/V8__sensor_alerts.sql");
                    if (!periodic) showError("Could not load smart-home data. Check MySQL and apply the V8 sensor alerts migration.",
                        ex.getCause());
                } finally {
                    refreshInProgress.set(false);
                    if (dashboardRefreshWorker == this) dashboardRefreshWorker = null;
                }
            }
        };
        dashboardRefreshWorker = worker;
        worker.execute();
    }

    private void loadSchedules() {
        schedulesPanel.setLoading();
        new SwingWorker<ScheduleData, Void>() {
            @Override protected ScheduleData doInBackground() throws SQLException {
                return new ScheduleData(homeService.findSchedules(), homeService.findScheduleHistory(200));
            }

            @Override protected void done() {
                try {
                    ScheduleData data = get();
                    schedulesPanel.setSchedules(data.schedules());
                    schedulesPanel.setHistory(data.history());
                    if ("Schedules".equals(currentPage)) {
                        status.setText(data.schedules().size() + " schedule(s) · execution history loaded");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    schedulesPanel.setLoadError("Loading schedules was interrupted.");
                    if ("Schedules".equals(currentPage)) showError("Loading schedules was interrupted.", ex);
                } catch (ExecutionException ex) {
                    schedulesPanel.setLoadError("Schedule data is unavailable.");
                    status.setText("Schedule data unavailable — apply the V3 schedules migration");
                    if ("Schedules".equals(currentPage)) {
                        showError("Could not load schedules. Apply database/migrations/V3__device_schedules.sql.",
                            ex.getCause());
                    }
                }
            }
        }.execute();
    }

    private AutomationRuleService automationRuleService() {
        return new AutomationRuleService(authenticatedUser, authenticationService, automationDao);
    }

    private void loadAutomationRules() {
        automationPanel.setLoading();
        new SwingWorker<AutomationData, Void>() {
            @Override protected AutomationData doInBackground() throws SQLException {
                AutomationRuleService service = automationRuleService();
                return new AutomationData(service.findAll(), service.findRecentEvaluations(100));
            }

            @Override protected void done() {
                try {
                    AutomationData data = get();
                    automationPanel.setRules(data.rules());
                    automationPanel.setEvaluations(data.evaluations());
                    if ("Automation Rules".equals(currentPage)) {
                        status.setText(data.rules().size() + " automation rule(s) · recent evaluations loaded");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    automationPanel.setLoadError("Loading automation rules was interrupted.");
                    if ("Automation Rules".equals(currentPage)) showError("Loading automation rules was interrupted.", ex);
                } catch (ExecutionException ex) {
                    automationPanel.setLoadError("Rules unavailable — check MySQL and apply the V7 monitoring migration.");
                    status.setText("Automation rules unavailable — apply the V7 migration");
                    if ("Automation Rules".equals(currentPage)) {
                        showError("Could not load automation rules. Apply database/migrations/V7__monitoring_and_automation.sql.",
                            ex.getCause());
                    }
                }
            }
        }.execute();
    }

    private void showAddAutomationDialog() {
        AutomationDialog.show(this, managedRooms, devices, null).ifPresent(this::saveAutomationRule);
    }

    private void showEditAutomationDialog(AutomationRule rule) {
        AutomationDialog.show(this, managedRooms, devices, rule)
            .ifPresent(form -> saveAutomationRule(rule, form));
    }

    private void saveAutomationRule(AutomationRuleService.Form form) {
        runDatabaseAction("Creating automation rule…", "Could not create automation rule.",
            () -> {
                try {
                    automationRuleService().create(form);
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, form.name() + " automation rule created");
    }

    private void saveAutomationRule(AutomationRule existing, AutomationRuleService.Form form) {
        runDatabaseAction("Saving automation rule…", "Could not update automation rule.",
            () -> {
                try {
                    automationRuleService().update(existing.id(), form);
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, form.name() + " automation rule updated");
    }

    private void toggleAutomationRule(AutomationRule rule) {
        boolean enabled = !rule.enabled();
        runDatabaseAction((enabled ? "Enabling " : "Disabling ") + rule.name() + "…",
            "Could not change automation rule status.",
            () -> {
                try {
                    automationRuleService().setEnabled(rule.id(), enabled);
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, rule.name() + (enabled ? " enabled" : " disabled"));
    }

    private void deleteAutomationRule(AutomationRule rule) {
        if (JOptionPane.showConfirmDialog(this, "Delete the automation rule \"" + rule.name()
                + "\"? Its evaluation history will be retained.",
                "Confirm rule deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) return;
        runDatabaseAction("Deleting automation rule…", "Could not delete automation rule.",
            () -> {
                try {
                    automationRuleService().delete(rule.id());
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, rule.name() + " automation rule deleted");
    }

    private void showAddScheduleDialog() {
        ScheduleDialog.show(this, devices, null).ifPresent(this::saveSchedule);
    }

    private void showEditScheduleDialog(DeviceSchedule schedule) {
        ScheduleDialog.show(this, devices, schedule).ifPresent(form -> saveSchedule(schedule, form));
    }

    private void saveSchedule(ScheduleDialog.Form form) {
        runDatabaseAction("Creating schedule…", "Could not create schedule.",
            () -> {
                try {
                    homeService.createSchedule(form.deviceId(), form.name(), form.action(), form.pattern(),
                        form.startDate(), form.localTime(), form.zone(), form.weekdays(), java.time.Instant.now());
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, form.name() + " schedule created");
    }

    private void saveSchedule(DeviceSchedule existing, ScheduleDialog.Form form) {
        runDatabaseAction("Saving schedule…", "Could not update schedule.",
            () -> {
                try {
                    homeService.updateSchedule(existing.id(), form.deviceId(), form.name(), form.action(), form.pattern(),
                        form.startDate(), form.localTime(), form.zone(), form.weekdays(), java.time.Instant.now());
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, form.name() + " schedule updated");
    }

    private void toggleSchedule(DeviceSchedule schedule) {
        boolean enable = !schedule.enabled();
        runDatabaseAction((enable ? "Enabling " : "Disabling ") + schedule.scheduleName() + "…",
            "Could not change schedule status.",
            () -> {
                try {
                    homeService.setScheduleEnabled(schedule.id(), enable, java.time.Instant.now());
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, schedule.scheduleName() + (enable ? " enabled" : " disabled"));
    }

    private void deleteSchedule(DeviceSchedule schedule) {
        if (JOptionPane.showConfirmDialog(this,
                "Delete the schedule \"" + schedule.scheduleName() + "\"? Execution history will be retained.",
                "Confirm schedule deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) return;
        runDatabaseAction("Deleting schedule…", "Could not delete schedule.",
            () -> {
                try {
                    homeService.deleteSchedule(schedule.id());
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, schedule.scheduleName() + " schedule deleted");
    }

    private void onScheduleServiceError(Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            status.setText("Schedule service could not access its database tables");
            showError("The schedule service could not run. Apply database/migrations/V3__device_schedules.sql "
                + "and confirm the MySQL connection.", failure);
        });
    }

    private void runDatabaseAction(String progressMessage, String failureMessage, Supplier<String> action, String activityMessage) {
        status.setText(progressMessage);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                try {
                    return action.get();
                } catch (RuntimeException failure) {
                    Throwable cause = failure instanceof DatabaseActionException && failure.getCause() != null
                        ? failure.getCause() : failure;
                    String detail = failureMessage + " Requested operation: " + activityMessage
                        + ". Cause: " + cause.getMessage();
                    if (detail.length() > 1000) detail = detail.substring(0, 1000);
                    try {
                        activityLogDao.recordFailure(new ActivityLog(0, "OPERATION_FAILED",
                            authenticatedUser.id(), null, null, null, java.time.Instant.now(),
                            ActivityLog.Outcome.FAILED, detail));
                    } catch (SQLException auditFailure) {
                        failure.addSuppressed(auditFailure);
                    }
                    throw failure;
                }
            }

            @Override protected void done() {
                try {
                    String resultMessage = get();
                    activity.addFirst(activityMessage);
                    while (activity.size() > 5) activity.removeLast();
                    notifications.addAction(activityMessage);
                    JOptionPane.showMessageDialog(MainFrame.this,
                        resultMessage == null ? activityMessage : resultMessage, "Success", JOptionPane.INFORMATION_MESSAGE);
                    refreshData();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError(failureMessage, ex);
                } catch (ExecutionException ex) {
                    showError(failureMessage, ex.getCause());
                    loadNotifications();
                    if ("Activity Logs".equals(currentPage)) loadActivityPage(activityFilter);
                    status.setText("Action failed");
                }
            }
        }.execute();
    }

    private void toggleDevice(Device device) {
        String next = "ON".equals(device.getStatus()) ? "OFF" : "ON";
        runDatabaseAction("Updating " + device.getName() + "…", "Could not update device status.",
            () -> {
                try {
                    homeService.setDeviceStatus(device.getId(), next);
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, device.getName() + " turned " + next);
    }

    private void setAllDevicesStatus(String requestedStatus) {
        String action = "ON".equals(requestedStatus) ? "turn every device on" : "turn every device off";
        if (JOptionPane.showConfirmDialog(this, "Are you sure you want to " + action + "?",
                "Confirm bulk change", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        runDatabaseAction("Setting all devices " + requestedStatus + "…", "Could not update all device statuses.",
            () -> {
                try {
                    int changed = homeService.setAllDevicesStatus(requestedStatus);
                    return changed + " device(s) set to " + requestedStatus + ".";
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, "All devices set to " + requestedStatus);
    }

    private void deleteDevice(Device device) {
        if (JOptionPane.showConfirmDialog(this, "Delete " + device.getName() + "?", "Confirm deletion",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        runDatabaseAction("Deleting " + device.getName() + "…", "Could not delete device.",
            () -> {
                try {
                    homeService.deleteDevice(device.getId());
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, device.getName() + " deleted");
    }

    private void showAddDeviceDialog() {
        status.setText("Loading available rooms…");
        new SwingWorker<List<String>, Void>() {
            @Override protected List<String> doInBackground() throws SQLException {
                return dao.findRoomNames();
            }

            @Override protected void done() {
                try {
                    List<String> availableRooms = get();
                    if (availableRooms.isEmpty()) {
                        JOptionPane.showMessageDialog(MainFrame.this, "Add a room to the database before adding devices.",
                            "No rooms available", JOptionPane.INFORMATION_MESSAGE);
                        status.setText("No rooms available");
                        return;
                    }
                    java.util.Optional<DeviceDialog.DeviceForm> form = DeviceDialog.show(
                        MainFrame.this, "Add device", availableRooms, null);
                    form.ifPresent(deviceForm ->
                        runDatabaseAction("Adding " + deviceForm.name() + "…", "Could not add device.",
                            () -> {
                                try {
                                    homeService.addDevice(deviceForm.name(), deviceForm.type(),
                                        roomIdFor(deviceForm.roomName()), deviceForm.powerWatts());
                                    return null;
                                } catch (SQLException ex) {
                                    throw new DatabaseActionException(ex);
                                }
                            }, deviceForm.name() + " added"));
                    if (form.isEmpty()) status.setText("Ready");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Could not load rooms.", ex);
                } catch (ExecutionException ex) {
                    showError("Could not load rooms for the device form.", ex.getCause());
                }
            }
        }.execute();
    }

    private void showEditDeviceDialog(Device device) {
        status.setText("Loading available rooms…");
        new SwingWorker<List<String>, Void>() {
            @Override protected List<String> doInBackground() throws SQLException {
                return dao.findRoomNames();
            }

            @Override protected void done() {
                try {
                    List<String> availableRooms = get();
                    java.util.Optional<DeviceDialog.DeviceForm> form = DeviceDialog.show(
                        MainFrame.this, "Edit device", availableRooms, device);
                    form.ifPresent(deviceForm ->
                        runDatabaseAction("Saving " + deviceForm.name() + "…", "Could not update device.",
                            () -> {
                                try {
                                    homeService.updateDevice(device.getId(), deviceForm.name(), deviceForm.type(),
                                        roomIdFor(deviceForm.roomName()), deviceForm.powerWatts());
                                    return null;
                                } catch (SQLException ex) {
                                    throw new DatabaseActionException(ex);
                                }
                            }, deviceForm.name() + " updated"));
                    if (form.isEmpty()) status.setText("Ready");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Could not load rooms.", ex);
                } catch (ExecutionException ex) {
                    showError("Could not load rooms for editing.", ex.getCause());
                }
            }
        }.execute();
    }

    private int roomIdFor(String roomName) throws SQLException {
        Integer roomId = dao.getRoomIds().get(roomName);
        if (roomId == null) throw new SQLException("The selected room no longer exists. Refresh and try again.");
        return roomId;
    }

    private void showAddRoomDialog() {
        RoomDialog.show(this, "Add room", null).ifPresent(form ->
            runDatabaseAction("Adding " + form.name() + "…", "Could not add room.",
                () -> {
                    try {
                        homeService.addRoom(form.name(), form.floorNumber());
                        return null;
                    } catch (SQLException ex) {
                        throw new DatabaseActionException(ex);
                    }
                }, form.name() + " room added"));
    }

    private void showEditRoomDialog(Room room) {
        RoomDialog.show(this, "Edit room", room).ifPresent(form ->
            runDatabaseAction("Saving " + form.name() + "…", "Could not update room.",
                () -> {
                    try {
                        homeService.updateRoom(room.id(), form.name(), form.floorNumber());
                        return null;
                    } catch (SQLException ex) {
                        throw new DatabaseActionException(ex);
                    }
                }, form.name() + " room updated"));
    }

    private void deleteRoom(Room room) {
        if (room.deviceCount() > 0) {
            JOptionPane.showMessageDialog(this,
                "This room has " + room.deviceCount() + " assigned device(s). Move or delete those devices before deleting the room.",
                "Room cannot be deleted", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Delete the room \"" + room.name() + "\"?",
                "Confirm room deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) return;
        runDatabaseAction("Deleting " + room.name() + "…",
            "Could not delete room. Rooms with assigned devices cannot be deleted.",
            () -> {
                try {
                    homeService.deleteRoom(room.id());
                    return null;
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, room.name() + " room deleted");
    }

    private void selectRoom(Room room) {
        selectedRoom = room;
        roomsPanel.showLoadingDevices(room);
        status.setText("Loading devices in " + room.name() + "…");
        new SwingWorker<List<Device>, Void>() {
            @Override protected List<Device> doInBackground() throws SQLException {
                return homeService.findDevicesByRoom(room.id());
            }

            @Override protected void done() {
                try {
                    List<Device> roomDevices = get();
                    if (selectedRoom == null || selectedRoom.id() != room.id()) return;
                    roomsPanel.setSelectedRoom(room, roomDevices);
                    status.setText(roomDevices.size() + " device(s) in " + room.name());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Loading room devices was interrupted.", ex);
                } catch (ExecutionException ex) {
                    roomsPanel.setLoadError("Could not load devices for " + room.name() + ".");
                    showError("Could not load devices for " + room.name() + ".", ex.getCause());
                    status.setText("Could not load room devices");
                }
            }
        }.execute();
    }

    private void updateViews() {
        devicesPanel.setDevices(devices);
        devicesPanel.setRoomOptions(roomNames);
        roomsPanel.setRooms(managedRooms);
        energyPanel.setRooms(managedRooms);
        dashboard.setData(devices, rooms, List.copyOf(activity), this::toggleDevice);
        dashboard.setSensors(sensorReadings);
        dashboard.setLastRefreshed(java.time.Instant.now());
        if (selectedRoom != null) {
            Room current = managedRooms.stream().filter(room -> room.id() == selectedRoom.id()).findFirst().orElse(null);
            if (current == null) {
                selectedRoom = null;
                roomsPanel.setSelectedRoom(null, List.of());
            } else {
                selectedRoom = current;
                selectRoom(current);
            }
        }
        status.setText(devices.isEmpty() ? "No devices found — add one to get started" : devices.size() + " device(s) loaded");
        if ("Energy Usage".equals(currentPage)) loadEnergyReport(energyPanel.filter());
    }

    private void loadEnergyReport(EnergyPanel.EnergyFilter filter) {
        status.setText("Calculating energy estimates from recorded runtime…");
        energyPanel.setLoading();
        new SwingWorker<EnergyReport, Void>() {
            @Override protected EnergyReport doInBackground() throws SQLException {
                return energyDao.loadReport(filter.fromDate(), filter.toDate(), filter.interval(), filter.roomId());
            }

            @Override protected void done() {
                try {
                    energyPanel.setReport(get());
                    status.setText("Energy report updated · rated-power runtime estimate");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    energyPanel.setLoadError("Energy report loading was interrupted.");
                    showError("Energy report loading was interrupted.", ex);
                } catch (ExecutionException ex) {
                    energyPanel.setLoadError("Energy data is unavailable. Check the database and migration.");
                    showError("Could not load energy data. Apply the energy-history migration and check MySQL.", ex.getCause());
                    status.setText("Energy data unavailable");
                }
            }
        }.execute();
    }

    private void saveEnergyTariff(BigDecimal tariff) {
        runDatabaseAction("Saving electricity tariff…", "Could not save electricity tariff.",
            () -> {
                try {
                    energyDao.updateTariff(tariff);
                    return "Tariff saved: " + tariff.toPlainString() + " per kWh.";
                } catch (SQLException ex) {
                    throw new DatabaseActionException(ex);
                }
            }, "Electricity tariff updated");
    }

    private void exportEnergyReport() {
        EnergyReport report = energyPanel.report();
        if (report == null) {
            JOptionPane.showMessageDialog(this, "Load an energy report before exporting.", "No report",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("energy-usage.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path output = chooser.getSelectedFile().toPath();
        if (!output.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
            output = output.resolveSibling(output.getFileName() + ".csv");
        }
        Path target = output;
        status.setText("Exporting energy report…");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws IOException {
                try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                    writer.write("Report type,Label,Value,Unit,Estimated cost");
                    writer.newLine();
                    writeCsvRow(writer, "Range", report.fromDate() + " to " + report.toDate(), "", "", "");
                    writeCsvRow(writer, "Data availability",
                        report.historyAvailable() ? "Tracked runtime available; energy values are estimates"
                            : "No tracked runtime available for the selected date range", "", "", "");
                    writeCsvRow(writer, "Method", "Rated power multiplied by tracked ON runtime; estimate only", "", "", "");
                    writeCsvRow(writer, "Active rated power", "All selected devices currently ON",
                        report.activeRatedWatts().toPlainString(), "W", "");
                    if (report.tariffPerKwh() != null) {
                        writeCsvRow(writer, "Tariff", "Configured tariff", report.tariffPerKwh().toPlainString(), "per kWh", "");
                    }
                    for (var bucket : report.consumption()) {
                        String bucketCost = report.tariffPerKwh() == null ? ""
                            : bucket.kilowattHours().multiply(report.tariffPerKwh())
                                .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
                        writeCsvRow(writer, "Estimated consumption", bucket.label(),
                            bucket.kilowattHours().toPlainString(), "kWh", bucketCost);
                    }
                    for (var breakdown : report.activeByRoom()) {
                        writeCsvRow(writer, "Active power by room", breakdown.label(), breakdown.watts().toPlainString(), "W", "");
                    }
                    for (var breakdown : report.activeByType()) {
                        writeCsvRow(writer, "Active power by device type", breakdown.label(), breakdown.watts().toPlainString(), "W", "");
                    }
                    if (report.historyAvailable()) {
                        writeCsvRow(writer, "Total estimated energy", "Selected date range",
                            report.estimatedKilowattHours().toPlainString(), "kWh",
                            report.estimatedCost() == null ? "" : report.estimatedCost().toPlainString());
                    }
                }
                return null;
            }

            @Override protected void done() {
                try {
                    get();
                    status.setText("Energy report exported to " + target.getFileName());
                    JOptionPane.showMessageDialog(MainFrame.this, "CSV report exported successfully.",
                        "Export complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("CSV export was interrupted.", ex);
                } catch (ExecutionException ex) {
                    showError("Could not export energy report.", ex.getCause());
                    status.setText("CSV export failed");
                }
            }
        }.execute();
    }

    private void writeCsvRow(BufferedWriter writer, String... columns) throws IOException {
        for (int index = 0; index < columns.length; index++) {
            if (index > 0) writer.write(',');
            writer.write('"');
            writer.write(columns[index].replace("\"", "\"\""));
            writer.write('"');
        }
        writer.newLine();
    }

    private void showError(String message, Throwable cause) {
        notifications.addError(message);
        cause.printStackTrace();
        Throwable detail = cause instanceof DatabaseActionException && cause.getCause() != null ? cause.getCause() : cause;
        JOptionPane.showMessageDialog(this, message + "\nDetails: " + detail.getMessage(),
            "Smart Home error", JOptionPane.ERROR_MESSAGE);
    }

    private record AppData(List<Device> devices, List<RoomSummary> rooms, List<String> roomNames,
            List<Room> managedRooms, List<SensorReading> sensorReadings) {}
    private record SensorMonitoringData(List<SensorReading> latest, List<SensorReading> history,
            List<SensorAlert> alerts) {}
    private record ScheduleData(List<DeviceSchedule> schedules, List<ScheduleExecution> history) {}
    private record AutomationData(List<AutomationRule> rules,
            List<AutomationDao.AutomationEvaluation> evaluations) {}

    private static final class DatabaseActionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        DatabaseActionException(SQLException cause) { super(cause); }
    }
}