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
   SMART_HOME_DB_URL=jdbc:mysql://localhost:3306/smart_home_db?useSSL=false&serverTimezone=UTC
   SMART_HOME_DB_USER=root
   SMART_HOME_DB_PASSWORD=your-local-password
   ```
   You may instead provide these values as environment variables; environment values take precedence. The application does not include a default password and will show a configuration error when the password is blank.
3. From the project directory:
   ```powershell
   mvn clean verify
   mvn exec:java -Dexec.mainClass=com.smarthome.Main
   ```
   Or open the Maven project in an IDE and run `com.smarthome.Main`.

## Implemented
- MySQL-backed device list with type icons, per-row ON/OFF controls, add/edit/delete, name search, room/type/status filters, and confirmed All ON / All OFF operations.
- Device name, type, room, and rated power validation; duplicate names are checked before writes.
- MySQL-backed Rooms management with room cards, floor and device/active counts, add/edit/delete, and room-specific device details. Rooms with assigned devices cannot be deleted.
- Energy Usage analytics with current ON rated load by room/type, daily/weekly/monthly estimated kWh charts, configurable tariff/cost, date/room filters, and CSV export.
- Energy runtime and status changes are recorded as transactional MySQL usage sessions and status-history events.
- Persistent device schedules with one-time, daily, and selected-weekday recurrence; enable/disable, edit, delete, and execution history.
- A background scheduler executes due schedules and updates device/energy history transactionally. Schedules execute only while this desktop application is running.
- Activity Logs record successful device status, create/update/delete, schedule execution, missed schedules, and failed operations. The Activity Logs screen supports search, date/event filters, and 50-row paging; the header notification menu shows recent actions and errors.
- Dashboard statistics and room summaries queried from MySQL.
- Device and room database work runs in `SwingWorker`, not on the Swing Event Dispatch Thread. Bulk status changes use a transaction.
- The dashboard's compact recent-activity list is session-only; detailed Activity Logs and persisted notifications are stored in MySQL.

## Database migrations
For an existing database, run these scripts once, in order, in MySQL Workbench or the MySQL client before launching the updated application:
1. `database/migrations/V2__energy_usage_history.sql`
2. `database/migrations/V3__device_schedules.sql`
3. `database/migrations/V4__activity_logs.sql`

Fresh databases can be created by running the updated `database/smart_home.sql`.

### Energy estimates
The V2 migration adds:
- `energy_usage_sessions`, storing rated-power and room/type snapshots for each ON interval.
- `device_status_history`, recording ON/OFF changes and timestamps.
- `energy_settings`, storing the configured tariff and the earliest tracking timestamp.

The app records these rows transactionally when device state changes through the application. Existing devices already ON when the migration runs receive a session beginning at migration time; runtime before that point cannot be reconstructed. Changes made directly in MySQL or by older application builds are not tracked. Room/type/rated power values are snapshotted when an ON session begins and active sessions are split when a device’s room, type, or rated power is edited.

Energy is calculated as `rated watts × tracked ON duration in hours ÷ 1000`. This is an estimate assuming the device draws its configured rated power continuously while ON. It is not measured consumption. Actual measurement requires time-stamped meter/smart-plug kWh readings, which the current system does not collect. Cost is estimated kWh multiplied by the user-configured tariff; taxes, time-of-use pricing, and fixed charges are not included. Currency is not inferred or converted—the displayed amount uses the unit of the configured tariff.

### Scheduling behavior
V3 creates `device_schedules` and `schedule_execution_log`. Scheduling times use the local timezone captured when a schedule is created and shown in the schedule editor. Java's timezone rules resolve nonexistent times during a daylight-saving spring-forward gap by moving the time forward by the gap; ambiguous fall-back times use the earlier offset, and each local occurrence executes once. After restart, overdue recurring occurrences are skipped in favor of the next future occurrence; overdue one-time schedules are marked `MISSED`. The desktop scheduler is not an always-on service: schedules do not run while the application is closed. Execution history is retained when a schedule is deleted. Scheduled actions change the database state and energy history but do not operate physical IoT devices.

Settings remains a placeholder. Activity logs have no automatic deletion: the application retains them indefinitely and leaves history intact when a device or schedule is deleted (foreign keys are set to `NULL`, while the name snapshots remain). If the table grows significantly, archive entries older than 24 months to a separately backed-up archive and verify that archive before manually pruning the live table. No user-account tables or authentication exist, so entries are not currently associated with a user.

## Package structure
- `com.smarthome`: application entry point.
- `com.smarthome.db`: JDBC connection and local `.env` configuration.
- `com.smarthome.dao`: prepared-statement data access for devices, rooms, energy reports, schedules, and activity logs.
- `com.smarthome.model`: device, room, energy-report, schedule, and activity-log data models.
- `com.smarthome.service`: energy aggregation/estimation and the recurrence/scheduler services.
- `com.smarthome.ui`: main frame, reusable dashboard/device/room/energy/schedule/activity screens, notifications, and validated dialogs.

The existing schema and database relationship are preserved: each device belongs to one room via `devices.room_id`.

Device name writes are serialized and checked for duplicates by the DAO. The existing schema does not enforce a unique device-name constraint, so writes made directly by other database clients can bypass the application-level check.
