package com.studio4plus.homerplayer;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;

import com.studio4plus.homerplayer.model.LibraryContentType;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GlobalSettings {

    @SuppressWarnings("unused") // Used in xml, but use not detected by analyzer
    private enum Orientation {
        LANDSCAPE_AUTO(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
        LANDSCAPE_LOCKED(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
        LANDSCAPE_REVERSE_LOCKED(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);

        final int value;

        Orientation(int value) {
            this.value = value;
        }
    }

    @SuppressWarnings("unused") // Used in xml, but use not detected by analyzer
    public enum FaceDownAction {
        NONE,
        STOP_ONLY,
        STOP_RESUME
    }

    public enum SettingsInterlockMode {
        NONE,
        DOUBLE_PRESS,
        MULTI_TAP
    }

    // TODO: figure out if these constants can somehow be shared with the keys in xml files.
    public static final String KEY_KIOSK_MODE_SCREEN = "kiosk_mode_screen";
    public static final String KEY_KIOSK_MODE = "kiosk_mode_preference";
    public static final String KEY_SIMPLE_KIOSK_MODE = "simple_kiosk_mode_preference";
    public static final String KEY_JUMP_BACK = "jump_back_preference";
    public static final String KEY_SLEEP_TIMER = "sleep_timer_preference";
    public static final String KEY_SCREEN_ORIENTATION = "screen_orientation_preference";
    private static final String KEY_FF_REWIND_SOUND = "ff_rewind_sound_preference";
    private static final String KEY_SCREEN_VOLUME_SPEED = "screen_volume_speed_preference";
    public static final String KEY_PLAYBACK_SPEED = "playback_speed_preference";
    public static final String KEY_SNOOZE_DELAY = "snooze_delay_preference";
    public static final String KEY_BLINK_RATE = "blink_rate_preference";
    public static final String KEY_STOP_ON_FACE_DOWN = "stop_on_face_down_preference";
    public static final String KEY_SET_PROGRESS = "set_progress_preference";
    private static final String KEY_PROXIMITY_AWAKEN = "awaken_on_proximity_preference";
    public static final String KEY_SETTINGS_INTERLOCK = "settings_interlock_preference";

    private static final String KEY_BROWSING_HINT_SHOWN = "hints.browsing_hint_shown";
    // --Commented out by Inspection (2/25/2019 2:47 PM):private static final String KEY_SETTINGS_HINT_SHOWN = "hints.settings.hint_shown";
    private static final String KEY_FLIPTOSTOP_HINT_SHOWN = "hints.fliptostop.hint_shown";

    private static final String KEY_BOOKS_EVER_INSTALLED = "action_history.books_ever_installed";
    private static final String KEY_SETTINGS_EVER_ENTERED = "action_history.settings_ever_entered";

    private final Resources resources;
    private final SharedPreferences sharedPreferences;

    @Inject
    public GlobalSettings(Resources resources, SharedPreferences sharedPreferences) {
        this.resources = resources;
        this.sharedPreferences = sharedPreferences;
    }

    public int getJumpBackPreferenceMs() {
        String valueString = sharedPreferences.getString(
                KEY_JUMP_BACK, resources.getString(R.string.pref_jump_back_default_value));
        assert valueString != null;
        return (int) TimeUnit.SECONDS.toMillis(Integer.parseInt(valueString));
    }

    public long getSleepTimerMs() {
        String valueString = sharedPreferences.getString(
                KEY_SLEEP_TIMER, resources.getString(R.string.pref_sleep_timer_default_value));
        assert valueString != null;
        return TimeUnit.SECONDS.toMillis(Long.parseLong(valueString));
    }

    public int getScreenOrientation() {
        String stringValue = sharedPreferences.getString(
                GlobalSettings.KEY_SCREEN_ORIENTATION,
                resources.getString(R.string.pref_screen_orientation_default_value));
        return Orientation.valueOf(stringValue).value;
    }

    public float getPlaybackSpeed() {
        final String valueString = sharedPreferences.getString(
                KEY_PLAYBACK_SPEED, resources.getString(R.string.pref_playback_speed_default_value));
        assert valueString != null;
        return Float.parseFloat(valueString);
    }

    public void setPlaybackSpeed(float speed) {
        sharedPreferences.edit().putString(KEY_PLAYBACK_SPEED, String.format(Locale.US,"%1.1f",speed)).apply();
    }

    public int getSnoozeDelay() {
        final String valueString = sharedPreferences.getString(
                KEY_SNOOZE_DELAY, resources.getString(R.string.pref_snooze_time_default_value));
        assert valueString != null;
        return Integer.parseInt(valueString);
    }

    public int getBlinkRate() {
        final String valueString = sharedPreferences.getString(
                KEY_BLINK_RATE, resources.getString(R.string.pref_blink_rate_default_value));
        assert valueString != null;
        return Integer.parseInt(valueString);
    }

    public FaceDownAction getStopOnFaceDown() {
        final String stringValue = sharedPreferences.getString(
                GlobalSettings.KEY_STOP_ON_FACE_DOWN,
                resources.getString(R.string.pref_stop_on_face_down_default_value));
        return FaceDownAction.valueOf(stringValue);
    }

    public boolean isProximityEnabled() {
        return sharedPreferences.getBoolean(KEY_PROXIMITY_AWAKEN, true);
    }

    public SettingsInterlockMode getSettingsInterlock() {
        final String stringValue = sharedPreferences.getString(
                GlobalSettings.KEY_SETTINGS_INTERLOCK,
                resources.getString(R.string.pref_settings_interlock_default_value));
        return SettingsInterlockMode.valueOf(stringValue);
    }

    public LibraryContentType booksEverInstalled() {
        try {
            String value = sharedPreferences.getString(KEY_BOOKS_EVER_INSTALLED, null);
            if (value != null)
                return LibraryContentType.valueOf(value);
            else
                return LibraryContentType.EMPTY;
        } catch (ClassCastException e) {
            boolean everInstalled = sharedPreferences.getBoolean(KEY_BOOKS_EVER_INSTALLED, false);
            LibraryContentType contentType =
                    everInstalled ? LibraryContentType.USER_CONTENT : LibraryContentType.EMPTY;
            setBooksEverInstalled(contentType);
            return contentType;
        }
    }

    public void setBooksEverInstalled(LibraryContentType contentType) {
        LibraryContentType oldContentType = booksEverInstalled();
        if (contentType.supersedes(oldContentType))
            sharedPreferences.edit().putString(KEY_BOOKS_EVER_INSTALLED, contentType.name()).apply();
    }

    @SuppressWarnings("unused")
    public boolean settingsEverEntered() {
        return sharedPreferences.getBoolean(KEY_SETTINGS_EVER_ENTERED, false);
    }

    public void setSettingsEverEntered() {
        sharedPreferences.edit().putBoolean(KEY_SETTINGS_EVER_ENTERED, true).apply();
    }

    public boolean browsingHintShown() {
        return sharedPreferences.getBoolean(KEY_BROWSING_HINT_SHOWN, false);
    }

    public void setBrowsingHintShown() {
        sharedPreferences.edit().putBoolean(KEY_BROWSING_HINT_SHOWN, true).apply();
    }

// --Commented out by Inspection START (2/25/2019 12:08 PM):
//    public boolean settingsHintShown() {
//        return sharedPreferences.getBoolean(KEY_SETTINGS_HINT_SHOWN, false);
//    }
// --Commented out by Inspection STOP (2/25/2019 12:08 PM)

// --Commented out by Inspection START (2/25/2019 12:08 PM):
//    public void setSettingsHintShown() {
//        sharedPreferences.edit().putBoolean(KEY_SETTINGS_HINT_SHOWN, true).apply();
//    }
// --Commented out by Inspection STOP (2/25/2019 12:08 PM)

    public boolean flipToStopHintShown() {
        return sharedPreferences.getBoolean(KEY_FLIPTOSTOP_HINT_SHOWN, false);
    }

    public void setFlipToStopHintShown() {
        sharedPreferences.edit().putBoolean(KEY_FLIPTOSTOP_HINT_SHOWN, true).apply();
    }

    public boolean isFullKioskModeEnabled() {
        return sharedPreferences.getBoolean(KEY_KIOSK_MODE, false);
    }

    @SuppressLint("ApplySharedPref")
    public void setFullKioskModeEnabledNow(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_KIOSK_MODE, enabled).commit();
    }

    public boolean isSimpleKioskModeEnabled() {
        return sharedPreferences.getBoolean(KEY_SIMPLE_KIOSK_MODE, false);
    }

    public boolean isAnyKioskModeEnabled() {
        return isSimpleKioskModeEnabled() || sharedPreferences.getBoolean(KEY_KIOSK_MODE, false);
    }

    public boolean isFFRewindSoundEnabled() {
        return sharedPreferences.getBoolean(KEY_FF_REWIND_SOUND, true);
    }

    public boolean isScreenVolumeSpeedEnabled() {
        return sharedPreferences.getBoolean(KEY_SCREEN_VOLUME_SPEED, true);
    }

    public SharedPreferences appSharedPreferences() {
        return sharedPreferences;
    }
}
