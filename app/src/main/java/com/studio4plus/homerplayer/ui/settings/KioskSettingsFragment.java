package com.studio4plus.homerplayer.ui.settings;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;

import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.HomerPlayerApplication;
import com.studio4plus.homerplayer.HomerPlayerDeviceAdmin;
import com.studio4plus.homerplayer.KioskModeSwitcher;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.events.DeviceAdminChangeEvent;

import java.util.Objects;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

import static android.app.ActivityManager.LOCK_TASK_MODE_PINNED;

public class KioskSettingsFragment extends BaseSettingsFragment {

    private static final String KEY_UNREGISTER_DEVICE_OWNER = "unregister_device_owner_preference";
    private static final String KEY_CLEAR_PINNING = "clear_application_pinning";
    private static final String KEY_FILTERED_LIST = "kiosk_choice_preference";

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
        HomerPlayerApplication.getComponent(Objects.requireNonNull(getActivity())).inject(this);
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
        ConfirmDialogPreference preferenceClearPinning =
                (ConfirmDialogPreference) findPreference(KEY_CLEAR_PINNING);

        if (Build.VERSION.SDK_INT < 21) { // L
            getPreferenceScreen().removePreference(preferenceClearPinning);
            getPreferenceScreen().removePreference(preferenceUnregisterDeviceOwner);

            kioskPolicies[PINNING_].possible = false;
            kioskPolicies[PINNING_].available = false;
            kioskPolicies[PINNING_].subTitle = R.string.pref_kiosk_mode_full_summary_old_version;

            kioskPolicies[FULL_].possible = false;
            kioskPolicies[FULL_].available = false;
            kioskPolicies[FULL_].subTitle = R.string.pref_kiosk_mode_full_summary_old_version;
        }
        else {
            preferenceUnregisterDeviceOwner.setOnConfirmListener(this::disableDeviceOwner);
            updateUnregisterDeviceOwner(HomerPlayerDeviceAdmin.isDeviceOwner(getActivity()));

            kioskPolicies[PINNING_].possible = true;
            kioskPolicies[PINNING_].available = true;
            kioskPolicies[PINNING_].subTitle = R.string.pref_kiosk_mode_screen_summary_2_pinning;

            if (HomerPlayerDeviceAdmin.isDeviceOwner(getActivity())) {
                kioskPolicies[FULL_].possible = true;
                kioskPolicies[FULL_].available = true;
                kioskPolicies[FULL_].subTitle = R.string.pref_kiosk_mode_screen_summary_2_full;
            }
            else {
                kioskPolicies[FULL_].possible = true;
                kioskPolicies[FULL_].available = false;
                kioskPolicies[FULL_].subTitle = R.string.settings_device_owner_required_alert;
            }
        }

        // Application pinning is not the same as Kiosk mode according to Google (see the
        // distinction made for getLockTaskModeState() made below), but
        // they are very similar. Normally, exit is achieved by simultaneously pressing
        // the Back and Recents (triangle and square) keys, but that's not possible
        // remotely. So provide a way to do it for remote administrators.
        if (Build.VERSION.SDK_INT >= 21) { // L
            // Set up the listener
            preferenceClearPinning.setOnConfirmListener(
                    () -> {
                        kioskModeSwitcher.stopAppPinning((AppCompatActivity)getActivity());
                        // Setting below won't stick on API 21-22... sigh.
                        Preference clearApplicationPinningPreference =
                                findPreference(KEY_CLEAR_PINNING);
                        clearApplicationPinningPreference.setEnabled(false);
                    }
            );
            // If we don't need it after all, disable it. Ugly, but anything else is worse
            // given the disparate version levels.
            // (Only at API23 can we tell if we're pinned.)
            // (Effectively: say if we're pinned.)
            if (Build.VERSION.SDK_INT >= 23) { // M
                ActivityManager activityManager =
                        (ActivityManager) Objects.requireNonNull(getContext())
                                .getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager.getLockTaskModeState() != LOCK_TASK_MODE_PINNED) {
                    Preference clearApplicationPinningPreference =
                            findPreference(KEY_CLEAR_PINNING);
                    clearApplicationPinningPreference.setEnabled(false);
                }
            }
        }

        KioskSelectionPreference preferenceFilteredList =
                (KioskSelectionPreference) findPreference(KEY_FILTERED_LIST);
        preferenceFilteredList.setPolicies(kioskPolicies);
        preferenceFilteredList.setOnNewValueListener((mode) ->
                kioskModeSwitcher.onKioskModeChanged(mode,(AppCompatActivity)getActivity()));

        Preference kioskModeScreen = findPreference(KEY_FILTERED_LIST);
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

    @SuppressWarnings("UnusedDeclaration")
    public void onEvent(DeviceAdminChangeEvent deviceAdminChangeEvent) {
        updateKioskModeSummary();
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
        HomerPlayerDeviceAdmin.clearDeviceOwner(getActivity());
        updateKioskModeSummary();
    }
}
