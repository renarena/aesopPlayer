/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2019 Donn S. Terry
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
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.donnKey.aesopPlayer.events.DeviceAdminChangeEvent;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

public class AesopPlayerDeviceAdmin extends DeviceAdminReceiver {
    @Inject
    public GlobalSettings globalSettings;

    public AesopPlayerDeviceAdmin () {
        AesopPlayerApplication.getComponent(AesopPlayerApplication.getAppContext()).inject(this);
    }

    // onEnabled and onDisabled are called when the privilege changes, even when
    // the app is NOT running. (It is run for a moment.) We want to be sure
    // that if the privilege changes, the mode gets reset to NONE, so the user
    // knows what to expect from locking. The app is sufficiently started that
    // we can get globalSettings.
    @Override
    public void onEnabled(Context context, Intent intent) {
        if (globalSettings != null) {
            if (globalSettings.getKioskMode() != GlobalSettings.SettingsKioskMode.SIMPLE) {
                globalSettings.setKioskModeNow(GlobalSettings.SettingsKioskMode.NONE);
            }
        }
        if (Build.VERSION.SDK_INT >= 21)
            API21.enableLockTask(context);
        EventBus.getDefault().post(new DeviceAdminChangeEvent(true));
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        if (globalSettings != null) {
            if (globalSettings.getKioskMode() != GlobalSettings.SettingsKioskMode.SIMPLE) {
                globalSettings.setKioskModeNow(GlobalSettings.SettingsKioskMode.NONE);
            }
        }
        EventBus.getDefault().post(new DeviceAdminChangeEvent(false));
    }

    public static boolean isDeviceOwner(Context context) {
        return Build.VERSION.SDK_INT >= 21 && API21.isDeviceOwner(context);
    }

    public static void clearDeviceOwner(Context context) {
        if (Build.VERSION.SDK_INT >= 21)
            API21.clearDeviceOwnerAndAdmin(context);
    }

    @TargetApi(21)
    private static class API21 {

        static boolean isDeviceOwner(Context context) {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            return dpm.isDeviceOwnerApp(context.getPackageName());
        }

        static void clearDeviceOwnerAndAdmin(Context context) {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            dpm.clearDeviceOwnerApp(context.getPackageName());
            ComponentName adminComponentName = new ComponentName(context, AesopPlayerDeviceAdmin.class);
            dpm.removeActiveAdmin(adminComponentName);
        }

        static void enableLockTask(Context context) {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponentName = new ComponentName(context, AesopPlayerDeviceAdmin.class);
            if (dpm.isAdminActive(adminComponentName) &&
                   dpm.isDeviceOwnerApp(context.getPackageName()))
                dpm.setLockTaskPackages(adminComponentName, new String[]{context.getPackageName()});
        }
    }
}
