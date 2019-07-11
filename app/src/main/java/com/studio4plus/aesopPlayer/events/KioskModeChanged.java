package com.studio4plus.aesopPlayer.events;

import com.studio4plus.aesopPlayer.GlobalSettings;

public class KioskModeChanged {

    public final GlobalSettings.SettingsKioskMode type;
    public final boolean isEnabled;

    public KioskModeChanged(GlobalSettings.SettingsKioskMode type, boolean isEnabled) {
        this.type = type;
        this.isEnabled = isEnabled;
    }
}
