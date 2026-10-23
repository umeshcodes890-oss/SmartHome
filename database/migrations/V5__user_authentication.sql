CREATE DATABASE IF NOT EXISTS smart_home_db;
USE smart_home_db;

CREATE TABLE IF NOT EXISTS app_users (
  user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('ADMIN','RESIDENT') NOT NULL DEFAULT 'RESIDENT',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_app_users_username UNIQUE (username)
);

ALTER TABLE device_schedules
  ADD COLUMN created_by_user_id BIGINT NULL AFTER device_id,
  ADD CONSTRAINT fk_schedule_creator FOREIGN KEY (created_by_user_id) REFERENCES app_users(user_id)
    ON UPDATE CASCADE ON DELETE SET NULL;

ALTER TABLE activity_logs
  ADD COLUMN user_id BIGINT NULL AFTER event_type,
  ADD INDEX idx_activity_user_time (user_id, occurred_at),
  ADD CONSTRAINT fk_activity_user FOREIGN KEY (user_id) REFERENCES app_users(user_id)
    ON UPDATE CASCADE ON DELETE SET NULL;
