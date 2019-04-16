package com.studio4plus.homerplayer.ui.settings;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.SwitchPreference;
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

    @Inject public KioskModeSwitcher kioskModeSwitcher;
    @Inject public EventBus eventBus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        HomerPlayerApplication.getComponent(Objects.requireNonNull(getActivity())).inject(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_kiosk, rootKey);

        if (Build.VERSION.SDK_INT < 21) {
            Preference kioskModePreference = findPreference(GlobalSettings.KEY_KIOSK_MODE);
            kioskModePreference.setEnabled(false);
        }
        if (Build.VERSION.SDK_INT < 19) {
            Preference simpleKioskModePreference =
                    findPreference(GlobalSettings.KEY_SIMPLE_KIOSK_MODE);
            simpleKioskModePreference.setEnabled(false);
        }
        updateKioskModeSummaries();

        ConfirmDialogPreference preferenceUnregisterDeviceOwner =
                (ConfirmDialogPreference) findPreference(KEY_UNREGISTER_DEVICE_OWNER);
        if (Build.VERSION.SDK_INT >= 21) {
            preferenceUnregisterDeviceOwner.setOnConfirmListener(
                    this::disableDeviceOwner);
            updateUnregisterDeviceOwner(HomerPlayerDeviceAdmin.isDeviceOwner(getActivity()));
        } else {
            getPreferenceScreen().removePreference(preferenceUnregisterDeviceOwner);
        }

        // Application pinning is not the same as Kiosk mode according to Google (see the
        // distinction made for getLockTaskModeState() made below), but
        // they are very similar. Normally, exit is achieved by simultaneously pressing
        // the Back and Recents (triangle and square) keys, but that's not possible
        // remotely. So provide a way to do it for remote administrators.
        ConfirmDialogPreference preferenceClearPinning =
                (ConfirmDialogPreference) findPreference(KEY_CLEAR_PINNING);
        if (Build.VERSION.SDK_INT >= 21) {
            // Set up the listener
            preferenceClearPinning.setOnConfirmListener(
                () -> {
                    try {
                        Objects.requireNonNull(getActivity()).stopLockTask();
                        // The system provides a Toast when it does this.
                    } catch (Exception e) {
                        // I haven't seen this happen, but it can't hurt
                        Toast.makeText(getActivity(), getString(R.string.pref_kiosk_already_unlocked_toast), Toast.LENGTH_SHORT).show();
                    }
                    // Setting below won't stick on API 21-22... sigh.
                    Preference clearApplicationPinningPreference =
                            findPreference(KEY_CLEAR_PINNING);
                    clearApplicationPinningPreference.setEnabled(false);
                }
                );
            // If we don't need it after all, disable it. Ugly, but anything else is worse
            // given the disparate version levels.
            if (Build.VERSION.SDK_INT >= 23) {
                ActivityManager activityManager =
                        (ActivityManager) Objects.requireNonNull(getContext())
                                .getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager.getLockTaskModeState() != LOCK_TASK_MODE_PINNED)
                {
                    Preference clearApplicationPinningPreference =
                            findPreference(KEY_CLEAR_PINNING);
                    clearApplicationPinningPreference.setEnabled(false);
                }
            }
        }
        else {
            getPreferenceScreen().removePreference(preferenceClearPinning);
        }

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
        updateUnregisterDeviceOwner(deviceAdminChangeEvent.isEnabled);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case GlobalSettings.KEY_KIOSK_MODE:
                onKioskModeSwitched(sharedPreferences);
                break;
            case GlobalSettings.KEY_SIMPLE_KIOSK_MODE:
                kioskModeSwitcher.onSimpleKioskModeEnabled(sharedPreferences.getBoolean(key, false), getActivity());
                updateKioskModeSummaries();
                break;
        }
    }

    private void updateKioskModeSummaries() {
        SwitchPreference fullModePreference =
                (SwitchPreference) findPreference(GlobalSettings.KEY_KIOSK_MODE);
        {
            int summaryStringId;
            boolean isLockedPermitted = kioskModeSwitcher.isLockTaskPermitted();
            if (Build.VERSION.SDK_INT < 21) {
                summaryStringId = R.string.pref_kiosk_mode_full_summary_old_version;
            } else if (!isLockedPermitted) {
                summaryStringId = R.string.settings_device_owner_required_alert;
            } else {
                summaryStringId = fullModePreference.isChecked()
                        ? R.string.pref_kiosk_mode_any_summary_on
                        : R.string.pref_kiosk_mode_any_summary_off;
            }
            fullModePreference.setSummary(summaryStringId);
        }

        SwitchPreference simpleModePreference =
                (SwitchPreference) findPreference(GlobalSettings.KEY_SIMPLE_KIOSK_MODE);
        {
            int summaryStringId;
            if (Build.VERSION.SDK_INT < 19) {
                summaryStringId = R.string.pref_kiosk_mode_simple_summary_old_version;
            } else {
                summaryStringId = simpleModePreference.isChecked()
                        ? R.string.pref_kiosk_mode_any_summary_on
                        : R.string.pref_kiosk_mode_any_summary_off;
            }
            simpleModePreference.setSummary(summaryStringId);
            simpleModePreference.setEnabled(!fullModePreference.isChecked());
        }
    }

    private void updateUnregisterDeviceOwner(boolean isEnabled) {
        Preference preference = findPreference(KEY_UNREGISTER_DEVICE_OWNER);
        preference.setEnabled(isEnabled);
        preference.setSummary(getString(isEnabled
                ? R.string.pref_kiosk_mode_unregister_device_owner_summary_on
                : R.string.pref_kiosk_mode_unregister_device_owner_summary_off));
    }

    private void disableDeviceOwner() {
        SwitchPreference kioskModePreference =
                (SwitchPreference) findPreference(GlobalSettings.KEY_KIOSK_MODE);
        kioskModePreference.setChecked(false);
        HomerPlayerDeviceAdmin.clearDeviceOwner(getActivity());
    }

    @SuppressLint({"CommitPrefEdits", "ApplySharedPref"}) // editor.commit() seems safer in this case
    private void onKioskModeSwitched(SharedPreferences sharedPreferences) {
        boolean newKioskModeEnabled =
                sharedPreferences.getBoolean(GlobalSettings.KEY_KIOSK_MODE, false);
        boolean isLockedPermitted = kioskModeSwitcher.isLockTaskPermitted();
        if (newKioskModeEnabled && !isLockedPermitted) {
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setMessage(getResources().getString(
                            R.string.settings_device_owner_required_alert))
                    .setNeutralButton(android.R.string.ok, null)
                    .create();
            dialog.show();

            SwitchPreference switchPreference =
                    (SwitchPreference) findPreference(GlobalSettings.KEY_KIOSK_MODE);
            switchPreference.setChecked(false);
            // Beware: the code below causes this function to be recursively entered again.
            // It should be the last thing the function does.
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(GlobalSettings.KEY_KIOSK_MODE, false);
            editor.commit();
            return;
        }
        if (isLockedPermitted)
            kioskModeSwitcher.onFullKioskModeEnabled(newKioskModeEnabled, getActivity());
        updateKioskModeSummaries();
    }
}
