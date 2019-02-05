package com.studio4plus.homerplayer.ui.settings;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import android.widget.Toast;

import com.studio4plus.homerplayer.BuildConfig;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.HomerPlayerApplication;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.model.AudioBookManager;

import java.util.Objects;

import javax.inject.Inject;

public class MainSettingsFragment extends BaseSettingsFragment {

    private static final String KEY_RESET_ALL_BOOK_PROGRESS = "reset_all_book_progress_preference";
    private static final String KEY_FAQ = "faq_preference";
    private static final String KEY_VERSION = "version_preference";
    private static final String KEY_QUICK_EXIT = "quick_exit_preference";

    private static final String FAQ_URL = "https://goo.gl/1RVxFW";

    @Inject public AudioBookManager audioBookManager;
    @Inject public GlobalSettings globalSettings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        HomerPlayerApplication.getComponent(Objects.requireNonNull(getActivity())).inject(this);
        super.onCreate(savedInstanceState);
    }

    private static boolean enteringSettings;

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);

        ConfirmDialogPreference preferenceResetProgress =
                (ConfirmDialogPreference) findPreference(KEY_RESET_ALL_BOOK_PROGRESS);
        preferenceResetProgress.setOnConfirmListener(new ConfirmDialogPreference.OnConfirmListener() {
            @Override
            public void onConfirmed() {
                audioBookManager.resetAllBookProgress();
                Toast.makeText(
                        getActivity(),
                        R.string.pref_reset_all_book_progress_done,
                        Toast.LENGTH_SHORT).show();
            }
        });
        setupFaq();
        setupQuickExit();
        updateVersionSummary();
    }

    @Override
    public void onStart() {
        super.onStart();
        enteringSettings = true;

        SharedPreferences sharedPreferences = getSharedPreferences();
        updateKioskModeSummary();
        updateScreenOrientationSummary(sharedPreferences);
        updateSnoozeDelaySummary(sharedPreferences);
        updateBlinkRateSummary(sharedPreferences);
        updateSettingsInterlockSummary(sharedPreferences);
    }

    @Override
    public void onStop() {
        super.onStop();
        enteringSettings = false;
    }

    static public boolean getInSettings() {
        return enteringSettings;
    }

    @Override
    protected int getTitle() {
        return R.string.settings_title;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch(key) {
            case GlobalSettings.KEY_SCREEN_ORIENTATION:
                updateScreenOrientationSummary(sharedPreferences);
                break;
            case GlobalSettings.KEY_SIMPLE_KIOSK_MODE:
            case GlobalSettings.KEY_KIOSK_MODE:
                updateKioskModeSummary();
                break;
            case GlobalSettings.KEY_SNOOZE_DELAY:
                updateSnoozeDelaySummary(sharedPreferences);
                break;
            case GlobalSettings.KEY_BLINK_RATE:
                updateBlinkRateSummary(sharedPreferences);
                break;
            case GlobalSettings.KEY_SETTINGS_INTERLOCK:
                updateSettingsInterlockSummary(sharedPreferences);
                break;
        }
    }

    private void updateVersionSummary() {
        Preference preference = findPreference(KEY_VERSION);
        preference.setSummary(BuildConfig.VERSION_NAME);
    }

    private void updateScreenOrientationSummary(@NonNull SharedPreferences sharedPreferences) {
        updateListPreferenceSummary(
                sharedPreferences,
                GlobalSettings.KEY_SCREEN_ORIENTATION,
                R.string.pref_screen_orientation_default_value);
    }

    private void updateSnoozeDelaySummary(SharedPreferences sharedPreferences) {
        updateListPreferenceSummary(
                sharedPreferences,
                GlobalSettings.KEY_SNOOZE_DELAY,
                R.string.pref_snooze_time_default_value);
    }

    private void updateBlinkRateSummary(SharedPreferences sharedPreferences) {
        updateListPreferenceSummary(
                sharedPreferences,
                GlobalSettings.KEY_BLINK_RATE,
                R.string.pref_blink_rate_default_value);
    }

    private void updateSettingsInterlockSummary(SharedPreferences sharedPreferences) {
        updateListPreferenceSummary(
                sharedPreferences,
                GlobalSettings.KEY_SETTINGS_INTERLOCK,
                R.string.pref_settings_interlock_default_value);
    }

    private void updateKioskModeSummary() {
        Preference kioskModeScreen =
                findPreference(GlobalSettings.KEY_KIOSK_MODE_SCREEN);

        int summaryStringId = R.string.pref_kiosk_mode_screen_summary_disabled;
        if (globalSettings.isFullKioskModeEnabled())
            summaryStringId = R.string.pref_kiosk_mode_screen_summary_full;
        else if (globalSettings.isSimpleKioskModeEnabled())
            summaryStringId = R.string.pref_kiosk_mode_screen_summary_simple;
        kioskModeScreen.setSummary(summaryStringId);
    }

    private void setupFaq() {
        Preference preference = findPreference(KEY_FAQ);
        preference.setSummary(getString(R.string.pref_help_faq_summary, FAQ_URL));
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                openUrl(FAQ_URL);
                return true;
            }
        });
    }

    private void setupQuickExit() {
        Preference preference = findPreference(KEY_QUICK_EXIT);
        preference.setOnPreferenceClickListener( (pref) -> {
            if (Build.VERSION.SDK_INT >= 21) {
                // If it's already pinned, un-pin it.
                Objects.requireNonNull(getActivity()).stopLockTask();
            }
            // First bring up "the usual android" screen. Then exit so we start a new process next
            // time, rather than resuming.  (The recents window may well show the settings
            // screen we just left, but it's just a snapshot.)
            Objects.requireNonNull(getActivity()).moveTaskToBack(true);
            System.exit(0);
            return true;
        });
    }
}
