CREATE DATABASE IF NOT EXISTS smart_home_db;
USE smart_home_db;

CREATE TABLE IF NOT EXISTS rooms (
  room_id INT PRIMARY KEY AUTO_INCREMENT,
  room_name VARCHAR(80) NOT NULL UNIQUE,
  floor_no INT NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS devices (
  device_id INT PRIMARY KEY AUTO_INCREMENT,
  device_name VARCHAR(100) NOT NULL,
  device_type VARCHAR(40) NOT NULL,
  room_id INT NOT NULL,
  status ENUM('ON','OFF') NOT NULL DEFAULT 'OFF',
  power_watts DECIMAL(8,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_device_room FOREIGN KEY (room_id) REFERENCES rooms(room_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS energy_usage_sessions (
  session_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id INT NOT NULL,
  device_name VARCHAR(100) NOT NULL,
  device_type VARCHAR(40) NOT NULL,
  room_id INT NOT NULL,
  room_name VARCHAR(80) NOT NULL,
  power_watts DECIMAL(8,2) NOT NULL,
  started_at DATETIME(6) NOT NULL,
  ended_at DATETIME(6) NULL,
  open_device_id INT GENERATED ALWAYS AS (CASE WHEN ended_at IS NULL THEN device_id ELSE NULL END) STORED,
  CONSTRAINT uq_energy_open_device UNIQUE (open_device_id),
  INDEX idx_energy_session_time (started_at, ended_at),
  INDEX idx_energy_session_room_time (room_id, started_at, ended_at)
);

CREATE TABLE IF NOT EXISTS device_status_history (
  history_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id INT NOT NULL,
  device_name VARCHAR(100) NOT NULL,
  device_type VARCHAR(40) NOT NULL,
  room_id INT NOT NULL,
  room_name VARCHAR(80) NOT NULL,
  status ENUM('ON','OFF') NOT NULL,
  power_watts DECIMAL(8,2) NOT NULL,
  changed_at DATETIME(6) NOT NULL,
  INDEX idx_device_status_history_time (changed_at),
  INDEX idx_device_status_history_device (device_id, changed_at)
);

CREATE TABLE IF NOT EXISTS energy_settings (
  setting_id TINYINT PRIMARY KEY,
  tariff_per_kwh DECIMAL(10,4) NULL,
  history_started_at DATETIME(6) NOT NULL
);

INSERT IGNORE INTO energy_settings(setting_id, tariff_per_kwh, history_started_at)
VALUES (1, NULL, UTC_TIMESTAMP(6));

CREATE TABLE IF NOT EXISTS device_schedules (
  schedule_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id INT NOT NULL,
  schedule_name VARCHAR(120) NOT NULL,
  action ENUM('ON','OFF') NOT NULL,
  repeat_type ENUM('ONCE','DAILY','WEEKDAYS') NOT NULL,
  start_date DATE NOT NULL,
  local_time TIME NOT NULL,
  timezone_id VARCHAR(64) NOT NULL,
  weekdays_mask TINYINT UNSIGNED NOT NULL DEFAULT 0,
  next_run_at DATETIME(6) NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT UTC_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT UTC_TIMESTAMP(6),
  CONSTRAINT fk_schedule_device FOREIGN KEY (device_id) REFERENCES devices(device_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  INDEX idx_schedule_due (enabled, next_run_at),
  INDEX idx_schedule_device (device_id, enabled)
);

CREATE TABLE IF NOT EXISTS schedule_execution_log (
  execution_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  schedule_id BIGINT NULL,
  schedule_name VARCHAR(120) NOT NULL,
  device_name VARCHAR(100) NOT NULL,
  action ENUM('ON','OFF') NOT NULL,
  occurrence_at DATETIME(6) NOT NULL,
  executed_at DATETIME(6) NULL,
  result ENUM('SUCCESS','FAILED','MISSED') NOT NULL,
  details VARCHAR(1000) NULL,
  CONSTRAINT fk_schedule_log_schedule FOREIGN KEY (schedule_id) REFERENCES device_schedules(schedule_id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT uq_schedule_occurrence UNIQUE (schedule_id, occurrence_at),
  INDEX idx_schedule_log_time (occurrence_at),
  INDEX idx_schedule_log_schedule_time (schedule_id, occurrence_at)
);

CREATE TABLE IF NOT EXISTS activity_logs (
  activity_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_type VARCHAR(40) NOT NULL,
  device_id INT NULL,
  device_name VARCHAR(100) NULL,
  schedule_id BIGINT NULL,
  occurred_at DATETIME(6) NOT NULL,
  outcome ENUM('SUCCESS','FAILED','MISSED') NOT NULL,
  details VARCHAR(1000) NOT NULL,
  CONSTRAINT fk_activity_device FOREIGN KEY (device_id) REFERENCES devices(device_id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_activity_schedule FOREIGN KEY (schedule_id) REFERENCES device_schedules(schedule_id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  INDEX idx_activity_time (occurred_at, activity_id),
  INDEX idx_activity_type_time (event_type, occurred_at),
  INDEX idx_activity_device_time (device_id, occurred_at)
);

INSERT IGNORE INTO rooms(room_name, floor_no) VALUES
 ('Living Room',1),('Kitchen',1),('Bedroom',2),('Study Room',2);

INSERT INTO devices(device_name, device_type, room_id, status, power_watts)
SELECT 'Main Light','Light',room_id,'ON',12 FROM rooms WHERE room_name='Living Room'
AND NOT EXISTS (SELECT 1 FROM devices WHERE device_name='Main Light');
INSERT INTO devices(device_name, device_type, room_id, status, power_watts)
SELECT 'Air Conditioner','Climate',room_id,'OFF',1200 FROM rooms WHERE room_name='Bedroom'
AND NOT EXISTS (SELECT 1 FROM devices WHERE device_name='Air Conditioner');
INSERT INTO devices(device_name, device_type, room_id, status, power_watts)
SELECT 'Smart TV','Entertainment',room_id,'OFF',95 FROM rooms WHERE room_name='Living Room'
AND NOT EXISTS (SELECT 1 FROM devices WHERE device_name='Smart TV');

INSERT INTO energy_usage_sessions
  (device_id, device_name, device_type, room_id, room_name, power_watts, started_at)
SELECT d.device_id, d.device_name, d.device_type, d.room_id, r.room_name, d.power_watts, UTC_TIMESTAMP(6)
FROM devices d
JOIN rooms r ON r.room_id=d.room_id
WHERE d.status='ON'
ON DUPLICATE KEY UPDATE session_id=session_id;