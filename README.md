# Smart Home Management System

A Java Swing + MySQL mini-project for managing smart-home devices.

## Features
- View devices, room, type, status and estimated power draw
- Turn a device ON/OFF
- Add devices and delete selected devices
- Dashboard summary of total devices, active devices and estimated active load
- JDBC prepared statements and a simple DAO/model separation

## Requirements
- JDK 25 LTS+
- Maven 3.8+
- MySQL 8+

## Setup
1. Open MySQL Workbench or a MySQL terminal.
2. Run `database/smart_home.sql`. This creates `smart_home_db`, rooms, devices and sample rows.
3. Configure database credentials. Defaults are `root` with an empty password. Recommended: set environment variables instead:
   - `SMART_HOME_DB_URL` (optional; default `jdbc:mysql://localhost:3306/smart_home_db?useSSL=false&serverTimezone=UTC`)
   - `SMART_HOME_DB_USER`
   - `SMART_HOME_DB_PASSWORD`
4. From the project directory run:
   ```bash
   mvn clean package
   mvn exec:java -Dexec.mainClass=com.smarthome.Main
   ```
   If you do not have the Maven exec plugin installed, run with your IDE: import as a Maven project and run `com.smarthome.Main`.

   Alternatively, compile/package with `mvn clean package`, then run:
   ```bash
   java -cp target/smart-home-management-1.0.0.jar com.smarthome.Main
   ```
   The last command also needs MySQL Connector/J on the runtime classpath; easiest is to run from your IDE or use Maven exec.

## Project structure
```
src/main/java/com/smarthome/
  Main.java
  db/Database.java
  model/Device.java
  dao/DeviceDao.java
  ui/DashboardFrame.java
database/smart_home.sql
```

## Database relationship
- One room can contain many devices.
- Each device belongs to one room (`devices.room_id -> rooms.room_id`).

## Notes / future improvements
This is a working starter PBL, not a real IoT controller. ON/OFF is stored in MySQL and does not operate physical devices. For production, add authentication/roles, validation, audit logs, connection pooling, and an IoT integration layer.
