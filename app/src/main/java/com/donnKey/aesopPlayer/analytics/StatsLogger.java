/**
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
package com.donnKey.aesopPlayer.analytics;

import android.content.Context;
import androidx.annotation.NonNull;
/*
import android.content.res.AssetManager;
import androidx.annotation.Nullable;

import com.flurry.android.FlurryAgent;
import com.donnKey.aesopPlayer.util.VersionUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
 */
import java.util.Map;

// Flurry is a product for gathering customer demographic data.
// Currently Flurry is disabled rather than deal with the legal issues involved.
// All functions are no-ops. No Flurry account exists.
// There is no assets/api_keys/flurry in GIT; one would need to be created after siging up with Flurry
// App build.gradle would also need to be uncommented
class StatsLogger {

    private static final String FLURRY_API_KEY_ASSET = "api_keys/flurry";
    private boolean isFlurryEnabled;

    StatsLogger(@NonNull Context context) {
        /*
        String flurryKey = getFlurryKey(context.getAssets());
        if (flurryKey != null && VersionUtil.isOfficialVersion()) {
            new FlurryAgent.Builder()
                    .withLogEnabled(true)
                    .build(context, flurryKey);
            isFlurryEnabled = true;
        }
         */
    }

    void logEvent(@NonNull String eventName) {
        /*
        if (isFlurryEnabled) {
            FlurryAgent.logEvent(eventName);
        }
         */
    }

    void logEvent(@NonNull String eventName, @NonNull Map<String, String> eventData) {
        /*
        if (isFlurryEnabled) {
            FlurryAgent.logEvent(eventName, eventData);
        }
         */
    }

    @SuppressWarnings("SameParameterValue")
    void endTimedEvent(@NonNull String eventName) {
        /*
        if (isFlurryEnabled) {
            FlurryAgent.endTimedEvent(eventName);
        }
         */
    }

    /*
    @Nullable
    private static String getFlurryKey(AssetManager assets) {
        try {
            InputStream inputStream = assets.open(FLURRY_API_KEY_ASSET);
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String key = reader.readLine();
                inputStream.close();
                return key;
            } catch(IOException e) {
                inputStream.close();
                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }
     */
}
