package com.smarthome.ui;

import com.smarthome.dao.DeviceDao;
import com.smarthome.dao.EnergyUsageDao;
import com.smarthome.dao.ActivityLogDao;
import com.smarthome.dao.RoomDao;
import com.smarthome.dao.ScheduleDao;
import com.smarthome.model.Device;
import com.smarthome.model.ActivityLog;
import com.smarthome.model.DeviceSchedule;
import com.smarthome.model.EnergyReport;
import com.smarthome.model.Room;
import com.smarthome.model.RoomSummary;
import com.smarthome.model.ScheduleExecution;
import com.smarthome.service.ScheduleService;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
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
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;

public class MainFrame extends JFrame {
    private final DeviceDao dao = new DeviceDao();
    private final ActivityLogDao activityLogDao = new ActivityLogDao();
    private final RoomDao roomDao = new RoomDao();
    private final EnergyUsageDao energyDao = new EnergyUsageDao();
    private final ScheduleDao scheduleDao = new ScheduleDao(dao);
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pageHost = new JPanel(pageLayout);
    private final Sidebar sidebar = new Sidebar(this::showPage);
    private final NotificationPanel notifications = new NotificationPanel();
    private final AppHeader header = new AppHeader(event -> refreshData(), notifications);
    private final DashboardPanel dashboard = new DashboardPanel(this::toggleDevice);
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
    private final JLabel status = new JLabel("Connecting to your home database…");
    private final Deque<String> activity = new ArrayDeque<>();
    private List<Device> devices = List.of();
    private List<RoomSummary> rooms = List.of();
    private List<String> roomNames = List.of();
    private List<Room> managedRooms = List.of();
    private Room selectedRoom;
    private String currentPage = "Dashboard";
    private ActivityLogPanel.Filter activityFilter =
        new ActivityLogPanel.Filter("", "ALL", null, null, 0);
    private final ScheduleService scheduleService;

    public MainFrame() {
        super("Smart Living | Smart Home Management");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        scheduleService = new ScheduleService(scheduleDao,
            () -> SwingUtilities.invokeLater(this::refreshAfterBackgroundActivity), this::onScheduleServiceError);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                status.setText("Stopping the schedule service…");
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        scheduleService.close();
                        return null;
                    }
                    @Override protected void done() {
                        try {
                            get();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException ex) {
                            ex.getCause().printStackTrace();
                        }
                        dispose();
                    }
                }.execute();
            }
        });
        setMinimumSize(new java.awt.Dimension(1060, 700));
        setSize(1280, 820);
        setLocationRelativeTo(null);
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
        pageHost.add(schedulesPanel, "Schedules");
        pageHost.add(activityLogPanel, "Activity Logs");
        pageHost.add(placeholder("Settings"), "Settings");
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
        scheduleService.start();
        refreshData();
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
        currentPage = page;
        sidebar.select(page);
        header.setTitle(page);
        pageLayout.show(pageHost, page);
        if ("Energy Usage".equals(page)) loadEnergyReport(energyPanel.filter());
        if ("Schedules".equals(page)) loadSchedules();
        if ("Activity Logs".equals(page)) loadActivityPage(activityFilter);
    }

    private ActivityLogPanel.Filter currentActivityFilter() {
        return activityFilter;
    }

    private void loadActivityPage(ActivityLogPanel.Filter filter) {
        activityFilter = filter;
        new SwingWorker<ActivityLogDao.Page, Void>() {
            @Override protected ActivityLogDao.Page doInBackground() throws SQLException {
                return activityLogDao.findPage(filter.search(), filter.eventType(), filter.from(),
                    filter.through(), filter.offset(), ActivityLogDao.PAGE_SIZE);
            }

            @Override protected void done() {
                try {
                    activityLogPanel.setPage(get());
                    if ("Activity Logs".equals(currentPage)) status.setText("Activity log page loaded");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Loading activity logs was interrupted.", ex);
                } catch (ExecutionException ex) {
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
                return activityLogDao.findRecent(10);
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
        refreshData();
        if ("Activity Logs".equals(currentPage)) loadActivityPage(activityFilter);
    }

    private void refreshData() {
        status.setText("Loading devices and room data…");
        new SwingWorker<AppData, Void>() {
            @Override protected AppData doInBackground() throws SQLException {
                List<Room> roomList = roomDao.findAll();
                List<RoomSummary> summaries = roomList.stream()
                    .map(room -> new RoomSummary(room.id(), room.name(), room.deviceCount(), room.activeDeviceCount()))
                    .toList();
                List<String> names = roomList.stream().map(Room::name).toList();
                return new AppData(dao.findAll(), summaries, names, roomList);
            }

            @Override protected void done() {
                try {
                    AppData data = get();
                    devices = data.devices();
                    rooms = data.rooms();
                    roomNames = data.roomNames();
                    managedRooms = data.managedRooms();
                    updateViews();
                    loadSchedules();
                    loadNotifications();
                    if ("Activity Logs".equals(currentPage)) loadActivityPage(activityFilter);
                    status.setText("Data refreshed from MySQL");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Refresh was interrupted.", ex);
                } catch (ExecutionException ex) {
                    showError("Could not load smart-home data. Check the database connection and schema.", ex.getCause());
                }
            }
        }.execute();
    }

    private void loadSchedules() {
        new SwingWorker<ScheduleData, Void>() {
            @Override protected ScheduleData doInBackground() throws SQLException {
                return new ScheduleData(scheduleDao.findAll(), scheduleDao.findHistory(200));
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
                    if ("Schedules".equals(currentPage)) showError("Loading schedules was interrupted.", ex);
                } catch (ExecutionException ex) {
                    status.setText("Schedule data unavailable — apply the V3 schedules migration");
                    if ("Schedules".equals(currentPage)) {
                        showError("Could not load schedules. Apply database/migrations/V3__device_schedules.sql.",
                            ex.getCause());
                    }
                }
            }
        }.execute();
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
                    scheduleDao.create(form.deviceId(), form.name(), form.action(), form.pattern(),
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
                    scheduleDao.update(existing.id(), form.deviceId(), form.name(), form.action(), form.pattern(),
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
                    scheduleDao.setEnabled(schedule.id(), enable, java.time.Instant.now());
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
                    scheduleDao.delete(schedule.id());
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
                        activityLogDao.recordFailure(ActivityLog.failure("OPERATION_FAILED", null, null, null,
                            java.time.Instant.now(), detail));
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
                    dao.setStatus(device.getId(), next);
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
                    int changed = dao.setAllStatus(requestedStatus);
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
                    dao.delete(device.getId());
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
                                    dao.add(deviceForm.name(), deviceForm.type(), roomIdFor(deviceForm.roomName()),
                                        "OFF", deviceForm.powerWatts());
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
                                    dao.update(device.getId(), deviceForm.name(), deviceForm.type(),
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
                        roomDao.add(form.name(), form.floorNumber());
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
                        roomDao.update(room.id(), form.name(), form.floorNumber());
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
                    roomDao.delete(room.id());
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
                return dao.findByRoomId(room.id());
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
                    showError("Energy report loading was interrupted.", ex);
                } catch (ExecutionException ex) {
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
            List<Room> managedRooms) {}
    private record ScheduleData(List<DeviceSchedule> schedules, List<ScheduleExecution> history) {}

    private static final class DatabaseActionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        DatabaseActionException(SQLException cause) { super(cause); }
    }
}