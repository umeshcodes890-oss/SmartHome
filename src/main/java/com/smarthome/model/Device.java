package com.smarthome.model;

import java.math.BigDecimal;

public class Device {
    private final int id;
    private final String name;
    private final String type;
    private final String room;
    private final String status;
    private final BigDecimal powerWatts;

    public Device(int id, String name, String type, String room, String status, BigDecimal powerWatts) {
        this.id=id; this.name=name; this.type=type; this.room=room; this.status=status; this.powerWatts=powerWatts;
    }
    public int getId(){ return id; }
    public String getName(){ return name; }
    public String getType(){ return type; }
    public String getRoom(){ return room; }
    public String getStatus(){ return status; }
    public BigDecimal getPowerWatts(){ return powerWatts; }
}