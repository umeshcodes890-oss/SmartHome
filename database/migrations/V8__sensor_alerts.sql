-- Apply after V7__monitoring_and_automation.sql.
USE smart_home_db;

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
