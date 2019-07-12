package com.donnKey.aesopPlayer;

import android.annotation.TargetApi;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.events.KioskModeChanged;
import com.donnKey.aesopPlayer.ui.HomeActivity;
import com.donnKey.aesopPlayer.ui.KioskModeHandler;
import com.donnKey.aesopPlayer.ui.MainActivity;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

@ApplicationScope
public class KioskModeSwitcher {

    private final Context context;
    private final GlobalSettings globalSettings;
    private final EventBus eventBus;

    @Inject
    KioskModeSwitcher(Context applicationContext, GlobalSettings globalSettings,
                      EventBus eventBus) {
        this.context = applicationContext;
        this.globalSettings = globalSettings;
        this.eventBus = eventBus;
    }

    public void onKioskModeChanged(GlobalSettings.SettingsKioskMode newMode,
                                   AppCompatActivity activity) {
        GlobalSettings.SettingsKioskMode oldMode = globalSettings.getKioskMode();
        // See note in Active... must do changeActiveKioskMode last
        globalSettings.setKioskModeNow(newMode);
        if (!globalSettings.isMaintenanceMode()) {
            changeActiveKioskMode(oldMode, newMode, activity);
        }
    }

    private void changeActiveKioskMode(GlobalSettings.SettingsKioskMode oldMode,
                                       GlobalSettings.SettingsKioskMode newMode,
                                       AppCompatActivity activity) {

        // Be very careful here - this doesn't always return - see trigger... below.

        // Turn off the old mode.
        eventBus.post(new KioskModeChanged(oldMode, false));
        switch (oldMode) {
            case NONE: {
                break;
            }
            case SIMPLE: {
                try {
                    HomeActivity.setEnabled(context, false);
                } catch (Exception e) {
                    // ignore (just in case)
                }
                break;
            }
            case PINNING: {
                // Normally, exit from pinning is achieved by simultaneously pressing
                // the Back and Recents (triangle and square) keys, but that's not possible
                // remotely. So this provides a way to do it for remote administrators.
                // (Remember, this case can't be reached for <21 because the choice is not offered
                try {
                    stopAppPinning(activity);
                } catch (Exception e) {
                    // ignore (just in case)
                }
                break;
            }
            case FULL: {
                try {
                    API21.clearPreferredHomeActivity(context);
                } catch (Exception e) {
                    // ignore (just in case)
                }
                break;
            }
        }

        // ...And turn on the new. Note call to trigger... below
        eventBus.post(new KioskModeChanged(newMode, true));
        switch (newMode) {
            case NONE: {
                break;
            }
            case SIMPLE: {
                HomeActivity.setEnabled(context, true);
                // These requests pile up over the top of the Settings window until the user disposes
                // of them. API level matters for the Status Bar.
                if (android.os.Build.VERSION.SDK_INT <= 22) { // Lollipop and below
                    // Get REORDER_TASKS and SYSTEM_ALERT_WINDOW
                    KioskModeHandler.triggerSimpleKioskPermissionsIfNecessary(activity);
                } else if (android.os.Build.VERSION.SDK_INT <= 25) { // Marshmallow and Nougat
                    // At API23 we need SYSTEM_ALERT_WINDOW permission; as of 26, the permission is
                    // useless because it's too weak. MainActivity has an alternate solution.
                    //
                    // This gets us SYSTEM_ALERT_WINDOW implicitly, but that's not a normal
                    // permission at API 23. (Asking for SYSTEM_ALERT_WINDOW in the old way will
                    // never succeed, creating a loop for the user.)
                    KioskModeHandler.triggerOverlayPermissionsIfNecessary(activity);
                }
                //else { // Oreo and up
                // Nothing (yet)
                //}

                // Should be last as it exits Settings forcibly when it happens
                triggerHomeAppSelectionIfNecessary(activity);
                break;
            }
            case PINNING: {
                startAppPinning(activity);
                break;
            }
            case FULL: {
                Preconditions.checkState(isLockTaskPermitted());
                API21.setPreferredHomeActivity(context, MainActivity.class);
                break;
            }
        }
    }

    public boolean isLockTaskPermitted() {
        return Build.VERSION.SDK_INT >= 21 && API21.isLockTaskPermitted(context);
    }

    public void startAppPinning(AppCompatActivity activity) {
        Preconditions.checkState(Build.VERSION.SDK_INT >= 21);
        //noinspection ConstantConditions
        if(Build.VERSION.SDK_INT >= 21) { // L
            try {
                activity.startLockTask();
                // The system provides a Toast when it does this.
            } catch (Exception e) {
                // Shouldn't be possible
            }
        }
    }

    public void stopAppPinning(AppCompatActivity activity) {
        Preconditions.checkState(Build.VERSION.SDK_INT >= 21);
        //noinspection ConstantConditions
        if(Build.VERSION.SDK_INT >= 21) { // L
            try {
                activity.stopLockTask();
                // The system provides a Toast when it does this.
            } catch (Exception e) {
                // I haven't seen this happen, but it can't hurt
                Toast.makeText(activity, R.string.pref_kiosk_already_unlocked_toast, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public int getKioskModeSummary() {
        switch (globalSettings.getKioskMode()) {
            default:
            case NONE:
                return R.string.pref_kiosk_mode_screen_summary_disabled;
            case SIMPLE:
                return R.string.pref_kiosk_mode_screen_summary_simple;
            case PINNING:
                return R.string.pref_kiosk_mode_screen_summary_pinned;
            case FULL:
                return R.string.pref_kiosk_mode_screen_summary_full;
        }
    }

    public void setKioskMaintenanceMode(AppCompatActivity activity, boolean enable) {
        GlobalSettings.SettingsKioskMode realKioskMode = globalSettings.getKioskMode();
        if (enable) {
            // Turn it on - disable Kiosk mode
            changeActiveKioskMode(realKioskMode,
                    GlobalSettings.SettingsKioskMode.NONE, activity);
        }
        else {
            // Turn it off - re-enable actual kiosk mode
            changeActiveKioskMode(GlobalSettings.SettingsKioskMode.NONE,
                    realKioskMode, activity);
        }
    }

    private void triggerHomeAppSelectionIfNecessary(AppCompatActivity activity) {
        // This creates a new Intent with the effect of restarting the main activity,
        // with the side-effect of setting it (with user approval) as the default
        // activity/app. That means we'll be yanked out of Settings in the process.
        // This can be called at any sensible time, but the actual home selection process
        // occurs on the new task so there's no "result" to get.

        final Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addCategory(Intent.CATEGORY_DEFAULT);

        // Necessary because application context is used.
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PackageManager pm = context.getPackageManager();
        ResolveInfo resolveInfo = pm.resolveActivity(homeIntent, 0);
        if (resolveInfo.activityInfo.name.equals("com.android.internal.app.ResolverActivity")) {

            // Tell the user what's going to happen and then do it (in lambda) after OK.
            // (The context we have doesn't work at runtime, thus the activity parameter.)
            String coachingMessage = activity.getString(R.string.permission_rationale_home_screen);
            if (android.os.Build.VERSION.SDK_INT <= 20) { // KitKat and below
                coachingMessage += activity.getString(R.string.permission_rationale_home_screen_more1);
            }
            else {
                coachingMessage += activity.getString(R.string.permission_rationale_home_screen_more2);
            }
            coachingMessage += activity.getString(R.string.permission_rationale_home_screen_more3);
            new AlertDialog.Builder(activity)
                .setTitle(R.string.permission_rationale_simple_default_app)
                .setMessage(coachingMessage)
                .setPositiveButton(android.R.string.ok,
                    (a, b)-> {
                        // The real work
                        context.startActivity(homeIntent);
                    }
                )
                .setIcon(R.mipmap.ic_launcher)
                .show();
        }
    }

    @TargetApi(21)
    static class API21 {

        static boolean isLockTaskPermitted(Context context) {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            return dpm != null && dpm.isLockTaskPermitted(context.getPackageName())
                    && dpm.isDeviceOwnerApp(context.getPackageName());
        }

        @SuppressWarnings("SameParameterValue") // For a future?
        static void setPreferredHomeActivity(Context context, Class activityClass) {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            Preconditions.checkNotNull(dpm);
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
            intentFilter.addCategory(Intent.CATEGORY_HOME);
            intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
            ComponentName adminComponentName =
                    new ComponentName(context, AesopPlayerDeviceAdmin.class);
            ComponentName activityComponentName =
                    new ComponentName(context, activityClass);
            dpm.addPersistentPreferredActivity(
                    adminComponentName, intentFilter, activityComponentName);
        }

        static void clearPreferredHomeActivity(Context context) {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            Preconditions.checkNotNull(dpm);
            ComponentName adminComponentName =
                    new ComponentName(context, AesopPlayerDeviceAdmin.class);
            dpm.clearPackagePersistentPreferredActivities(
                    adminComponentName, context.getPackageName());
        }
    }
}
