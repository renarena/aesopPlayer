package com.studio4plus.homerplayer.ui;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.events.KioskModeChanged;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

@ActivityScope
public class KioskModeHandler {

    private static final String SCREEN_LOCK_PREFS = "ScreenLocker";
    private static final String PREF_SCREEN_LOCK_ENABLED = "screen_lock_enabled";

    private final Activity activity;
    private final GlobalSettings globalSettings;
    private final EventBus eventBus;
    private boolean keepNavigation = false;

    @Inject
    KioskModeHandler(Activity activity, GlobalSettings settings, EventBus eventBus) {
        this.activity = activity;
        this.globalSettings = settings;
        this.eventBus = eventBus;
    }

    public void setKeepNavigation(Boolean keepNavigation) {
        this.keepNavigation = keepNavigation;
    }

    public void onActivityStart() {
        if (!globalSettings.isFullKioskModeEnabled() && isLockTaskEnabled())
            lockTask(false);
        setUiFlagsAndLockTask();
        controlStatusBarExpansion(activity.getApplicationContext(),
                globalSettings.isSimpleKioskModeEnabled());
        eventBus.register(this);
    }

    public void onActivityStop() {
        controlStatusBarExpansion(activity.getApplicationContext(),
                globalSettings.isSimpleKioskModeEnabled());
        eventBus.unregister(this);
    }

    public void onFocusGained() {
        setUiFlagsAndLockTask();
    }

    @SuppressWarnings("unused")
    public void onEvent(KioskModeChanged event) {
        if (event.type == KioskModeChanged.Type.FULL) {
            lockTask(event.isEnabled);
        }
        else {
            getSimpleKioskPermissionsAsNeeded();
            controlStatusBarExpansion(activity.getApplicationContext(), event.isEnabled);
        }
        setNavigationVisibility(!event.isEnabled);
    }

    private void getSimpleKioskPermissionsAsNeeded() {
        PermissionUtils.checkAndRequestPermission(
                activity,
                new String[]{Manifest.permission.REORDER_TASKS,
                             Manifest.permission.SYSTEM_ALERT_WINDOW},
                UiControllerMain.PERMISSION_REQUEST_FOR_SIMPLE_KIOSK);
    }

    private void setUiFlagsAndLockTask() {
        int visibilitySetting = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (!keepNavigation) {
            visibilitySetting |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
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

    static private void controlStatusBarExpansion(Context context, boolean enable) {
        if (Build.VERSION.SDK_INT > 23) {
            // This requires a permission on Marshmallow and up. A different project but
            // leave as is for older devices (that don't have Pinning/Kiosk.)
            return;
        }

        if (enable) {
            if (suppressStatusView != null) {
                // we already did the below
                return;
            }

            // TYPE_SYSTEM_ERROR below does what we need, but is deprecated. The suggested
            // TYPE_APPLICATION_OVERLAY needs some sort of permissions setup, and may not
            // work anyway (according to the docs). So for now we'll live with the warning.
            // (Our target being older machines, that's not an issue as much.)
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
            manager.addView(suppressStatusView, localLayoutParams);
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
}
