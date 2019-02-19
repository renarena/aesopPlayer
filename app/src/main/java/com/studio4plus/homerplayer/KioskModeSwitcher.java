package com.studio4plus.homerplayer;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import androidx.appcompat.app.AlertDialog;

import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.events.KioskModeChanged;
import com.studio4plus.homerplayer.ui.HomeActivity;
import com.studio4plus.homerplayer.ui.KioskModeHandler;
import com.studio4plus.homerplayer.ui.MainActivity;

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

    public boolean isLockTaskPermitted() {
        return Build.VERSION.SDK_INT >= 21 && API21.isLockTaskPermitted(context);
    }

    public void onFullKioskModeEnabled(boolean fullKioskEnabled, Activity activity) {
        Preconditions.checkState(!fullKioskEnabled || isLockTaskPermitted());

        if (globalSettings.isSimpleKioskModeEnabled())
            onSimpleKioskModeEnabled(!fullKioskEnabled, activity);

        eventBus.post(new KioskModeChanged(KioskModeChanged.Type.FULL, fullKioskEnabled));

        if (fullKioskEnabled)
            API21.setPreferredHomeActivity(context, MainActivity.class);
        else
            API21.clearPreferredHomeActivity(context);
    }

    public void onSimpleKioskModeEnabled(boolean enable, Activity activity) {
        if (globalSettings.isFullKioskModeEnabled() & enable)
            return;

        HomeActivity.setEnabled(context, enable);
        if (enable) {
            // These requests pile up over the top of the Settings window until the user disposes
            // of them. API level matters for the Status Bar.
            if (android.os.Build.VERSION.SDK_INT <= 22) { // Lollipop and below
                // Get REORDER_TASKS and SYSTEM_ALERT_WINDOW
                KioskModeHandler.triggerSimpleKioskPermissionsIfNecessary(activity);
            }
            else if (android.os.Build.VERSION.SDK_INT <= 25) { // Marshmallow and Nougat
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
        }
        eventBus.post(new KioskModeChanged(KioskModeChanged.Type.SIMPLE, enable));
    }

    private void triggerHomeAppSelectionIfNecessary(Activity activity) {
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
                    new ComponentName(context, HomerPlayerDeviceAdmin.class);
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
                    new ComponentName(context, HomerPlayerDeviceAdmin.class);
            dpm.clearPackagePersistentPreferredActivities(
                    adminComponentName, context.getPackageName());
        }
    }
}
