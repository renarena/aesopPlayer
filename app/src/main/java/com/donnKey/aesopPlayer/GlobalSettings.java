/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2020 Donn S. Terry
 * Copyright (c) 2015-2017 Marcin Simonides
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.donnKey.aesopPlayer;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.os.Environment;

import androidx.annotation.NonNull;

import com.donnKey.aesopPlayer.model.LibraryContentType;
import com.donnKey.aesopPlayer.ui.ColorTheme;
import com.donnKey.aesopPlayer.ui.UiControllerBookList;
import com.donnKey.aesopPlayer.ui.settings.VersionName;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Calendar;
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

    public enum SettingsKioskMode {
        NONE,
        SIMPLE,
        PINNING,
        FULL
    }

    @SuppressWarnings("unused") // Used in xml, but use not detected by analyzer
    public enum NewVersionAction {
        NONE(0),
        SETTINGS(1),
        ALL(2);

        public final int value;

        NewVersionAction(int value) {
            this.value = value;
        }
    }

    // TODO: figure out if these constants can somehow be shared with the keys in xml files.
    public static final String KEY_COLOR_THEME = "color_theme";
    public static final String KEY_KIOSK_MODE_SCREEN = "kiosk_mode_screen";
    private static final String KEY_KIOSK_MODE_SELECTION = "kiosk_mode_selection_preference";
    public static final String KEY_JUMP_BACK = "jump_back_preference";
    public static final String KEY_SLEEP_TIMER = "sleep_timer_preference";
    private static final String KEY_STOP_POINTS_ACCESS = "stop_points_access_preference";
    public static final String KEY_SCREEN_ORIENTATION = "screen_orientation_preference";
    private static final String KEY_FF_REWIND_SOUND = "ff_rewind_sound_preference";
    private static final String KEY_SCREEN_VOLUME_SPEED = "screen_volume_speed_preference";
    private static final String KEY_TILT_VOLUME_SPEED = "tilt_volume_speed_preference";
    private static final String KEY_ARCHIVE_BOOKS = "archive_books_preference";
    private static final String KEY_RETAIN_BOOKS = "retain_books_preference";
    private static final String KEY_RENAME_FILES = "rename_files_preference";
    public static final String KEY_PLAYBACK_SPEED = "playback_speed_preference";
    public static final String KEY_SNOOZE_DELAY = "snooze_delay_preference";
    public static final String KEY_BLINK_RATE = "blink_rate_preference";
    public static final String KEY_STOP_ON_FACE_DOWN = "stop_on_face_down_preference";
    private static final String KEY_PROXIMITY_AWAKEN = "awaken_on_proximity_preference";
    public static final String KEY_SETTINGS_INTERLOCK = "settings_interlock_preference";
    private static final String KEY_MAINTENANCE_MODE = "settings_maintenance_mode";
    public static final String KEY_ANALYTICS = "settings_analytics_preference";
    private static final String KEY_ANALYTICS_QUERIED = "analytics_queried_once";
    public static final String KEY_NEW_VERSION_ACTION = "new_version_action_preference";
    public static final String KEY_NEW_VERSION_SCREEN1 = "new_version_options_screen";
    public static final String KEY_NEW_VERSION_SCREEN2 = "new_version_options_screen2";
    public static final String KEY_NEW_VERSION_WEB_PAGE = "new_version_web_page";
    public static final String KEY_NEW_VERSION_POLICY = "new_version_policy";
    public static final String KEY_NEW_VERSION_VERSION = "new_version_version";
    private static final String KEY_DIRS_LIST = "dirs_list";

    private static final String KEY_BROWSING_HINT_SHOWN = "hints.browsing_hint_shown";
    // --Commented out by Inspection (2/25/2019 2:47 PM):private static final String KEY_SETTINGS_HINT_SHOWN = "hints.settings.hint_shown";
    private static final String KEY_FLIPTOSTOP_HINT_SHOWN = "hints.fliptostop.hint_shown";
    private static final String KEY_VOLUMEDRAG_HINT_SHOWN = "hints.volumedrag.hint_shown";

    private static final String KEY_BOOKS_EVER_INSTALLED = "action_history.books_ever_installed";
    private static final String KEY_SETTINGS_EVER_ENTERED = "action_history.settings_ever_entered";
    private static final String KEY_STORED_VERSION = "stored_version";

    public static final String KEY_REMOTE_PASSWORD = "remote_password";
    public static final String KEY_REMOTE_HOST = "remote_hostname";
    public static final String KEY_REMOTE_LOGIN = "remote_login";
    public static final String KEY_REMOTE_DEVICE_NAME = "remote_device_name";
    public static final String KEY_REMOTE_CONTROL_DIR = "remote_control_dir";
    public static final String KEY_REMOTE_MAIL_POLL = "remote_mail_poll";
    public static final String KEY_REMOTE_FILE_POLL = "remote_file_poll";
    public static final String KEY_REMOTE_OPTIONS_SCREEN = "remote_options_screen";
    public static final String KEY_REMOTE_FILE_TIMESTAMP = "remote_file_timestamp";
    public static final String KEY_REMOTE_AT_TIME = "remote_at_time";

    public static final String TAG_KIOSK_DIALOG = "tag_kiosk_dialog";

    private static final String DEFAULT_VERSION = "v0.0.0";

    private final Resources resources;
    private final SharedPreferences sharedPreferences;

    public boolean versionIsCurrent;
    public boolean versionUpdated = false;

    // We changed analytics: must restart completely
    public boolean forceAppRestart = false;

    @Inject
    public GlobalSettings(Resources resources, SharedPreferences sharedPreferences) {
        this.resources = resources;
        this.sharedPreferences = sharedPreferences;

        if (!browsingHintShown()) {
            // browsingHintShown is a proxy for "did we ever get here on this device"
            // since the browsing hint MUST be the first click.
            setStoredVersion(BuildConfig.VERSION_NAME);
            this.versionIsCurrent = true;
        }
        else {
            VersionName currentVersion = new VersionName(BuildConfig.VERSION_NAME);
            final String storedVersion = sharedPreferences.getString(KEY_STORED_VERSION, DEFAULT_VERSION);
            VersionName storedVersion1 = new VersionName(storedVersion);
            this.versionIsCurrent = storedVersion1.compareTo(currentVersion) >= 0;
        }
    }

    public int getJumpBackPreferenceMs() {
        String valueString = sharedPreferences.getString(
                KEY_JUMP_BACK, resources.getString(R.string.pref_jump_back_default_value));
        return (int) TimeUnit.SECONDS.toMillis(Integer.parseInt(valueString));
    }

    public long getSleepTimerMs() {
        String valueString = sharedPreferences.getString(
                KEY_SLEEP_TIMER, resources.getString(R.string.pref_sleep_timer_default_value));
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
        return Float.parseFloat(valueString);
    }

    public void setPlaybackSpeed(float speed) {
        sharedPreferences.edit().putString(KEY_PLAYBACK_SPEED, String.format(Locale.US,"%1.1f",speed)).apply();
    }

    public int getSnoozeDelay() {
        if (isMaintenanceMode()) {
            return 0;
        }
        final String valueString = sharedPreferences.getString(
                KEY_SNOOZE_DELAY, resources.getString(R.string.pref_snooze_time_default_value));
        return Integer.parseInt(valueString);
    }

    public int getBlinkRate() {
        final String valueString = sharedPreferences.getString(
                KEY_BLINK_RATE, resources.getString(R.string.pref_blink_rate_default_value));
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
        if (isMaintenanceMode()) {
            return SettingsInterlockMode.NONE;
        }
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

    public void setBooksEverInstalled(@NonNull LibraryContentType contentType) {
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
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

    public boolean volumeDragHintShown() {
        return sharedPreferences.getBoolean(KEY_VOLUMEDRAG_HINT_SHOWN, false);
    }

    public void setVolumeDragHintShown() {
        sharedPreferences.edit().putBoolean(KEY_VOLUMEDRAG_HINT_SHOWN, true).apply();
    }

    @SuppressLint("ApplySharedPref")
    public void setMaintenanceMode(boolean mode) {
        sharedPreferences.edit().putBoolean(KEY_MAINTENANCE_MODE, mode).commit();
        UiControllerBookList.suppressAnnounce();
    }

    // True->certain features temporarily disabled (actual settings NOT changed).
    public boolean isMaintenanceMode() {
        return sharedPreferences.getBoolean(KEY_MAINTENANCE_MODE, false);
    }

    public NewVersionAction getNewVersionAction() {
        final String stringValue = sharedPreferences.getString(
                GlobalSettings.KEY_NEW_VERSION_ACTION,
                resources.getString(R.string.pref_new_version_action_default_value));
        return NewVersionAction.valueOf(stringValue);
    }

    public void setStoredVersion(String versionName) {
        sharedPreferences.edit().putString(KEY_STORED_VERSION, versionName).apply();
    }

    public SettingsKioskMode getKioskMode() {
        final String stringValue = sharedPreferences.getString(
                GlobalSettings.KEY_KIOSK_MODE_SELECTION,
                resources.getString(R.string.pref_kiosk_mode_selection_default_value));
        return SettingsKioskMode.valueOf(stringValue);
    }

    public boolean isFullKioskModeEnabled() {
        if (isMaintenanceMode()) {
            return false;
        }
        return getKioskMode() == SettingsKioskMode.FULL;
    }

    public boolean isPinningKioskModeEnabled() {
        if (isMaintenanceMode()) {
            return false;
        }
        return getKioskMode() == SettingsKioskMode.PINNING;
    }

    @SuppressLint("ApplySharedPref")
    public void setKioskModeNow(@NonNull SettingsKioskMode kioskMode) {
        sharedPreferences.edit().putString(KEY_KIOSK_MODE_SELECTION, kioskMode.toString()).commit();
    }

    public boolean isSimpleKioskModeEnabled() {
        if (isMaintenanceMode()) {
            return false;
        }
        return getKioskMode() == SettingsKioskMode.SIMPLE;
    }

    public boolean isAnyKioskModeEnabled() {
        if (isMaintenanceMode()) {
            return false;
        }
        return getKioskMode() != SettingsKioskMode.NONE;
    }

    public boolean isFFRewindSoundEnabled() {
        return sharedPreferences.getBoolean(KEY_FF_REWIND_SOUND, true);
    }

    public boolean isSwipeStopPointsEnabled() {
        return sharedPreferences.getBoolean(KEY_STOP_POINTS_ACCESS, false);
    }

    public boolean isScreenVolumeSpeedEnabled() {
        return sharedPreferences.getBoolean(KEY_SCREEN_VOLUME_SPEED, true);
    }

    public boolean isTiltVolumeSpeedEnabled() {
        return sharedPreferences.getBoolean(KEY_TILT_VOLUME_SPEED, false);
    }

    public boolean getAnalytics() {
        return sharedPreferences.getBoolean(KEY_ANALYTICS, false);
    }

    public void setAnalytics(boolean b) {
        sharedPreferences.edit().putBoolean(KEY_ANALYTICS, b).apply();
    }

    public boolean getAnalyticsQueried() {
        return sharedPreferences.getBoolean(KEY_ANALYTICS_QUERIED, false);
    }

    public void setAnalyticsQueried(boolean b) {
        sharedPreferences.edit().putBoolean(KEY_ANALYTICS_QUERIED, b).apply();
    }

    public boolean getRetainBooks() {
        return sharedPreferences.getBoolean(KEY_RETAIN_BOOKS, false);
    }

    public void setRetainBooks(boolean b) {
        sharedPreferences.edit().putBoolean(KEY_RETAIN_BOOKS, b).apply();
    }

    public boolean getRenameFiles() {
        return sharedPreferences.getBoolean(KEY_RENAME_FILES, true);
    }

    public void setRenameFiles(boolean b) {
        sharedPreferences.edit().putBoolean(KEY_RENAME_FILES, b).apply();
    }

    public boolean getArchiveBooks() {
        return sharedPreferences.getBoolean(KEY_ARCHIVE_BOOKS, false);
    }

    public void setArchiveBooks(boolean b) {
        sharedPreferences.edit().putBoolean(KEY_ARCHIVE_BOOKS, b).apply();
    }

    public void setDownloadDirectories(ArrayList<String> dirs) {
        // Can't use get/putStringSet because it's unordered.
        if (dirs == null) {
            return;
        }
        try {
            JSONObject jsonDirList = new JSONObject();
            JSONArray jsonDirs = new JSONArray(dirs);
            jsonDirList.put(KEY_DIRS_LIST, jsonDirs);
            sharedPreferences.edit().putString(KEY_DIRS_LIST, jsonDirList.toString()).apply();
        } catch (Exception e) {
            /* don't bother */
        }
    }

    public ArrayList<String> getDownloadDirectories() {
        String jsonDirs = sharedPreferences.getString(KEY_DIRS_LIST, null);
        if (jsonDirs == null) {
            return null;
        }
        try {
            ArrayList<String> downloadDirs = null;
            JSONObject jsonObject = (JSONObject) new JSONTokener(jsonDirs).nextValue();
            JSONArray jsonDownloadDirs = jsonObject.optJSONArray(KEY_DIRS_LIST);
            if (jsonDownloadDirs != null) {
                final int count = jsonDownloadDirs.length();
                downloadDirs = new ArrayList<>(count);
                for (int i = 0; i < count; ++i) {
                    downloadDirs.add(jsonDownloadDirs.getString(i));
                }
            }
            return downloadDirs;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean getFilePollEnabled() {
        return sharedPreferences.getBoolean(GlobalSettings.KEY_REMOTE_FILE_POLL, false);
    }

    public boolean getMailPollEnabled() {
        return sharedPreferences.getBoolean(GlobalSettings.KEY_REMOTE_MAIL_POLL, false);
    }

    public String getMailHostname() {
        return sharedPreferences.getString(GlobalSettings.KEY_REMOTE_HOST, "");
    }

    public String getMailLogin() {
        return sharedPreferences.getString(GlobalSettings.KEY_REMOTE_LOGIN, "");
    }

    public String getMailPassword() {
        return sharedPreferences.getString(GlobalSettings.KEY_REMOTE_PASSWORD, "");
    }

    public String getMailDeviceName() {
        return sharedPreferences.getString(GlobalSettings.KEY_REMOTE_DEVICE_NAME, "");
    }

    public String getRemoteControlDir() {
        return sharedPreferences.getString(GlobalSettings.KEY_REMOTE_CONTROL_DIR,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath());
    }

    public long getSavedControlFileTimestamp() {
        // File timestamp (in millis)
        return sharedPreferences.getLong(GlobalSettings.KEY_REMOTE_FILE_TIMESTAMP, 0L);
    }

    public void setSavedControlFileTimestamp(long timestamp) {
        sharedPreferences.edit().putLong(KEY_REMOTE_FILE_TIMESTAMP, timestamp).apply();
    }

    public Calendar getSavedAtTime() {
        long millis =  sharedPreferences.getLong(GlobalSettings.KEY_REMOTE_AT_TIME, 0L);
        Calendar result = Calendar.getInstance();
        if (millis == 0) {
            // Default to 100 years from now (==never)
            result.add(Calendar.YEAR, 100);
        }
        else {
            result.setTimeInMillis(millis);
        }
        return result;
    }

    public void setSavedAtTime(@NonNull Calendar cal) {
        sharedPreferences.edit().putLong(KEY_REMOTE_AT_TIME, cal.getTimeInMillis()).apply();
    }

    public SharedPreferences appSharedPreferences() {
        return sharedPreferences;
    }

    @NonNull
    public ColorTheme colorTheme() {
        String colorThemeName = sharedPreferences.getString(
                KEY_COLOR_THEME, resources.getString(R.string.pref_color_theme_default_value));
        try {
            return ColorTheme.valueOf(colorThemeName);
        } catch (IllegalArgumentException illegalArgument) {
            return ColorTheme.valueOf(resources.getString(R.string.pref_color_theme_default_value));
        }
    }
}
