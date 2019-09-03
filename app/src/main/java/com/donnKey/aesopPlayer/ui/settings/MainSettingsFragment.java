package com.donnKey.aesopPlayer.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.crashlytics.android.Crashlytics;
import com.donnKey.aesopPlayer.BuildConfig;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.KioskModeSwitcher;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.model.AudioBookManager;

import java.util.Objects;

import javax.inject.Inject;

public class MainSettingsFragment extends BaseSettingsFragment {

    private static final String KEY_FAQ = "faq_preference";
    private static final String KEY_VERSION = "version_preference";

    private static final String FAQ_URL = "https://donnkey.github.io/aesopPlayer/faq.html";

    @Inject public AudioBookManager audioBookManager;
    @Inject public GlobalSettings globalSettings;
    @Inject public KioskModeSwitcher kioskModeSwitcher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AesopPlayerApplication.getComponent(Objects.requireNonNull(getActivity())).inject(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        setupFaq();
        setupVersionSummary();
    }

    @Override
    public void onStart() {
        super.onStart();

        SharedPreferences sharedPreferences = getSharedPreferences();
        updateKioskModeSummary();
        updateScreenOrientationSummary(sharedPreferences);
        updateSnoozeDelaySummary(sharedPreferences);
        updateBlinkRateSummary(sharedPreferences);
        updateSettingsInterlockSummary(sharedPreferences);
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

    @SuppressWarnings("SameReturnValue")
    private void setupVersionSummary() {
        Preference preference = findPreference(KEY_VERSION);
        preference.setSummary(BuildConfig.VERSION_NAME);
        preference.setOnPreferenceClickListener(preference1 -> {
            if (BuildConfig.DEBUG) {
                Crashlytics.getInstance().crash();
            }
            return true;
        });
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

        int summaryStringId = kioskModeSwitcher.getKioskModeSummary();
        kioskModeScreen.setSummary(summaryStringId);
    }

    @SuppressWarnings("SameReturnValue")
    private void setupFaq() {
        Preference preference = findPreference(KEY_FAQ);
        preference.setSummary(getString(R.string.pref_help_faq_summary, FAQ_URL));
        preference.setOnPreferenceClickListener(preference1 -> {
            openUrl(FAQ_URL);
            return true;
        });
    }
}
