package com.studio4plus.homerplayer.ui;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import androidx.appcompat.app.AlertDialog;

import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.events.KioskModeChanged;
import com.studio4plus.homerplayer.analytics.AnalyticsTracker;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import de.greenrobot.event.EventBus;

@ActivityScope
public class KioskModeHandler {

    private static final String SCREEN_LOCK_PREFS = "ScreenLocker";
    private static final String PREF_SCREEN_LOCK_ENABLED = "screen_lock_enabled";

    private static final int PERMISSION_REQUEST_FOR_SIMPLE_KIOSK = 2;
    private static final int PERMISSION_REQUEST_FOR_MANAGE_OVERLAYS = 3;

    private final @NonNull Activity activity;
    private final @NonNull GlobalSettings globalSettings;
    private final @NonNull EventBus eventBus;
    private final @NonNull AnalyticsTracker analyticsTracker;
    private boolean keepNavigation = false;

    @Inject
    KioskModeHandler(@NonNull Activity activity,
                     @NonNull GlobalSettings settings,
                     @NonNull AnalyticsTracker analyticsTracker,
                     @NonNull EventBus eventBus) {
        this.activity = activity;
        this.globalSettings = settings;
        this.eventBus = eventBus;
        this.analyticsTracker = analyticsTracker;
    }

    public void setKeepNavigation(Boolean keepNavigation) {
        this.keepNavigation = keepNavigation;
    }

    public void onActivityStart() {
        if (!globalSettings.isFullKioskModeEnabled() && isLockTaskEnabled())
            lockTask(false);
        setUiFlagsAndLockTask();
        eventBus.register(this);
    }

    public void onActivityStop() {
        eventBus.unregister(this);
    }

    public void onFocusGained() {
        setUiFlagsAndLockTask();
    }

    @SuppressWarnings("unused") // Used on EventBus
    public void onEvent(KioskModeChanged event) {
        if (event.type == KioskModeChanged.Type.FULL) {
            lockTask(event.isEnabled);
        }
        setNavigationVisibility(!event.isEnabled);
    }

    public static void triggerSimpleKioskPermissionsIfNecessary(Activity activity) {
        PermissionUtils.checkAndRequestPermission(
                activity,
                new String[]{Manifest.permission.REORDER_TASKS,
                        Manifest.permission.SYSTEM_ALERT_WINDOW},
                PERMISSION_REQUEST_FOR_SIMPLE_KIOSK);
    }

    // This callback comes via Settings Activity
    public void onRequestPermissionResult(
            int code, final @NonNull String[] permissions, final @NonNull int[] grantResults) {
        switch (code) {
        case PERMISSION_REQUEST_FOR_SIMPLE_KIOSK:
            //noinspection StatementWithEmptyBody
            if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // Have them now, will need them later but not right now.
            } else {
                boolean canRetry =
                        ActivityCompat.shouldShowRequestPermissionRationale(
                                activity, Manifest.permission.REORDER_TASKS) ||
                        ActivityCompat.shouldShowRequestPermissionRationale(
                                activity, Manifest.permission.SYSTEM_ALERT_WINDOW);
                AlertDialog.Builder dialogBuilder = PermissionUtils.permissionRationaleDialogBuilder(
                        activity, R.string.permission_rationale_simple_kiosk);
                // The only live scenarios appear to be <=22, when the permissions are just
                // granted, or >=23, where SYSTEM_ALERT_WINDOW is never granted (no chance of
                // retry), thus this is never called. But this can't hurt if there's some middle.
                if (canRetry) {
                    dialogBuilder.setPositiveButton(
                            R.string.permission_rationale_try_again,
                            (dI, i) -> PermissionUtils.checkAndRequestPermission(
                                    activity, permissions, PERMISSION_REQUEST_FOR_SIMPLE_KIOSK));
                } else {
                    analyticsTracker.onPermissionRationaleShown("simpleKioskEnable");
                    dialogBuilder.setPositiveButton(
                            R.string.permission_rationale_settings,
                            (dI, i) -> PermissionUtils.openAppSettings(activity));
                }
                dialogBuilder.setNegativeButton(
                        R.string.permission_rationale_exit,
                        (dI, i) -> forceExit(activity));
                dialogBuilder.create().show();
            }
            break;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean canDrawOverlays(Context context)
    {
        if (android.os.Build.VERSION.SDK_INT <= 22) { // Lollipop
            // Before API 23, there was no permission required (or even available)
            return true;
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) { // Oreo
            // Permission to paint over status bar never granted to ordinary processes
            return false;
        }
        // Makes sense for only M and N
        return Settings.canDrawOverlays(context);
    }

    @TargetApi(23)
    static public void triggerOverlayPermissionsIfNecessary(Activity activity)
    {
        if (!canDrawOverlays(activity.getApplicationContext())) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, PERMISSION_REQUEST_FOR_MANAGE_OVERLAYS);
        }
    }

    // This callback chains from Settings Activity
    @SuppressWarnings("unused")
    public void onActivityResult(
            int code, int resultCode, Intent data) {
        switch (code) {
        case PERMISSION_REQUEST_FOR_MANAGE_OVERLAYS:
            Preconditions.checkState(android.os.Build.VERSION.SDK_INT >= 23); // Marshmallow

            if (!canDrawOverlays(activity.getApplicationContext())) {
                analyticsTracker.onPermissionRationaleShown("manageOverlays");
                AlertDialog.Builder dialogBuilder = PermissionUtils.permissionRationaleDialogBuilder(
                        activity, R.string.permission_rationale_simple_kiosk_overlay);
                dialogBuilder.setPositiveButton(
                        R.string.permission_rationale_try_again,
                        (dI, i) -> triggerOverlayPermissionsIfNecessary(activity));
                dialogBuilder.setNeutralButton(
                        R.string.permission_rationale_ignore,
                        (dI, i) -> {
                            // Do nothing, the StatusBarCollapser in MainActivity will take over
                        });
                /*
                dialogBuilder.setNegativeButton(
                        R.string.permission_rationale_exit,
                        (dI, i) -> forceExit(activity));
                */
                dialogBuilder.create().show();
            }
            break;
        }
    }


    private void setUiFlagsAndLockTask() {
        int visibilitySetting = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (!keepNavigation) {
            visibilitySetting |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                              |  View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        }

        activity.getWindow().getDecorView().setSystemUiVisibility(visibilitySetting);
        if (globalSettings.isAnyKioskModeEnabled())
            setNavigationVisibility(false);

        if (globalSettings.isFullKioskModeEnabled())
            lockTask(true);
    }

    private void setNavigationVisibility(boolean show) {
        if (Build.VERSION.SDK_INT < 19 || keepNavigation)
            return;

        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        View decorView = activity.getWindow().getDecorView();
        int visibilitySetting = decorView.getSystemUiVisibility();
        if (show)
            visibilitySetting &= ~flags;
        else
            visibilitySetting |= flags;

        decorView.setSystemUiVisibility(visibilitySetting);
    }

    private boolean isLockTaskEnabled() {
        return activity.getSharedPreferences(SCREEN_LOCK_PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_SCREEN_LOCK_ENABLED, false);
    }

    private void lockTask(boolean isLocked) {
        activity.getSharedPreferences(SCREEN_LOCK_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_SCREEN_LOCK_ENABLED, isLocked).apply();
        if (isLocked)
            API21.startLockTask(activity);
        else
            API21.stopLockTask(activity);
    }
    @TargetApi(21)
    private static class API21 {
        static void startLockTask(Activity activity) {
            activity.startLockTask();
        }

        static void stopLockTask(Activity activity) {
            activity.stopLockTask();
        }
    }

    // We can't fully disable the status bar, but we can make it harmless.
    // (After Andreas Schrade's article on Kiosks)
    private static CustomViewGroup suppressStatusView;

    public void controlStatusBarExpansion(Context context, boolean enable) {

        if (!canDrawOverlays(context)) {
            // We don't have permission (see the function above for the rules)
            // For API levels at or above 26 (Oreo) there's code in MainActivity that
            // does the best it can do achieve the same goal.
            return;
        }

        if (enable) {
            if (suppressStatusView != null) {
                // we already did the below
                return;
            }

            WindowManager manager = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE));
            WindowManager.LayoutParams localLayoutParams = new WindowManager.LayoutParams();
            //noinspection deprecation
            localLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
            localLayoutParams.gravity = Gravity.TOP;
            localLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

            localLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;

            int resId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            int result = 60; // default in px if we can't get it
            if (resId > 0) {
                result = context.getResources().getDimensionPixelSize(resId);
            }

            localLayoutParams.height = result;
            localLayoutParams.format = PixelFormat.TRANSPARENT;  // this is optional - without it
                                                                   // the bar area is just black.

            suppressStatusView = new CustomViewGroup(context);
            try {
                manager.addView(suppressStatusView, localLayoutParams);
            }
            catch (Exception e) {
                // if for some reason we lose the permission to do this, just go on.
                suppressStatusView = null;
            }
        }
        else {
            if (suppressStatusView != null) {
                WindowManager manager = ((WindowManager) context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE));
                manager.removeView(suppressStatusView);
                suppressStatusView = null;
            }
        }
    }

    private static class CustomViewGroup extends ViewGroup {

        public CustomViewGroup(Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            // Intercepted touch!
            return true;
        }
    }

    static public void forceExit(@NonNull Activity activity)
    {
        if (Build.VERSION.SDK_INT >= 21) { // Lollipop
            // If it's already pinned, un-pin it.
            activity.stopLockTask();
        }
        // First bring up "the usual android" screen. Then exit so we start a new process next
        // time, rather than resuming.  (The recents window may well show the settings
        // screen we just left, but it's just a snapshot.)
        activity.moveTaskToBack(true);
        System.exit(0);
    }
}
