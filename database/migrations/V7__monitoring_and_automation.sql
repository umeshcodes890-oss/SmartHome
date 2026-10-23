-- Apply after V6__revoke_sessions_on_password_change.sql.
CREATE DATABASE IF NOT EXISTS smart_home_db;
USE smart_home_db;

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


CREATE TABLE IF NOT EXISTS automation_rules (
  rule_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_name VARCHAR(100) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,

  condition_type ENUM(
    'TEMPERATURE_ABOVE',
    'HUMIDITY_ABOVE',
    'MOTION_DETECTED',
    'SCHEDULED_TIME'
  ) NOT NULL,

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
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),

  CONSTRAINT fk_automation_sensor_room
    FOREIGN KEY (sensor_room_id)
    REFERENCES rooms(room_id)
    ON UPDATE CASCADE ON DELETE CASCADE,

  CONSTRAINT fk_automation_target_device
    FOREIGN KEY (target_device_id)
    REFERENCES devices(device_id)
    ON UPDATE CASCADE ON DELETE CASCADE,

  CONSTRAINT fk_automation_creator
    FOREIGN KEY (created_by_user_id)
    REFERENCES app_users(user_id)
    ON UPDATE CASCADE ON DELETE SET NULL,

  CONSTRAINT chk_automation_cooldown
    CHECK (cooldown_seconds BETWEEN 0 AND 86400),

  INDEX idx_automation_enabled (enabled, rule_id),
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

  CONSTRAINT fk_automation_evaluation_rule
    FOREIGN KEY (rule_id)
    REFERENCES automation_rules(rule_id)
    ON UPDATE CASCADE ON DELETE SET NULL,

  INDEX idx_automation_evaluation_time
    (evaluated_at, evaluation_id),

  INDEX idx_automation_evaluation_rule_time
    (rule_id, evaluated_at)
);
