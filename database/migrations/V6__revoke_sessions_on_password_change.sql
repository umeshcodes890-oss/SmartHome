-- Apply after database/migrations/V5__user_authentication.sql.
CREATE DATABASE IF NOT EXISTS smart_home_db;
USE smart_home_db;

ALTER TABLE app_users
  ADD COLUMN session_version INT NOT NULL DEFAULT 0 AFTER enabled;
