package com.studio4plus.aesopPlayer.events;

public class DeviceAdminChangeEvent {
    public final boolean isEnabled;

    public DeviceAdminChangeEvent(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }
}
