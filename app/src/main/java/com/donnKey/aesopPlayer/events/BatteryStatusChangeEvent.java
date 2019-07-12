package com.donnKey.aesopPlayer.events;

import com.donnKey.aesopPlayer.battery.BatteryStatus;

public class BatteryStatusChangeEvent {
    public final BatteryStatus batteryStatus;


    public BatteryStatusChangeEvent(BatteryStatus batteryStatus) {
        this.batteryStatus = batteryStatus;
    }
}
