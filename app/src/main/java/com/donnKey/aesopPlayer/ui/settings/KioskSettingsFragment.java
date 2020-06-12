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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;
import android.text.Spanned;
import android.text.SpannedString;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.AesopPlayerDeviceAdmin;
import com.donnKey.aesopPlayer.KioskModeSwitcher;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.accessibility.AesopAccessibility;
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

    private static final String DEVICE_OWNER_URL = AesopPlayerApplication.WEBSITE_URL + "install-full-kiosk.html";

    // These have to match the row numbers in the RadioButton list
    private static final int NONE_ = 0;
    private static final int SIMPLE_ = 1;
    private static final int PINNING_ = 2;
    private static final int FULL_ = 3;

    // This really should be an array with enum subscripts that we can guarantee the ordinal
    // of, but Java just won't do that. (Radio buttons are array-based.)
    static class KioskPolicy {
        boolean available;
        boolean possible;
        Spanned subTitle;
        final GlobalSettings.SettingsKioskMode kioskMode;
        final int slot;

        KioskPolicy(GlobalSettings.SettingsKioskMode kioskMode, int slot) {
            this.kioskMode = kioskMode;
            this.slot = slot;
            this.available = false;
            this.possible = false;
        }
    }

    private boolean tentativeMode_isSet = false;
    private static GlobalSettings.SettingsKioskMode tentativeMode;
    private boolean any_permNeeded = false;

    private final KioskPolicy[] kioskPolicies = new KioskPolicy[]{
            new KioskPolicy(GlobalSettings.SettingsKioskMode.NONE, NONE_),
            new KioskPolicy(GlobalSettings.SettingsKioskMode.SIMPLE, SIMPLE_),
            new KioskPolicy(GlobalSettings.SettingsKioskMode.PINNING, PINNING_),
            new KioskPolicy(GlobalSettings.SettingsKioskMode.FULL, FULL_)};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AesopPlayerApplication.getComponent(requireActivity()).inject(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_kiosk, rootKey);
        updateKioskModeSummary();
    }

    @Override
    public void onResume() {
        // If this resume is due to coming back from the Intent used to start the
        // Accessibility mode, we have work to do, depending on what the user did there.
        if (any_permNeeded && !AesopAccessibility.isAccessibilityConnected()) {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.pref_must_enable_accessibility)
                    .setMessage(R.string.pref_why_accessibility)
                    .setPositiveButton(android.R.string.ok, (a, b)-> startKioskSelectionFragment())
                    .setIcon(R.drawable.ic_launcher)
                    .show();
            // any_permNeeded will be updated by the NewValueListener below.
        }
        else if (tentativeMode_isSet){
            setNewKioskMode(tentativeMode);
            tentativeMode_isSet = false;
            any_permNeeded = false;
        }
        super.onResume();
    }

    private void updateKioskModeSummary() {
        // The user can pop up a dialog from the current screen. However, the dialog
        // varies a lot depending on the actual state of the device. The factors include
        // the current running Android version and whether Accessibility is enabled,
        // and whether we're the device owner.
        // We also don't want to change the mode until the user has enabled accessibility if it's
        // needed. The criteria for when accessibility is needed depends on the kiosk mode the
        // user selects, so we ask for it only when we need it, and come back if the user
        // doesn't set it, so they can select another mode if they don't want to use
        // accessibility.
        kioskPolicies[NONE_].possible = true;
        kioskPolicies[NONE_].available = true;
        kioskPolicies[NONE_].subTitle = getSpannableString(R.string.pref_kiosk_mode_screen_summary_2_disables);
        boolean simple_permissionsNeeded = false;
        boolean pinning_permissionsNeeded = false;

        // Simple Mode has lots of possibilities
        if (Build.VERSION.SDK_INT < 19) {
            // JB and prior: not available
            kioskPolicies[SIMPLE_].possible = false;
            kioskPolicies[SIMPLE_].available = false;
            kioskPolicies[SIMPLE_].subTitle = getSpannableString(R.string.pref_kiosk_mode_simple_summary_old_version);
        }
        else if (Build.VERSION.SDK_INT < 21) {
            // KK: Simple only
            kioskPolicies[SIMPLE_].possible = true;
            kioskPolicies[SIMPLE_].available = true;
            kioskPolicies[SIMPLE_].subTitle = getSpannableString(R.string.pref_kiosk_mode_simple_title_recommended);
        }
        else if (Build.VERSION.SDK_INT < 29) {
            // L-P: simple or pinning or full, suggest pinning
            kioskPolicies[SIMPLE_].possible = true;
            kioskPolicies[SIMPLE_].available = true;
            kioskPolicies[SIMPLE_].subTitle = getSpannableString(R.string.pref_kiosk_mode_simple_title_use_pinning);
        }
        else { // Q
            // In native Q (and above?), restarting the activity after pressing Home
            // or Recents (aka Overview) doesn't work without additional permissions.
            // Adding a no-op Accessibility Service <service> to the Manifest
            // (and the user enabling it), or acquiring the SYSTEM_ALERT_WINDOW permission
            // have the same effect of allowing that.
            // (For Pie and below it is possible without the additional permissions.)
            // The recovery from Back (on Q), when it does work, is slow and ugly, even as compared to Pie.
            simple_permissionsNeeded = !AesopAccessibility.isAccessibilityConnected();
            if (simple_permissionsNeeded) {
                kioskPolicies[SIMPLE_].subTitle = getSpannableString(R.string.pref_kiosk_mode_broken_need_acc);
            }
            else {
                kioskPolicies[SIMPLE_].subTitle = getSpannableString(R.string.pref_kiosk_mode_broken_acc_OK);
            }
            kioskPolicies[SIMPLE_].possible = true;
            kioskPolicies[SIMPLE_].available = true;
        }

        // App Pinning and Full.
        ConfirmDialogPreference preferenceUnregisterDeviceOwner =
                findPreference(KEY_UNREGISTER_DEVICE_OWNER);

        if (Build.VERSION.SDK_INT < 21) {
            // 4.x (JB) and below: no pinning or full Kiosk
            // ... device owner not available: display nothing
            if (preferenceUnregisterDeviceOwner != null) {
                // Already did it
                getPreferenceScreen().removePreference(preferenceUnregisterDeviceOwner);
            }

            kioskPolicies[PINNING_].possible = false;
            kioskPolicies[PINNING_].available = false;
            kioskPolicies[PINNING_].subTitle = getSpannableString(R.string.pref_kiosk_mode_full_summary_old_version);

            kioskPolicies[FULL_].possible = false;
            kioskPolicies[FULL_].available = false;
            kioskPolicies[FULL_].subTitle = getSpannableString(R.string.pref_kiosk_mode_full_summary_old_version);
        }
        else {
            // L and up: pinning/full Kiosk available

            // Display device owner state/allow unregister.
            // The user must confirm that they want to un-register the device owner,
            // so set up the dialog.
            Objects.requireNonNull(preferenceUnregisterDeviceOwner).setOnConfirmListener(this::disableDeviceOwner);
            boolean isDeviceOwner = AesopPlayerDeviceAdmin.isDeviceOwner(requireActivity());
            updateUnregisterDeviceOwner(isDeviceOwner);

            kioskPolicies[PINNING_].possible = true;

            if (isDeviceOwner) {
                kioskPolicies[PINNING_].available = false;
                kioskPolicies[PINNING_].subTitle = getSpannableString(R.string.pref_kiosk_mode_screen_summary_3_pinning);
                kioskPolicies[FULL_].possible = true;
                kioskPolicies[FULL_].available = true;
                kioskPolicies[FULL_].subTitle = getSpannableString(R.string.pref_kiosk_mode_screen_summary_2_full);
            }
            else {
                kioskPolicies[PINNING_].available = true;
                // N.B. API21 is weird, but AesopAccessibility works around it. If that should
                // become a problem, a fix to change behavior here might be needed.
                if (AesopAccessibility.isAccessibilityConnected()) {
                    kioskPolicies[PINNING_].subTitle = getSpannableString(R.string.pref_kiosk_mode_screen_summary_2_pinning_acc_OK);
                }
                else {
                    kioskPolicies[PINNING_].subTitle = getSpannableString(R.string.pref_kiosk_mode_screen_summary_2_pinning_need_acc);
                    pinning_permissionsNeeded = true;
                }
                kioskPolicies[FULL_].possible = true;
                kioskPolicies[FULL_].available = false;
                kioskPolicies[FULL_].subTitle = Html.fromHtml(getString(R.string.settings_device_owner_required_alert, DEVICE_OWNER_URL));
            }
        }
        // Make the lambda callback below happy
        final boolean pinning_permNeeded = pinning_permissionsNeeded;
        final boolean simple_permNeeded = simple_permissionsNeeded;

        // Set the short summary for the current screen
        Preference kioskModeScreen = findPreference(KEY_KIOSK_SELECTION);
        int summaryStringId = kioskModeSwitcher.getKioskModeSummary();
        Objects.requireNonNull(kioskModeScreen).setSummary(summaryStringId);

        // Set up the dialog screen including the callback
        KioskSelectionPreference preferenceFilteredList = (KioskSelectionPreference) kioskModeScreen;
        Objects.requireNonNull(preferenceFilteredList).setPolicies(kioskPolicies);
        preferenceFilteredList.setOnNewValueListener((mode) -> {
            // Respond to the user's selection. If there's other one-time permission
            // stuff in the future, it'd go here as well

            // We need to know what the mode the user selected was to decide if we want to
            // pop up the Accessibility Dialog.
            any_permNeeded = false;
            switch (mode) {
                case NONE:
                case FULL:
                    break;
                case SIMPLE:
                    any_permNeeded = simple_permNeeded;
                    break;
                case PINNING:
                    any_permNeeded = pinning_permNeeded;
                    break;
            }

            if (any_permNeeded & !AesopAccessibility.isAccessibilityConnected()) {
                // Start enable Accessibility activity. The accessibility service notes when it's turned on or off.
                // Can't get here on prior to Pie (28) because there's no "Got It" popup.
                if (Build.VERSION.SDK_INT < 29) {
                    // Ick.
                    // On Pie, the Back button (bottom of screen) works as you'd expect.
                    // However the back-arrow at the top moves out of accessibility settings
                    // into more general settings, and you don't automatically return to the app.
                    // On Q it works as you'd expect: both buttons do the same thing: return to the app.
                    // I was unable to find a workaround.
                    // Consequently, since it's a one-time thing, just warn the user.
                    new AlertDialog.Builder(requireActivity())
                            .setTitle(R.string.back_arrow_caution)
                            .setMessage(R.string.back_arrow_text)
                            .setPositiveButton(android.R.string.ok, (a, b)-> {
                                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            } )
                            .setIcon(R.drawable.ic_launcher)
                            .show();
                }
                else {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
                tentativeMode = mode;
                tentativeMode_isSet = true;
            } else {
                // Below calls updateKioskModeSummary, but it's not recursion... we're in
                // a lambda called from the dialog.
                setNewKioskMode(mode);
                tentativeMode_isSet = false;
            }
        });
    }

    private void setNewKioskMode(GlobalSettings.SettingsKioskMode mode) {
        GlobalSettings.SettingsKioskMode oldMode = globalSettings.getKioskMode();
        kioskModeSwitcher.changeStaticKioskMode(oldMode, mode, (AppCompatActivity)getActivity());
        globalSettings.setKioskModeNow(mode);
        updateKioskModeSummary();
    }

    private void startKioskSelectionFragment() {
        // See also SettingsActivity.java
        Preference kioskModeScreen = findPreference(KEY_KIOSK_SELECTION);
        assert kioskModeScreen != null;
        DialogFragment dialogFragment =
                KioskSelectionFragmentCompat.newInstance(kioskModeScreen.getKey());
        dialogFragment.setTargetFragment(this, 0);
        dialogFragment.show(requireActivity().getSupportFragmentManager(), TAG_KIOSK_DIALOG);
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

    // Event is from AesopPlayerDeviceAdmin to let us know adb changed something.
    public void onEvent(@SuppressWarnings("unused") DeviceAdminChangeEvent deviceAdminChangeEvent) {
        // Kiosk mode just got forced to NONE if it was FULL or PINNING
        updateKioskModeSummary();

        FragmentManager mgr =  requireActivity().getSupportFragmentManager();
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
        Objects.requireNonNull(preference).setEnabled(isEnabled);
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

    private Spanned getSpannableString(int resId, Object ... formatArgs) {
        return new SpannedString(getString(resId, formatArgs));
    }
}
