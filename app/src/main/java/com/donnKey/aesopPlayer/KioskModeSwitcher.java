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

import android.annotation.TargetApi;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.donnKey.aesopPlayer.accessibility.AesopAccessibility;
import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.ui.HomeActivity;
import com.donnKey.aesopPlayer.ui.KioskModeHandler;
import com.donnKey.aesopPlayer.ui.MainActivity;

import javax.inject.Inject;

@ApplicationScope
public class KioskModeSwitcher {

    private final Context context;
    static private GlobalSettings globalSettings = null;
    private static final String TAG="KioskModeSwitcher";

    @Inject
    KioskModeSwitcher(Context applicationContext, GlobalSettings gS) {
        this.context = applicationContext;
        globalSettings = gS;
    }

    enum activityState {active, inactive}
    private static activityState state = activityState.inactive;

    public void switchToCurrentKioskMode(AppCompatActivity activity) {
        // Called from onResume in MainActivity (frequently redundantly)
        if (state == activityState.active) {
            return;
        }
        state = activityState.active;
        if (!globalSettings.isMaintenanceMode()) {
            GlobalSettings.SettingsKioskMode newMode = globalSettings.getKioskMode();
            changeActiveKioskMode(GlobalSettings.SettingsKioskMode.NONE, newMode, activity);
        }
    }

    public void switchToNoKioskMode(AppCompatActivity activity) {
        // Called from onResume in SettingsActivity (frequently redundantly)
        if (state == activityState.inactive) {
            return;
        }
        state = activityState.inactive;
        if (!globalSettings.isMaintenanceMode()) {
            GlobalSettings.SettingsKioskMode oldMode = globalSettings.getKioskMode();
            changeActiveKioskMode(oldMode, GlobalSettings.SettingsKioskMode.NONE, activity);
        }
    }

    // Change the Kiosk mode stuff that only changes when actually setting Kiosk
    // mode. Below we handle dynamic changes.
    public void changeStaticKioskMode(GlobalSettings.SettingsKioskMode oldMode,
                                      GlobalSettings.SettingsKioskMode newMode,
                                      AppCompatActivity activity) {
        if (oldMode == newMode) {
            return;
        }

        // Turn off the old mode.
        switch (oldMode) {
            case NONE: {
                break;
            }
            case SIMPLE: {
                globalSettings.setAccessibilityNeeded(false);
                // Unset the home activity -- deprecated at API29, but works prior
                // If it doesn't work, turning off simple won't let you get back to root screen!
                context.getPackageManager().clearPackagePreferredActivities(context.getPackageName());

                try {
                    HomeActivity.setEnabled(context, false);
                } catch (Exception e) {
                    // ignore (just in case)
                }
                break;
            }
            case PINNING: {
                globalSettings.setAccessibilityNeeded(false);
                break;
            }
            case FULL: {
                try {
                    API21.clearPreferredHomeActivity(context);
                } catch (Exception e) {
                    // ignore (just in case)
                }
                // Unset the home activity -- deprecated at API29, but works prior
                context.getPackageManager().clearPackagePreferredActivities(context.getPackageName());
                break;
            }
        }

        switch (newMode) {
            case NONE:
            case PINNING: {
                break;
            }
            case SIMPLE: {
                registerDeviceOwnerAsNeeded(activity);
                HomeActivity.setEnabled(context, true);
                break;
            }
            case FULL: {
                Preconditions.checkState(isLockTaskPermitted(context));
                if (AesopPlayerDeviceAdmin.isDeviceOwner(context)) {
                    try {
                        API21.setPreferredHomeActivity(context, MainActivity.class);
                    } catch (Exception e) {
                        // ignore (just in case)
                    }
                }
                break;
            }
        }
    }
    private void changeActiveKioskMode(GlobalSettings.SettingsKioskMode oldMode,
                                       GlobalSettings.SettingsKioskMode newMode,
                                       AppCompatActivity activity) {
        if (oldMode == newMode) {
            return;
        }
        // Turn off the old mode.
        switch (oldMode) {
            case NONE: {
                break;
            }
            case SIMPLE: {
                KioskModeHandler.controlStatusBarExpansion(context, false);
                break;
            }
            case PINNING:
            case FULL: {
                // (Remember, this case can't be reached for <21 because the choice is not offered
                stopAppPinning(activity);
                setNavigationVisibility(activity, true);
                break;
            }
        }

        switch (newMode) {
            case NONE: {
                break;
            }
            case SIMPLE: {
                KioskModeHandler.controlStatusBarExpansion(context, true);
                break;
            }
            case PINNING: {
                if (!AesopAccessibility.isAccessibilityConnected()) {
                    state = activityState.inactive;
                    break;
                }
                // See note just below for why this:
                AesopAccessibility.activateCheck();

                // Be cautious not to over-call startAppPinning: it fires the dialog about app
                // pinning even when it's already pinned.
                startAppPinning(activity);
                setNavigationVisibility(activity, false);

                // For the purposes of suppressing the "Got It" popup (see AesopAccessibility),
                // we need to GUARANTEE an accessibility event after the lock task takes. Simply
                // sending a generic accessibility event doesn't seem to work because it's (I think)
                // "behind" the popup. However a Toast does work (yielding a NOTIFICATION_STATE_CHANGED).
                // Make the toast text match what the system uses.
                // All Android versions tested showed an occasional failure without this.
                // L.0 simply NEVER worked when using "Back" (hardware) button to exit settings.
                // P & Q *almost* always worked without this.
                // This also must occur so that it occurs after the system puts up the screen
                // pinning notice.
                // (When testing: test startup, exiting settings via back arrow and the back button,
                // and separately startup from "apps" after killing any previous instance. Each of
                // those showed odd failures.)
                Toast.makeText(activity, R.string.screen_pinning_toast, Toast.LENGTH_SHORT).show();
                break;
            }

            case FULL: {
                // (Remember, this case can't be reached for <21 because the choice is not offered
                startAppPinning(activity);
                setNavigationVisibility(activity,false);
                break;
            }
        }
    }

    public static void enableMaintenanceMode(AppCompatActivity activity, boolean enabled) {
        globalSettings.setMaintenanceMode(enabled);

        if (enabled) {
            // In case it got accidentally disabled
            enableAccessibilityIfNeeded(activity);
        }
    }

    private void setNavigationVisibility(AppCompatActivity activity, boolean show) {
        // Causes tool and nav bars to display (in a dark grey), but with
        // nothing at all on them. Without this, it's black, but with a 'back' button
        // (that's disabled but for a toast).
        if (Build.VERSION.SDK_INT < 19)
            return;

        View decorView = activity.getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        int visibilitySetting = decorView.getSystemUiVisibility();
        if (show)
            visibilitySetting &= ~flags;
        else
            visibilitySetting |= flags;

        decorView.setSystemUiVisibility(visibilitySetting);
    }

    public static boolean isLockTaskPermitted(Context context) {
        return Build.VERSION.SDK_INT >= 21 && API21.isLockTaskPermitted(context);
    }

    public static void startAppPinning(AppCompatActivity activity) {
        if(Build.VERSION.SDK_INT >= 21) { // L
            // The system provides a Toast when it does this.
            activity.startLockTask();
        }
    }

    private void stopAppPinning(AppCompatActivity activity) {
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

    private void registerDeviceOwnerAsNeeded(AppCompatActivity activity) {
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

        // Needed to make 'trigger' below happen now.
        HomeActivity.setEnabled(context, true);

        // Should be last as it exits Settings forcibly when it happens
        triggerHomeAppSelectionIfNecessary(activity);
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

        PackageManager pm = activity.getPackageManager();
        ResolveInfo resolveInfo = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        assert resolveInfo != null;

        if (!resolveInfo.activityInfo.name.equals("com.donnKey.aesopPlayer.ui.HomeActivity")) {
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
                .setTitle(R.string.permission_rationale_default_app)
                .setMessage(coachingMessage)
                .setPositiveButton(android.R.string.ok,
                    (a, b)-> {
                        // The real work
                        // Very important to use activity on Pie
                        activity.startActivity(homeIntent);
                    }
                )
                .setIcon(R.drawable.ic_launcher)
                .show();
        }
    }

    public static void enableAccessibility(@NonNull AppCompatActivity activity, boolean fromStartup) {
        // Ick.
        // On Pie, the Back button (bottom of screen) works as you'd expect.
        // However the back-arrow at the top moves out of accessibility settings
        // into more general settings, and you don't automatically return to the app.
        // On Q it works as you'd expect: both buttons do the same thing: return to the app.
        // I was unable to find a workaround.
        // Consequently, since it's a one-time thing, just warn the user.
        String title = activity.getString(R.string.enable_accessibility);
        String body = activity.getString(R.string.allow_aesop);
        if (Build.VERSION.SDK_INT < 29) {
            title += "\n" + activity.getString(R.string.back_arrow_caution);
            body += "\n\n" + activity.getString(R.string.back_arrow_text);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton(android.R.string.ok, (a, b) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                })
                .setIcon(R.drawable.ic_launcher);
        if (fromStartup) {
            builder.setNegativeButton("Start Maintenance Mode" + "    ", (a,b)->{
                globalSettings.setMaintenanceMode(true);
                activity.recreate();
            });
        }
        builder.show();
    }

    public static void enableAccessibilityIfNeeded(AppCompatActivity activity) {
        if (!globalSettings.isAccessibilityNeeded()) {
            return;
        }
        if (AesopAccessibility.isAccessibilityConnected()) {
            return;
        }
        if (globalSettings.isMaintenanceMode()) {
            return;
        }
        enableAccessibility(activity, true);
    }

    @TargetApi(21)
    private static class API21 {
        static boolean isLockTaskPermitted(@NonNull Context context) {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            return dpm != null && dpm.isLockTaskPermitted(context.getPackageName());
        }

        @SuppressWarnings("SameParameterValue") // For a future?
        static void setPreferredHomeActivity(@NonNull Context context, @SuppressWarnings("rawtypes") Class activityClass) {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            assert dpm != null;
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

        static void clearPreferredHomeActivity(@NonNull Context context) {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            assert dpm != null;
            ComponentName adminComponentName =
                    new ComponentName(context, AesopPlayerDeviceAdmin.class);
            dpm.clearPackagePersistentPreferredActivities(
                    adminComponentName, context.getPackageName());
        }
    }
}
