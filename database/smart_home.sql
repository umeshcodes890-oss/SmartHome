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