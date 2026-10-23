USE smart_home_db;

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
