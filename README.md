# Smart Home Management System

A Java Swing desktop dashboard backed by MySQL and JDBC.

## Requirements
- JDK 26
- Maven 3.8+
- MySQL 8+

## Setup and run
1. Run `database/smart_home.sql` in MySQL Workbench or a MySQL terminal.
2. Set your database credentials in the project-root `.env` file. The file is ignored by Git:
   ```dotenv
   SMART_HOME_DB_URL=jdbc:mysql://localhost:3306/smart_home_db?sslMode=DISABLED&serverTimezone=UTC
   SMART_HOME_DB_USER=your-database-user
   SMART_HOME_DB_PASSWORD=your-local-password
   ```
   You may instead provide these values as environment variables; environment values take precedence. The application does not include a default password and will show a configuration error when the password is blank.
   `sslMode=DISABLED` is only suitable for a database on the same machine or a trusted local tunnel. For a remote MySQL server, use `sslMode=VERIFY_IDENTITY` with a trusted certificate whose hostname matches the database server.
3. For an existing database, first run the V2 through V7 migrations in order (see below). For a fresh database, `smart_home.sql` contains the complete schema. From a terminal, create the first administrator:
   ```powershell
   mvn exec:java "-Dexec.args=--create-admin"
   ```
   This command prompts for the account password with terminal no-echo input. It refuses to proceed if an administrator already exists. Do not create an administrator by editing the role or password hash directly in SQL.
4. From the project directory:
   ```powershell
   mvn clean verify
   mvn exec:java
   ```
   The Maven exec plugin is configured to launch `com.smarthome.Main`. In VS Code, open the repository folder, install the Extension Pack for Java, let Maven import the project, and run `com.smarthome.Main` from `src/main/java/com/smarthome/Main.java`. Use a terminal in the repository root for the administrator bootstrap and Maven commands.

## Academic project documentation
The implementation-based project report, editable Mermaid diagrams, database table reference and SQL schema copy are in [`docs/`](docs/README.md). Student-identifying details and manual screenshots are deliberately left for the student to provide or capture.

## Implemented
- MySQL-backed device list with type icons, per-row ON/OFF controls, add/edit/delete, name search, room/type/status filters, and confirmed All ON / All OFF operations.
- Device name, type, room, and rated power validation; duplicate names are checked before writes.
- MySQL-backed Rooms management with room cards, floor and device/active counts, add/edit/delete, and room-specific device details. Rooms with assigned devices cannot be deleted.
- Energy Usage analytics with current ON rated load by room/type, daily/weekly/monthly estimated kWh charts, configurable tariff/cost, date/room filters, and CSV export.
- Energy runtime and status changes are recorded as transactional MySQL usage sessions and status-history events.
- Persistent device schedules with one-time, daily, and selected-weekday recurrence; enable/disable, edit, delete, and execution history.
- A background scheduler executes due schedules and updates device/energy history transactionally. Schedules execute only while this desktop application is running.
- Activity Logs record successful device status, create/update/delete, schedule execution, missed schedules, and failed operations. The Activity Logs screen supports search, date/event filters, and 50-row paging; the header notification menu shows recent actions and errors.
- Authentication uses Argon2id password hashes with per-password random salt; public registration assigns RESIDENT. The backend management service enforces role permissions for writes.
- Password changes increment the account session version. Other sessions become unauthorized on their next operation; the session changing its own password continues with its updated version.
- ADMIN can manage devices, rooms, schedules, automation rules, and user accounts. RESIDENT can view dashboard/device/room/energy screens, view sensor readings, configure local monitoring/simulation preferences, and control devices; administration, audit logs, schedules, and automation rules are restricted to administrators. Settings exposes profile, password change, and logout.
- Light and dark appearances are available in Settings and saved locally per account using Java Preferences. The sidebar uses a persistent active-page indicator; Alt+1 through Alt+9 navigate the first nine pages and Alt+0 opens Settings. The application and sign-in dialog share a generated icon, and the header includes an About dialog.
- Shared Swing helpers standardize cards, buttons, forms, tables, spacing, and theme colors. Data screens show loading, empty, and failure states; keyboard focus follows normal Swing traversal and form labels are associated with their controls.
- Dashboard statistics and room summaries queried from MySQL.
- Live dashboard polling uses a configurable Swing Timer interval (5–60 seconds) with one background refresh at a time, inline last-refresh status, and migration/connection error feedback.
- Optional room temperature, humidity, and motion simulation stores timestamped readings in MySQL. Simulated readings are explicitly labelled; simulation is off by default and its sampling interval can be configured.
- Sensor Monitoring provides room/type filters, latest room values, recent reading history, an in-app trend plot, persistent temperature/humidity threshold alerts, and Start/Stop Simulation controls.
- ADMIN-only Automation Rules support temperature-above, humidity-above, motion-detected, and daily local-time conditions; rules target existing devices with a typed ON/OFF action, cooldown and persisted transition state.
- The background automation engine logs condition transitions and attempted actions, avoids retriggering while sensor conditions remain true, and shuts down with the schedule service and sensor simulator.
- Device and room database work runs in `SwingWorker`, not on the Swing Event Dispatch Thread. Bulk status changes use a transaction.
- The dashboard's compact recent-activity list is session-only; detailed Activity Logs and persisted notifications are stored in MySQL.

## Database migrations
For an existing database, run these scripts once, in order, in MySQL Workbench or the MySQL client before launching the updated application. Select **File → Open SQL Script** for each file and execute them in this order; migrations require the base `rooms` and `devices` tables from the existing database:
1. `database/migrations/V2__energy_usage_history.sql`
2. `database/migrations/V3__device_schedules.sql`
3. `database/migrations/V4__activity_logs.sql`
4. `database/migrations/V5__user_authentication.sql`
5. `database/migrations/V6__revoke_sessions_on_password_change.sql`
6. `database/migrations/V7__monitoring_and_automation.sql`
7. `database/migrations/V8__sensor_alerts.sql`

Fresh databases can be created by running `database/smart_home.sql` only; it already contains the V2–V8 schema. Do not run the migrations after the fresh-install script; migration scripts are one-time upgrades and are not all idempotent. V8 itself uses idempotent table creation and default threshold seeding.

### Real-time monitoring and automation
Dashboard refresh checks are scheduled on Swing's event thread, but MySQL reads run in a background `SwingWorker`; a guard prevents simultaneous dashboard refresh workers. The per-user refresh interval is stored locally in Java Preferences. Sensor simulation is disabled by default. When enabled, a background service stores plausible temperature, humidity, and motion samples for each room at the configured interval. Every generated row is marked `simulated=TRUE`; the project does not currently read physical sensors. The automation evaluator ignores recent simulated readings while simulation is disabled.

The V7 migration creates `sensor_readings`, `automation_rules`, and `automation_rule_evaluations`. V8 adds editable temperature/humidity thresholds, persistent alert history, and per-room/type alert state; an alert is raised only on the first reading above its threshold and resolves when a reading returns to or below it. The Sensor Monitoring page can filter readings by room and type and plots the selected type without an added chart dependency. Automation rules are typed data, not scripts: no SQL or shell command can be configured. Temperature rules require a device type containing “climate” or “cool”; motion rules require a Light-type target and ON action. Threshold and motion rules run on false-to-true transitions and persist that state, with cooldown as an additional guard. Scheduled-time rules run at most once per local calendar date; daylight-saving gaps resolve forward using Java's timezone rules. Evaluation history records condition transitions, scheduled occurrences, suppressed attempts, and outcomes rather than every five-second polling pass. Rules execute only while the desktop application is running, and actions update database device state rather than controlling physical devices.

Sensor sample and automation evaluation history currently have no automatic retention. Simulated readings are data for demonstration/testing, not a substitute for calibrated sensor input.

### Energy estimates
The V2 migration adds:
- `energy_usage_sessions`, storing rated-power and room/type snapshots for each ON interval.
- `device_status_history`, recording ON/OFF changes and timestamps.
- `energy_settings`, storing the configured tariff and the earliest tracking timestamp.

The app records these rows transactionally when device state changes through the application. Existing devices already ON when the migration runs receive a session beginning at migration time; runtime before that point cannot be reconstructed. Changes made directly in MySQL or by older application builds are not tracked. Room/type/rated power values are snapshotted when an ON session begins and active sessions are split when a device’s room, type, or rated power is edited.

Energy is calculated as `rated watts × tracked ON duration in hours ÷ 1000`. This is an estimate assuming the device draws its configured rated power continuously while ON. It is not measured consumption. Actual measurement requires time-stamped meter/smart-plug kWh readings, which the current system does not collect. Cost is estimated kWh multiplied by the user-configured tariff; taxes, time-of-use pricing, and fixed charges are not included. Currency is not inferred or converted—the displayed amount uses the unit of the configured tariff.

### Scheduling behavior
V3 creates `device_schedules` and `schedule_execution_log`. Scheduling times use the local timezone captured when a schedule is created and shown in the schedule editor. Java's timezone rules resolve nonexistent times during a daylight-saving spring-forward gap by moving the time forward by the gap; ambiguous fall-back times use the earlier offset, and each local occurrence executes once. After restart, overdue recurring occurrences are skipped in favor of the next future occurrence; overdue one-time schedules are marked `MISSED`. The desktop scheduler is not an always-on service: schedules do not run while the application is closed. Execution history is retained when a schedule is deleted. Scheduled actions change the database state and energy history but do not operate physical IoT devices.

Activity logs have no automatic deletion: the application retains them indefinitely and leaves history intact when a device, schedule, or account is deleted (foreign keys are set to `NULL`, while name snapshots remain). If the table grows significantly, archive entries older than 24 months to a separately backed-up archive and verify that archive before manually pruning the live table.

## Package structure
- `com.smarthome`: application entry point.
- `com.smarthome.db`: JDBC connection and local `.env` configuration.
- `com.smarthome.dao`: prepared-statement data access for users, devices, rooms, energy reports, schedules, and activity logs.
- `com.smarthome.model`: authenticated users and roles alongside device, room, energy-report, schedule, and activity-log data models.
- `com.smarthome.service`: authentication/Argon2id password hashing, authorization and protected management operations, energy estimates, and scheduling.
- `com.smarthome.ui`: login/registration, profile, admin user management, and the existing dashboard/device/room/energy/schedule/activity screens.

The existing schema and database relationship are preserved: each device belongs to one room via `devices.room_id`.

Device name writes are serialized and checked for duplicates by the DAO. The existing schema does not enforce a unique device-name constraint, so writes made directly by other database clients can bypass the application-level check.
