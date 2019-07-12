package com.donnKey.aesopPlayer.battery;

public class BatteryStatus {
    public final ChargeLevel chargeLevel;
    public final boolean isCharging;

    public BatteryStatus(ChargeLevel chargeLevel, boolean isCharging) {
        this.chargeLevel = chargeLevel;
        this.isCharging = isCharging;
    }
}
