/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2019 Donn S. Terry
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

package com.donnKey.aesopPlayer.analytics;

import android.content.Context;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class CrashWrapper
{
    // Be sure to follow this pattern for any new methods you want to use from Crashlytics.

    private static boolean enabled = false;

    private CrashWrapper() {
        // not constructable
    }

    public static void start(Context applicationContext,  boolean e) {
        enabled = e;
        // Caution here: this is apparently a device-global, persistent setting.
        // In case anything went wrong, just be sure we know the state.
        FirebaseAnalytics.getInstance(applicationContext).setAnalyticsCollectionEnabled(enabled);
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled);
    }

    public static void recordException(Throwable t) {
        if (enabled) {
            FirebaseCrashlytics.getInstance().recordException(t);
        }
    }

    public static void log(String s) {
        if (enabled) {
            FirebaseCrashlytics.getInstance().log(s);
        }
    }

    public static void log(String tag, String s) {
        if (enabled) {
            FirebaseCrashlytics.getInstance().log("E/" + tag + s);
        }
    }

    public static void crash() {
        if (enabled) {
            throw new RuntimeException("Test Crash");
        }
        else {
            throw new RuntimeException("Unreported test crash");
        }
    }
}
