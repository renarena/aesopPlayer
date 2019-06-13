package com.studio4plus.homerplayer.events;

import com.studio4plus.homerplayer.GlobalSettings;

public class KioskModeChanged {

    public final GlobalSettings.SettingsKioskMode type;
    public final boolean isEnabled;

    public KioskModeChanged(GlobalSettings.SettingsKioskMode type, boolean isEnabled) {
        this.type = type;
        this.isEnabled = isEnabled;
    }
}
