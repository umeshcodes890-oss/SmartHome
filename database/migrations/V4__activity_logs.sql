-- Apply after database/migrations/V3__device_schedules.sql.
CREATE DATABASE IF NOT EXISTS smart_home_db;
USE smart_home_db;

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