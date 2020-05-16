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
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.donnKey.aesopPlayer.BuildConfig;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;

//import org.apache.commons.lang3.StringUtils; // for dumpTree below

import java.lang.ref.WeakReference;
import java.util.List;
public class AesopAccessibility extends AccessibilityService {
// This has two effects:
// 1) (Primary): When using Application Pinning for Kiosk Mode, on those implementations that
//    generate a "Got It" popup, it clicks that button to immediately proceed
//    when returning to the book list screen.
// 2) (Secondary): It's very presence (and being enabled) makes Simple Kiosk mode work on
//    Android 10/Q (and according to the documentation, R as well).

    private static final String TAG = "AesopAccessibility";

    private static boolean accessibilityConnected = false;
    private static WeakReference<AesopAccessibility> self;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        accessibilityConnected = true;
        self = new WeakReference<>(this);

        activateCheck();

        /* These are usually set in accessibility_service.xml
        AccessibilityServiceInfo accessibilityServiceInfo = getServiceInfo();
        accessibilityServiceInfo.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        accessibilityServiceInfo.packageNames = new String[] {"com.android.systemui",BulidConfig.APPLICATION_ID}; // dynamic
        accessibilityServiceInfo.feedbackType = AccessibilityServiceInfo.FEEDBACK_VISUAL;
        accessibilityServiceInfo.flags = AccessibilityServiceInfo.DEFAULT | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        accessibilityServiceInfo.notificationTimeout = 100;
        setServiceInfo(accessibilityServiceInfo);
        */
    }

    @SuppressLint("SwitchIntDef")
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (Build.VERSION.SDK_INT < 28) {
            // Unused, since app pinning AND the "Got It" message aren't present.
            return;
        }
        // Pie and above
        if (event.getPackageName() == null) {
            return;
        }
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
            {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (!windows.isEmpty()) {

                    // We assume that the top (0th) screen is the Got It overlay.
                    AccessibilityNodeInfo root = windows.get(0).getRoot();

                    // We can't use findAccessibilityNodeInfosByViewId() here. It always returns
                    // null when trying to get to the "Got it" overlay window.
                    // The findAndTap() works just fine. I think (from code-reading)
                    // that there's some sort of security check (that obviously isn't worth much)
                    // causing the official implementation to fail.
                    if (root == null) {
                        return;
                    }
                    // Sometimes we get here after the Got It overlay is gone.
                    if (!root.getPackageName().equals("com.android.systemui")) {
                        return;
                    }
                    String rootName = root.getViewIdResourceName();
                    if (rootName != null) {
                        // We know the one we want has a null root id name, so we can ignore any others
                        return;
                    }
                    if (!findAndTap(root, "com.android.systemui:id/screen_pinning_ok_button")) {
                        CrashWrapper.log(TAG + ": Button press failed.");
                    }
                }
                break;
            }
        default:
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
            return;
        }
        AccessibilityServiceInfo accessibilityServiceInfo = self.get().getServiceInfo();
        accessibilityServiceInfo.packageNames = new String[] {"com.android.systemui", BuildConfig.APPLICATION_ID};
        self.get().setServiceInfo(accessibilityServiceInfo);
    }

    static /* public if ever needed */ private void deActivateCheck() {
        assert self != null;
        AccessibilityServiceInfo accessibilityServiceInfo = self.get().getServiceInfo();
        // The system just ignores this if the string array is empty, but an empty string works.
        accessibilityServiceInfo.packageNames = new String[] {""};
        self.get().setServiceInfo(accessibilityServiceInfo);
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
