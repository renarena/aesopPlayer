/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Donn S. Terry
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
package com.donnKey.aesopPlayer.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.donnKey.aesopPlayer.BuildConfig;

//import org.apache.commons.lang3.StringUtils; // for dumpTree below

import java.lang.ref.WeakReference;
import java.util.List;
public class AesopAccessibility extends AccessibilityService {
// This has two effects:
// 1) (Primary): When using Application Pinning for Kiosk Mode, on those implementations that
//    generate a "Got It" popup, it clicks that button to immediately proceed
//    when returning to the book list screen.
//    NOTE:
// --------------------------------------------------------------------------
//    L.0 thru O: In the emulator: screen Pinning works, but does NOT put up the "Got it"
//    notification unless the emulator is using Google APIs!
// --------------------------------------------------------------------------
// 2) (Secondary): It's very presence (and being enabled) makes Simple Kiosk mode work on
//    Android 10/Q (and according to the documentation, R as well).

    private static boolean accessibilityConnected = false;
    private static WeakReference<AesopAccessibility> self;
    private static final int eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
    private static boolean lateActivate = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        accessibilityConnected = true;
        self = new WeakReference<>(this);

        // These are usually set in accessibility_service.xml, but we need dynamic values for a few.
        AccessibilityServiceInfo accessibilityServiceInfo = getServiceInfo();
        accessibilityServiceInfo.packageNames = new String[] {"com.android.systemui",BuildConfig.APPLICATION_ID};
        accessibilityServiceInfo.eventTypes = eventTypes;

        // In general, 100 works on the emulator, but 10 doesn't.
        // 100 doesn't work reliably on L.1 when starting a "killed" (removed from Overview) process.
        // Allow for really slow machines.
        // (The event likely happened BEFORE startLockTask, so we carefully preserve
        // that leftover to make this work, since we can't force an event.)
        accessibilityServiceInfo.notificationTimeout = 500;

        setServiceInfo(accessibilityServiceInfo);

        // Generally, start deactivated; we'll activate when needed.
        // Occasionally (often enough to matter), this service start occurs AFTER
        // MainActivity#onResume, which causes app pinning to happen before we
        // get set up, so detect that and handle it here.
        if (lateActivate) {
            activateCheck();
        }
        else {
            deActivateCheck();
        }
    }


    @SuppressWarnings("UnnecessaryLabelOnContinueStatement")
    @SuppressLint("SwitchIntDef")
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (Build.VERSION.SDK_INT < 21) {
            // Unused, since app pinning isn't present
            return;
        }
        // L and above
        if (event.getPackageName() == null) {
            return;
        }

        if (isAppLocked()) {
            // Somehow the goal was achieved... we're done
            // (Non-Google API emulators < P don't create the popup, just go straight to locked.)
            deActivateCheck();
            return;
        }

        if (Build.VERSION.SDK_INT > 21) {
            // API 22 and up
            switch (event.getEventType()) {
                // We need CONTENT for redundancy, particularly on startup.
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                {
                    // Try to avoid any unnecessary tree walks.

                    /* These filters sometimes cause us to miss something. Since as soon as
                       an event (there could be several) that has what we need, we'll turn
                       off sensitivity and not waste CPU. (A few events get queued up so the
                       one that works may not be the last, but it's near the last.)
                    if (!event.getPackageName().equals(BuildConfig.APPLICATION_ID)) {
                        return;
                    }
                    if (event.getText().size() <= 0) {
                        return;
                    }
                    if (!event.getText().get(0).equals("Aesop Player")
                        && !event.getText().get(0).equals("Screen is pinned")) {
                        return;
                    }
                     */
                    List<AccessibilityWindowInfo> windows = getWindows();
                    if (!windows.isEmpty()) {

                        // Presumably (according to the docs) the top (0th) screen is the Got It overlay, but...
                        scanWindows:
                        for (AccessibilityWindowInfo window : windows) {
                            AccessibilityNodeInfo root = window.getRoot();

                            // We can't use findAccessibilityNodeInfosByViewId() here. It always returns
                            // null when trying to get to the "Got it" overlay window.
                            // The findAndTap() works just fine. I think (from code-reading)
                            // that there's some sort of security check (that obviously isn't worth much)
                            // causing the official implementation to fail.
                            if (root == null) {
                                continue scanWindows;
                            }
                            // Sometimes we get here after the Got It overlay is gone.
                            if (!root.getPackageName().equals("com.android.systemui")) {
                                continue scanWindows;
                            }
                            String rootName = root.getViewIdResourceName();
                            if (rootName != null) {
                                // We know the one we want has a null root id name, so we can ignore any others
                                continue scanWindows;
                            }
                            //noinspection StatementWithEmptyBody
                            if (!findAndTap(root, "com.android.systemui:id/screen_pinning_ok_button")) {
                                /* Ignore the failure... it's benign as long as we finally do it.
                                   Sometimes the event is redundant and it fails because the job is done

                                CrashWrapper.log(TAG + ": Button press failed.");
                                 */
                            }
                            return;
                        }
                    }
                    break;
                }

                default:
                    break;
            }
        }
        else {
            // API 21 does it differently: different details
            switch (event.getEventType()) {
                // Click needed for from settings, CONTENT for startup.
                case AccessibilityEvent.TYPE_VIEW_CLICKED:
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                {
                    // Try to avoid any unnecessary tree walks.

                    List<AccessibilityWindowInfo> windows = getWindows();
                    if (!windows.isEmpty()) {

                        // Presumably (according to the docs) the top (0th) screen is the Got It overlay, but...
                        scanWindows:
                        for (AccessibilityWindowInfo window : windows) {
                            AccessibilityNodeInfo root = window.getRoot();

                            // We can't use findAccessibilityNodeInfosByViewId() here. It always returns
                            // null when trying to get to the "Got it" overlay window.
                            // The findAndTap() works just fine. I think (from code-reading)
                            // that there's some sort of security check (that obviously isn't worth much)
                            // causing the official implementation to fail.
                            if (root == null) {
                                continue scanWindows;
                            }
                            // Sometimes we get here after the Got It overlay is gone.
                            if (!root.getPackageName().equals("android")) {
                                continue scanWindows;
                            }
                            String rootName = root.getViewIdResourceName();
                            if (rootName != null) {
                                // We know the one we want has a null root id name, so we can ignore any others
                                continue scanWindows;
                            }
                            //noinspection StatementWithEmptyBody
                            if (!findAndTap(root, "android:id/button1")) {
                                /* Ignore the failure... it's benign as long as we finally do it.
                                   Sometimes the event is redundant and it fails because the job is done

                                CrashWrapper.log(TAG + ": Button press failed (API21).");
                                 */
                            }
                            return;
                        }
                    }
                    break;
                }

                default:
                    break;
            }
        }
    }

    @Override
    public void onInterrupt() {
        // Required stub.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        accessibilityConnected = false;
    }

    public static boolean isAccessibilityConnected() {
        return accessibilityConnected;
    }

    private boolean findAndTap(AccessibilityNodeInfo nodeInfo, String idName) {
        if (nodeInfo == null) {
            return false;
        }
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            // This isn't used until API21 (when App Pinning is possible).

            String resourceName = nodeInfo.getViewIdResourceName();
            if (resourceName != null && resourceName.equals(idName)) {
                deActivateCheck();
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }

            for (int i = 0; i < nodeInfo.getChildCount(); ++i) {
                if (findAndTap(nodeInfo.getChild(i), idName)) {
                    return true;
                }
            }
        }

        return false;
    }

    // Under rare circumstances, but often enough to matter, the accessibility event stream
    // somehow misses that the "Got It" message is displayed (the systemui event is either
    // incomplete or missed, I'm not sure which). Additional events will come from aesopPlayer
    // with the same information, but that's a lot of pointless callbacks. So instead enable
    // the check when pinning is started, and disable it when it's finally found and cleared.
    // FWIW, the event stream "coasts" for a little while after deactivation.

    // A nice side-effect is that this won't potentially interfere with other uses of App
    // Pinning because it's not active except when actually needed.
    static public void activateCheck() {
        // Sometimes locking runs before this service starts, so we ignore the call then,
        // and rely on the call from the constructor during startup.
        if (self == null || self.get() == null) {
            lateActivate = true;
            return;
        }
        AccessibilityServiceInfo accessibilityServiceInfo = self.get().getServiceInfo();
        accessibilityServiceInfo.eventTypes = eventTypes;
        self.get().setServiceInfo(accessibilityServiceInfo);
    }

    static /* public if ever needed */ private void deActivateCheck() {
        // Disable so that blinking doesn't keep bringing us back to the onEvent.
        assert self != null;
        AccessibilityServiceInfo accessibilityServiceInfo = self.get().getServiceInfo();
        // Disable by desensitizing event types.
        accessibilityServiceInfo.eventTypes = 0;
        self.get().setServiceInfo(accessibilityServiceInfo);
    }

    private boolean isAppLocked() {
        ActivityManager activityManager =
                (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        assert(activityManager!=null);

        if (Build.VERSION.SDK_INT >= 23) {
            return activityManager.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
        }
        else if (Build.VERSION.SDK_INT >= 21)  {
            //noinspection deprecation
            return activityManager.isInLockTaskMode();
        }

        return false;
    }

    /*  If things change, we might need this.
    public static void dumpTree(AccessibilityNodeInfo nodeInfo, int depth) {
        if (android.os.Build.VERSION.SDK_INT >= 21) {

            if (nodeInfo == null) {
                Log.w("AESOP", "dumpTree gets NULL");
                return;
            }

            //Log.w("AESOP", StringUtils.repeat(' ', 2*depth) + nodeInfo.getViewIdResourceName());
            Log.w("AESOP", StringUtils.repeat(' ', 2*depth) + nodeInfo);

            String resourceName = nodeInfo.getViewIdResourceName();
            if (resourceName != null && resourceName.equals("com.android.systemui:id/screen_pinning_ok_button")) {
                Log.w("AESOP", "******************** match");
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

            for (int i = 0; i < nodeInfo.getChildCount(); ++i) {
                dumpTree(nodeInfo.getChild(i), depth + 1);
            }
        }
    }
     */
}
