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
package com.donnKey.aesopPlayer.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.donnKey.aesopPlayer.BuildConfig;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.KioskModeSwitcher;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.model.AudioBookManager;

import java.util.Objects;

import javax.inject.Inject;

public class MainSettingsFragment extends BaseSettingsFragment {

    private static final String TAG="Settings Main";

    private static final String KEY_FAQ = "faq_preference";
    private static final String KEY_POLICY = "data_usage_preference";
    private static final String KEY_VERSION = "version_preference";

    private static final String FAQ_URL = AesopPlayerApplication.WEBSITE_URL + "faq.html";

    @Inject public AudioBookManager audioBookManager;
    @Inject public GlobalSettings globalSettings;
    @Inject public KioskModeSwitcher kioskModeSwitcher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AesopPlayerApplication.getComponent(requireActivity()).inject(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);

        PreferenceScreen screen = getPreferenceScreen();
        Preference pref;
        // Only show one variant of the new version screen
        if (globalSettings.versionIsCurrent || globalSettings.getNewVersionAction() == GlobalSettings.NewVersionAction.NONE) {
            pref = getPreferenceManager().findPreference(GlobalSettings.KEY_NEW_VERSION_SCREEN1);
        }
        else {
            pref = getPreferenceManager().findPreference(GlobalSettings.KEY_NEW_VERSION_SCREEN2);
        }
        assert pref != null;
        screen.removePreference(pref);

        setupFaq();
        setupPolicy();
        setupVersionSummary();
    }

    private void updateNewVersionSummary() {
        Preference pref;
        if (globalSettings.versionIsCurrent || globalSettings.getNewVersionAction() == GlobalSettings.NewVersionAction.NONE) {
            pref = findPreference(GlobalSettings.KEY_NEW_VERSION_SCREEN2);
        }
        else {
            pref = findPreference(GlobalSettings.KEY_NEW_VERSION_SCREEN1);
        }
        GlobalSettings.NewVersionAction action = globalSettings.getNewVersionAction();
        String[] actions = getResources().getStringArray(R.array.new_version_action_entries);
        if (pref != null) {
            pref.setSummary(actions[action.value]);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        SharedPreferences sharedPreferences = getSharedPreferences();
        updateColorThemeSummary(sharedPreferences);
        updateKioskModeSummary();
        updateScreenOrientationSummary(sharedPreferences);
        updateSnoozeDelaySummary(sharedPreferences);
        updateBlinkRateSummary(sharedPreferences);
        updateSettingsInterlockSummary(sharedPreferences);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (globalSettings.versionUpdated) {
            // If we changed the visibility of the NewVersionSettings stuff, refresh
            globalSettings.versionUpdated = false;
            requireActivity().recreate();
        }
        updateNewVersionSummary();
    }

    @Override
    protected int getTitle() {
        return R.string.settings_title;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch(key) {
            case GlobalSettings.KEY_COLOR_THEME:
                updateColorThemeSummary(sharedPreferences);
                // The recreate will end up calling onCreate to change the the actual theme
                requireActivity().recreate();
                break;
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
            case GlobalSettings.KEY_ANALYTICS:
                respondToAnalyticsChange();
                break;
        }
    }

    @SuppressWarnings("SameReturnValue")
    private void setupVersionSummary() {
        Preference preference = findPreference(KEY_VERSION);
        Objects.requireNonNull(preference).setSummary(BuildConfig.VERSION_NAME);
        preference.setOnPreferenceClickListener(preference1 -> {
            if (BuildConfig.DEBUG) {
                CrashWrapper.log(Log.DEBUG, TAG, "Forced Crash");
                CrashWrapper.crash();
            }
            return true;
        });
    }

    private void updateColorThemeSummary(@NonNull SharedPreferences sharedPreferences) {
        updateListPreferenceSummary(
                sharedPreferences,
                GlobalSettings.KEY_COLOR_THEME,
                R.string.pref_color_theme_default_value);
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
        Objects.requireNonNull(kioskModeScreen).setSummary(summaryStringId);
    }

    private void respondToAnalyticsChange()
    {
        if (globalSettings.getAnalytics()) {
            CrashWrapper.start(getContext(), true);
        }
        else {
            globalSettings.forceAppRestart = true;

            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireContext())
                    .setMessage(R.string.info_will_restart)
                    .setTitle(R.string.info_info)
                    .setIcon(R.drawable.ic_launcher);
            dialogBuilder.setPositiveButton(android.R.string.ok,
                    (dialogInterface, i) -> {});
            dialogBuilder.create().show();
        }

    }

    @SuppressWarnings("SameReturnValue")
    private void setupFaq() {
        Preference preference = findPreference(KEY_FAQ);
        Objects.requireNonNull(preference).setSummary(getString(R.string.pref_help_faq_summary, FAQ_URL));
        preference.setOnPreferenceClickListener(preference1 -> {
            openUrl(requireContext(),FAQ_URL);
            return true;
        });
    }

    @SuppressWarnings("SameReturnValue")
    private void setupPolicy() {
        Preference preference = findPreference(KEY_POLICY);
        Objects.requireNonNull(preference).setOnPreferenceClickListener(preference1 -> {
            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new PolicyFragment())
                    .addToBackStack(null)
                    .commit();
            return true;
        });
    }
}
