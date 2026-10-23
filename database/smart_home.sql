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

CREATE TABLE IF NOT EXISTS app_users (
  user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('ADMIN','RESIDENT') NOT NULL DEFAULT 'RESIDENT',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  session_version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_app_users_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS device_schedules (
  schedule_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id INT NOT NULL,
  created_by_user_id BIGINT NULL,
  schedule_name VARCHAR(120) NOT NULL,
  action ENUM('ON','OFF') NOT NULL,
  repeat_type ENUM('ONCE','DAILY','WEEKDAYS') NOT NULL,
  start_date DATE NOT NULL,
  local_time TIME NOT NULL,
  timezone_id VARCHAR(64) NOT NULL,
  weekdays_mask TINYINT UNSIGNED NOT NULL DEFAULT 0,
  next_run_at DATETIME(6) NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_schedule_device FOREIGN KEY (device_id) REFERENCES devices(device_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_schedule_creator FOREIGN KEY (created_by_user_id) REFERENCES app_users(user_id)
    ON UPDATE CASCADE ON DELETE SET NULL,
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
  user_id BIGINT NULL,
  device_id INT NULL,
  device_name VARCHAR(100) NULL,
  schedule_id BIGINT NULL,
  occurred_at DATETIME(6) NOT NULL,
  outcome ENUM('SUCCESS','FAILED','MISSED') NOT NULL,
  details VARCHAR(1000) NOT NULL,
  CONSTRAINT fk_activity_user FOREIGN KEY (user_id) REFERENCES app_users(user_id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_activity_device FOREIGN KEY (device_id) REFERENCES devices(device_id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_activity_schedule FOREIGN KEY (schedule_id) REFERENCES device_schedules(schedule_id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  INDEX idx_activity_time (occurred_at, activity_id),
  INDEX idx_activity_type_time (event_type, occurred_at),
  INDEX idx_activity_device_time (device_id, occurred_at)
);

CREATE TABLE IF NOT EXISTS sensor_readings (
  reading_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id INT NOT NULL,
  sensor_type ENUM('TEMPERATURE','HUMIDITY','MOTION') NOT NULL,
  reading_value DECIMAL(8,2) NOT NULL,
  simulated BOOLEAN NOT NULL DEFAULT FALSE,
  recorded_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_sensor_reading_room FOREIGN KEY (room_id) REFERENCES rooms(room_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  INDEX idx_sensor_latest (room_id,sensor_type,reading_id),
  INDEX idx_sensor_recorded (recorded_at)
);

CREATE TABLE IF NOT EXISTS sensor_alert_settings (
  sensor_type ENUM('TEMPERATURE','HUMIDITY') PRIMARY KEY,
  threshold_value DECIMAL(8,2) NOT NULL,
  CONSTRAINT chk_sensor_alert_temperature CHECK (
    sensor_type <> 'TEMPERATURE' OR threshold_value BETWEEN -20 AND 60
  ),
  CONSTRAINT chk_sensor_alert_humidity CHECK (
    sensor_type <> 'HUMIDITY' OR threshold_value BETWEEN 0 AND 100
  )
);

INSERT IGNORE INTO sensor_alert_settings(sensor_type,threshold_value) VALUES
  ('TEMPERATURE',30.00),('HUMIDITY',70.00);

CREATE TABLE IF NOT EXISTS sensor_alerts (
  alert_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id INT NOT NULL,
  sensor_type ENUM('TEMPERATURE','HUMIDITY') NOT NULL,
  reading_id BIGINT NULL,
  reading_value DECIMAL(8,2) NOT NULL,
  threshold_value DECIMAL(8,2) NOT NULL,
  simulated BOOLEAN NOT NULL DEFAULT FALSE,
  triggered_at DATETIME(6) NOT NULL,
  resolved_at DATETIME(6) NULL,
  CONSTRAINT fk_sensor_alert_room FOREIGN KEY (room_id) REFERENCES rooms(room_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_sensor_alert_reading FOREIGN KEY (reading_id) REFERENCES sensor_readings(reading_id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  INDEX idx_sensor_alert_room_time (room_id,triggered_at,alert_id),
  INDEX idx_sensor_alert_active (resolved_at,triggered_at)
);

CREATE TABLE IF NOT EXISTS sensor_alert_state (
  room_id INT NOT NULL,
  sensor_type ENUM('TEMPERATURE','HUMIDITY') NOT NULL,
  active_alert_id BIGINT NULL,
  PRIMARY KEY (room_id,sensor_type),
  CONSTRAINT fk_sensor_alert_state_room FOREIGN KEY (room_id) REFERENCES rooms(room_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_sensor_alert_state_alert FOREIGN KEY (active_alert_id) REFERENCES sensor_alerts(alert_id)
    ON UPDATE CASCADE ON DELETE SET NULL
);


CREATE TABLE IF NOT EXISTS automation_rules (
  rule_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_name VARCHAR(100) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  condition_type ENUM('TEMPERATURE_ABOVE','HUMIDITY_ABOVE','MOTION_DETECTED','SCHEDULED_TIME') NOT NULL,
  threshold_value DECIMAL(8,2) NULL,
  sensor_room_id INT NULL,
  target_device_id INT NOT NULL,
  action ENUM('ON','OFF') NOT NULL,
  scheduled_time TIME NULL,
  timezone_id VARCHAR(64) NOT NULL,
  cooldown_seconds INT NOT NULL DEFAULT 300,
  last_condition_met BOOLEAN NOT NULL DEFAULT FALSE,
  last_triggered_at DATETIME(6) NULL,
  last_scheduled_date DATE NULL,
  created_by_user_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_automation_sensor_room FOREIGN KEY (sensor_room_id) REFERENCES rooms(room_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_automation_target_device FOREIGN KEY (target_device_id) REFERENCES devices(device_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_automation_creator FOREIGN KEY (created_by_user_id) REFERENCES app_users(user_id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT chk_automation_cooldown CHECK (cooldown_seconds BETWEEN 0 AND 86400),
  CONSTRAINT chk_automation_rule_shape CHECK (
    (condition_type='TEMPERATURE_ABOVE' AND threshold_value BETWEEN -20 AND 60
      AND sensor_room_id IS NOT NULL AND scheduled_time IS NULL)
    OR (condition_type='HUMIDITY_ABOVE' AND threshold_value BETWEEN 0 AND 100
      AND sensor_room_id IS NOT NULL AND scheduled_time IS NULL)
    OR (condition_type='MOTION_DETECTED' AND threshold_value IS NULL
      AND sensor_room_id IS NOT NULL AND scheduled_time IS NULL AND action='ON')
    OR (condition_type='SCHEDULED_TIME' AND threshold_value IS NULL
      AND sensor_room_id IS NULL AND scheduled_time IS NOT NULL)
  ),
  INDEX idx_automation_enabled (enabled,rule_id),
  INDEX idx_automation_target (target_device_id)
);

CREATE TABLE IF NOT EXISTS automation_rule_evaluations (
  evaluation_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_id BIGINT NULL,
  rule_name VARCHAR(100) NOT NULL,
  condition_met BOOLEAN NOT NULL,
  action_executed BOOLEAN NOT NULL,
  outcome ENUM('SUCCESS','SUPPRESSED','FAILED') NOT NULL,
  evaluated_at DATETIME(6) NOT NULL,
  details VARCHAR(1000) NOT NULL,
  CONSTRAINT fk_automation_evaluation_rule FOREIGN KEY (rule_id) REFERENCES automation_rules(rule_id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  INDEX idx_automation_evaluation_time (evaluated_at,evaluation_id),
  INDEX idx_automation_evaluation_rule_time (rule_id,evaluated_at)
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