/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Donn S. Terry
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

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.BuildConfig;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.R;

import java.util.Objects;

import javax.inject.Inject;

import org.greenrobot.eventbus.EventBus;

public class NewVersionSettingsFragment extends BaseSettingsFragment {

    private static final String SUMMARY_URL = AesopPlayerApplication.WEBSITE_URL + "features.html#futures";

    @Inject
    public EventBus eventBus;
    @Inject
    public GlobalSettings globalSettings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AesopPlayerApplication.getComponent(requireActivity()).inject(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_new_version, rootKey);
        PreferenceScreen screen = getPreferenceScreen();
        Preference pref = findPreference(GlobalSettings.KEY_NEW_VERSION_POLICY);
        assert pref != null;
        if (globalSettings.versionIsCurrent) {
            screen.removePreference(pref);
        }
        pref = screen.findPreference(GlobalSettings.KEY_NEW_VERSION_VERSION);
        assert pref != null;
        pref.setTitle(getString(R.string.pref_summary_of_features_for, BuildConfig.VERSION_NAME));
    }

    @Override
    public void onStart() {
        super.onStart();
        setupSummary();
        updateNewVersionSummary(getSharedPreferences());
    }

    @Override
    public void onStop() {
        super.onStop();
        globalSettings.setStoredVersion(BuildConfig.VERSION_NAME);
        globalSettings.versionIsCurrent = true;
        globalSettings.versionUpdated = true;
    }

    @Override
    protected int getTitle() {
        return R.string.pref_new_version_actions_title;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @NonNull String key) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (key) {
            case GlobalSettings.KEY_NEW_VERSION_ACTION:
                updateNewVersionSummary(sharedPreferences);
                break;
        }
    }

    private void updateNewVersionSummary(@NonNull SharedPreferences sharedPreferences) {
        updateListPreferenceSummary(
                sharedPreferences,
                GlobalSettings.KEY_NEW_VERSION_ACTION,
                R.string.pref_new_version_action_default_value);
    }

    @SuppressWarnings("SameReturnValue")
    private void setupSummary() {
        Preference preference = findPreference(GlobalSettings.KEY_NEW_VERSION_WEB_PAGE);
        Objects.requireNonNull(preference).setSummary(getString(R.string.pref_help_faq_summary, SUMMARY_URL));
        preference.setOnPreferenceClickListener(preference1 -> {
            openUrl(requireContext(),SUMMARY_URL);
            return true;
        });
    }
}
