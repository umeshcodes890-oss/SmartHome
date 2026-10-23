package com.smarthome.model;

public record Room(int id, String name, int floorNumber, int deviceCount, int activeDeviceCount) {}
