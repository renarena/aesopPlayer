package com.studio4plus.aesopPlayer.events;

import com.studio4plus.aesopPlayer.battery.BatteryStatus;

public class BatteryStatusChangeEvent {
    public final BatteryStatus batteryStatus;


    public BatteryStatusChangeEvent(BatteryStatus batteryStatus) {
        this.batteryStatus = batteryStatus;
    }
}
