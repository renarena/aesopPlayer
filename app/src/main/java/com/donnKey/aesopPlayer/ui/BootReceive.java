package com.donnKey.aesopPlayer.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;

import javax.inject.Inject;

// A boot event receiver so we can start up and lock the device if we're running
// in a Kiosk mode, thus making it fairly likely that the user will be able to use
// the player after an unintended reboot (or dead battery).
public class BootReceive extends BroadcastReceiver {
    private static final String TAG = "LockReceive";

    @Inject
    public GlobalSettings globalSettings;

    public BootReceive () {
        AesopPlayerApplication.getComponent().inject(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        assert intent.getAction() != null;
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            if (globalSettings.isAnyKioskModeEnabled()) {
                // We just booted. Start the main line if we're locked on screen.
                Intent mIntent = new Intent(context, MainActivity.class);
                mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(mIntent);
            }
        }
    }

    /* Debugging tools:
    public static final String KEY_LOGGER = "logger";
        This will log info into sharedPreferences, and works during boot startup before debugging can
        be enabled. Print the result from getStartupLog() during main startup. Call logClear() from
        startSettings() or similar so it's retained until acknowledged

    static public void logStartup(Context context, String content) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences!= null) {
            sharedPreferences.edit().putString(KEY_LOGGER, getStartupLog(context) + "\n" + content).apply();
        }
    }

    static public void logClear(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences!= null) {
            sharedPreferences.edit().putString(KEY_LOGGER, "").apply();
        }
    }

    static public String getStartupLog(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences!= null) {
            return sharedPreferences.getString(KEY_LOGGER, "");
        }
        return "";
    }
     */
}
