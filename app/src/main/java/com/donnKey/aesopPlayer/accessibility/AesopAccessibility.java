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

import org.apache.commons.lang3.StringUtils;
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
    private static final String TAG = "Access";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        accessibilityConnected = true;
        self = new WeakReference<>(this);

        // These are usually set in accessibility_service.xml, but we need dynamic values for a few.
        AccessibilityServiceInfo accessibilityServiceInfo = getServiceInfo();
        accessibilityServiceInfo.packageNames = new String[] {"com.android.systemui",BuildConfig.APPLICATION_ID};
        accessibilityServiceInfo.eventTypes = eventTypes;

        // Be careful with timeout... too large a value causes it to miss the LG special case.
        // Default appears fine.
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

        String targetPackageName = "com.android.systemui";

        // By targeting the right package name, we eliminate almost all "ordinary" apps,
        // which means were much less likely to press an unintended button below.
        if (Build.VERSION.SDK_INT <= 21) {
            // API 21 was different.
            targetPackageName = "android";
        }

        // See the comment about a Toast in KioskModeSwitcher#changeActiveKioskMode.
        // It's required.
        switch (event.getEventType()) {
            // We need CONTENT for redundancy, particularly on startup.
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
            {
                // The App pinning message doesn't come from Aesop, so there's no point
                // in filtering from that. It's from the system.
                // In API 21 we never see the event that puts up the screen, just the
                // Toast we mentioned above.
                // API22 and above MIGHT see the event.
                List<AccessibilityWindowInfo> windows = getWindows();
                if (!windows.isEmpty()) {

                    // Presumably (according to the docs) the top (0th) screen is the Got It overlay, but...
                    // The LG special case is definitely NOT the 0th entry.
                    scanWindows:
                    for (AccessibilityWindowInfo window : windows) {
                        AccessibilityNodeInfo root = window.getRoot();

                        // We can't use findAccessibilityNodeInfosByViewId() here. It always returns
                        // null when trying to get to the "Got it" overlay window.
                        // Probably because the overlay window is from the system.
                        if (root == null) {
                            continue scanWindows;
                        }
                        // Sometimes we get here after the Got It overlay is gone.
                        if (!root.getPackageName().equals(targetPackageName)) {
                            continue scanWindows;
                        }
                        String rootName = root.getViewIdResourceName();
                        if (rootName != null) {
                            // We know the one we want has a null root id name, so we can ignore any others
                            continue scanWindows;
                        }
                        //noinspection StatementWithEmptyBody
                        if (findAndTap(root)) {
                            return;
                        }
                        else {
                            /* Ignore the failure... it's benign as long as we finally do it.
                               Sometimes the event is redundant and it fails because the job is done

                            CrashWrapper.log(TAG + ": Button press failed.");
                             */
                        }
                    }
                }
                break;
            }

            default:
                break;
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

    // This function and the next try to find the "OK"/"Got It"/<whatever> button
    // that we don't definitely know either the node-name or text content. (Three
    // variants known, and it seems likely there might be more "customization".)
    // This should work unless things get really weird.

    // Historically it's been either "com.android.systemui:id/screen_pinning_ok_button"
    // or "android:id/button1".  With content of "OK" or "Got It".
    // We first find a text field that contains "pin" (we can't  rely on "pinning").
    // Look for siblings or children (siblings in standard Android,
    // children seen on LG G6) that are buttons, and pick the right-most and click it.

    private boolean findAndTap(AccessibilityNodeInfo nodeInfo) {
        // This isn't used until API21 (when App Pinning is possible).
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            if (nodeInfo == null) {
                return false;
            }

            // If this node contains an entry with text containing "pin", try to find a
            // button pair below it, and click the rightmost one.
            for (int i = 0; i < nodeInfo.getChildCount(); ++i) {
                CharSequence cs = nodeInfo.getChild(i).getText();
                if (cs != null) {
                    String t = cs.toString();
                    if (StringUtils.startsWithIgnoreCase(t, "pin")
                            || StringUtils.containsIgnoreCase(t, " pin")) {
                        // Note, start with *this* node.
                        return findButtonsAndTap(nodeInfo);
                    }
                }
            }

            // failing that, recursively check the children for the same
            for (int i = 0; i < nodeInfo.getChildCount(); ++i) {
                if (findAndTap(nodeInfo.getChild(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean findButtonsAndTap(AccessibilityNodeInfo nodeInfo) {
        // Find a row with one or more buttons, and click the rightmost.
        // This isn't used until API21 (when App Pinning is possible).
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            if (nodeInfo == null) {
                return false;
            }

            AccessibilityNodeInfo button = null;
            int button_x = -1;
            for (int i = 0; i < nodeInfo.getChildCount(); ++i) {
                AccessibilityNodeInfo child = nodeInfo.getChild(i);
                android.graphics.Rect rect = new android.graphics.Rect();
                child.getBoundsInScreen(rect);
                if (StringUtils.containsIgnoreCase(child.getClassName().toString(), "button")) {
                    if (button == null || rect.left > button_x) {
                        button = child;
                        button_x = rect.left;
                    }
                }
            }

            if (button != null) {
                deActivateCheck();
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }

            for (int i = 0; i < nodeInfo.getChildCount(); ++i) {
                if (findButtonsAndTap(nodeInfo.getChild(i))) {
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
                Log.w("AESOP " + TAG, StringUtils.repeat(' ', 2*depth) + "dumpTree gets NULL");
                return;
            }

            //Log.w("AESOP " + TAG, StringUtils.repeat(' ', 2*depth) + nodeInfo.getViewIdResourceName());
            Log.w("AESOP " + TAG, StringUtils.repeat(' ', 2*depth) + nodeInfo);

            String resourceName = nodeInfo.getViewIdResourceName();
            if (resourceName != null && resourceName.equals("com.android.systemui:id/screen_pinning_ok_button")) {
                Log.w("AESOP " + TAG, "******************** match");
                //nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

            for (int i = 0; i < nodeInfo.getChildCount(); ++i) {
                dumpTree(nodeInfo.getChild(i), depth + 1);
            }
        }
    }
     */
}
