/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2019 Donn S. Terry
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
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.AesopPlayerDeviceAdmin;
import com.donnKey.aesopPlayer.KioskModeSwitcher;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.events.DeviceAdminChangeEvent;

import java.util.Objects;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

import static com.donnKey.aesopPlayer.GlobalSettings.TAG_KIOSK_DIALOG;

public class KioskSettingsFragment extends BaseSettingsFragment {

    private static final String KEY_UNREGISTER_DEVICE_OWNER = "unregister_device_owner_preference";
    private static final String KEY_KIOSK_SELECTION = "kiosk_choice_preference";

    @Inject
    public KioskModeSwitcher kioskModeSwitcher;
    @Inject
    public EventBus eventBus;
    @Inject
    public GlobalSettings globalSettings;

    // These have to match the row numbers in the RadioButton list
    private static final int NONE_ = 0;
    private static final int SIMPLE_ = 1;
    private static final int PINNING_ = 2;
    private static final int FULL_ = 3;

    // This really should be an array with enum subscripts that we can guarantee the ordinal
    // of, but Java just won't do that. (Radio buttons are array-based.)
    class KioskPolicy {
        boolean available;
        boolean possible;
        int subTitle;
        final GlobalSettings.SettingsKioskMode kioskMode;
        final int slot;

        KioskPolicy(GlobalSettings.SettingsKioskMode kioskMode, int slot) {
            this.kioskMode = kioskMode;
            this.slot = slot;
            this.available = false;
            this.possible = false;
        }
    }

    private final KioskPolicy[] kioskPolicies = new KioskPolicy[]{
            new KioskPolicy(GlobalSettings.SettingsKioskMode.NONE, NONE_),
            new KioskPolicy(GlobalSettings.SettingsKioskMode.SIMPLE, SIMPLE_),
            new KioskPolicy(GlobalSettings.SettingsKioskMode.PINNING, PINNING_),
            new KioskPolicy(GlobalSettings.SettingsKioskMode.FULL, FULL_)};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AesopPlayerApplication.getComponent(Objects.requireNonNull(getActivity())).inject(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_kiosk, rootKey);
        updateKioskModeSummary();
    }

    private void updateKioskModeSummary() {
        kioskPolicies[NONE_].possible = true;
        kioskPolicies[NONE_].available = true;
        kioskPolicies[NONE_].subTitle = R.string.pref_kiosk_mode_screen_summary_2_disables;

        // Simple Mode
        if (Build.VERSION.SDK_INT < 19) { //K
            kioskPolicies[SIMPLE_].possible = false;
            kioskPolicies[SIMPLE_].available = false;
            kioskPolicies[SIMPLE_].subTitle = R.string.pref_kiosk_mode_simple_summary_old_version;
        }
        else {
            kioskPolicies[SIMPLE_].possible = true;
            kioskPolicies[SIMPLE_].available = true;
            kioskPolicies[SIMPLE_].subTitle = R.string.pref_kiosk_mode_simple_title;
        }

        // App Pinning and Full.
        ConfirmDialogPreference preferenceUnregisterDeviceOwner =
                (ConfirmDialogPreference) findPreference(KEY_UNREGISTER_DEVICE_OWNER);

        if (Build.VERSION.SDK_INT < 21) { // L
            if (preferenceUnregisterDeviceOwner != null) {
                // Already did it
                getPreferenceScreen().removePreference(preferenceUnregisterDeviceOwner);
            }

            kioskPolicies[PINNING_].possible = false;
            kioskPolicies[PINNING_].available = false;
            kioskPolicies[PINNING_].subTitle = R.string.pref_kiosk_mode_full_summary_old_version;

            kioskPolicies[FULL_].possible = false;
            kioskPolicies[FULL_].available = false;
            kioskPolicies[FULL_].subTitle = R.string.pref_kiosk_mode_full_summary_old_version;
        }
        else {
            preferenceUnregisterDeviceOwner.setOnConfirmListener(this::disableDeviceOwner);
            updateUnregisterDeviceOwner(AesopPlayerDeviceAdmin.isDeviceOwner(getActivity()));

            kioskPolicies[PINNING_].possible = true;

            if (AesopPlayerDeviceAdmin.isDeviceOwner(getActivity())) {
                kioskPolicies[PINNING_].available = false;
                kioskPolicies[PINNING_].subTitle = R.string.pref_kiosk_mode_screen_summary_3_pinning;
                kioskPolicies[FULL_].possible = true;
                kioskPolicies[FULL_].available = true;
                kioskPolicies[FULL_].subTitle = R.string.pref_kiosk_mode_screen_summary_2_full;
            }
            else {
                kioskPolicies[PINNING_].available = true;
                kioskPolicies[PINNING_].subTitle = R.string.pref_kiosk_mode_screen_summary_2_pinning;
                kioskPolicies[FULL_].possible = true;
                kioskPolicies[FULL_].available = false;
                kioskPolicies[FULL_].subTitle = R.string.settings_device_owner_required_alert;
            }
        }

        Preference kioskModeScreen = findPreference(KEY_KIOSK_SELECTION);
        KioskSelectionPreference preferenceFilteredList = (KioskSelectionPreference) kioskModeScreen;
        preferenceFilteredList.setPolicies(kioskPolicies);
        preferenceFilteredList.setOnNewValueListener((mode) -> {
                GlobalSettings.SettingsKioskMode oldMode = globalSettings.getKioskMode();
                // This is where we actually change the mode.
                globalSettings.setKioskModeNow(mode);
                // If we have any one-time prep work, do it here.
                kioskModeSwitcher.changeStaticKioskMode(oldMode, mode, (AppCompatActivity)getActivity());
            }
        );

        int summaryStringId = kioskModeSwitcher.getKioskModeSummary();
        kioskModeScreen.setSummary(summaryStringId);
    }

    @Override
    public void onStart() {
        super.onStart();
        eventBus.register(this);
    }

    @Override
    public void onStop() {
        eventBus.unregister(this);
        super.onStop();
    }

    @Override
    protected int getTitle() {
        return R.string.pref_kiosk_mode_screen_title;
    }

    // Event is from AesopPlayerDeviceAdmin
    // @SuppressWarnings("UnusedDeclaration")
    public void onEvent(@SuppressWarnings("unused") DeviceAdminChangeEvent deviceAdminChangeEvent) {
        // Kiosk mode just got forced to NONE if it was FULL or PINNING
        updateKioskModeSummary();

        FragmentManager mgr =  Objects.requireNonNull(getActivity()).getSupportFragmentManager();
        KioskSelectionFragmentCompat dialog = (KioskSelectionFragmentCompat)mgr.findFragmentByTag(TAG_KIOSK_DIALOG);
        if (dialog != null) {
            dialog.deviceOwnerChanged();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateKioskModeSummary();
    }

    private void updateUnregisterDeviceOwner(boolean isEnabled) {
        Preference preference = findPreference(KEY_UNREGISTER_DEVICE_OWNER);
        preference.setEnabled(isEnabled);
        preference.setSummary(getString(isEnabled
                ? R.string.pref_kiosk_mode_unregister_device_owner_summary_on
                : R.string.pref_kiosk_mode_unregister_device_owner_summary_off));
    }

    private void disableDeviceOwner() {
        // We just stopped being device owner. Switch to NONE for the same reasons
        // as above.
        if (globalSettings.getKioskMode() == GlobalSettings.SettingsKioskMode.FULL) {
            globalSettings.setKioskModeNow(GlobalSettings.SettingsKioskMode.NONE);
        }
        AesopPlayerDeviceAdmin.clearDeviceOwner(getActivity());
        updateKioskModeSummary();
    }
}
