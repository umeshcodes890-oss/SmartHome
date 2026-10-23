USE smart_home_db;

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

INSERT INTO energy_usage_sessions
  (device_id, device_name, device_type, room_id, room_name, power_watts, started_at)
SELECT d.device_id, d.device_name, d.device_type, d.room_id, r.room_name, d.power_watts, UTC_TIMESTAMP(6)
FROM devices d
JOIN rooms r ON r.room_id=d.room_id
WHERE d.status='ON'
ON DUPLICATE KEY UPDATE session_id=session_id;
