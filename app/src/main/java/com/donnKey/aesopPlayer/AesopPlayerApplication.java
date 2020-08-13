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

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;

import com.donnKey.aesopPlayer.analytics.AnalyticsTracker;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.ui.HomeActivity;
import com.donnKey.aesopPlayer.service.NotificationUtil;
import com.donnKey.aesopPlayer.ui.Speaker;

import javax.inject.Inject;

public class AesopPlayerApplication extends androidx.multidex.MultiDexApplication {

    private static final String AUDIOBOOKS_DIRECTORY = "AudioBooks";
    //???????????? Fix me
    //public static final String WEBSITE_URL = "https://donnkey.github.io/aesopPlayer/";
    public static final String WEBSITE_URL = "http://donnteH02:4000/aesopPlayer/";
    // To use with a local server using http: rather than https: (and thus avoiding
    // having to fuss with certificates) use a URI like the below. You'll also need
    // to enable cleartext in network_security_config.xml.
    // For Jekyll, add --host 0.0.0.0 to the command line.
    // public static final String WEBSITE_URL = "http://my_local_server:4000/aesopPlayer/";

    private static final String DEMO_SAMPLES_URL = WEBSITE_URL + "samples/TheAesopAudiobookSamples.zip";

    private ApplicationComponent component;
    private MediaStoreUpdateObserver mediaStoreUpdateObserver;
    private static Context applicationContext;

    @Inject public GlobalSettings globalSettings;
    @Inject public AnalyticsTracker analyticsTracker;  // Force creation of the tracker early.

    @Override
    public void onCreate() {
        super.onCreate();

        applicationContext = getApplicationContext();

        component = DaggerApplicationComponent.builder()
                .applicationModule(new ApplicationModule(this, Uri.parse(DEMO_SAMPLES_URL)))
                .audioBookManagerModule(new AudioBookManagerModule(AUDIOBOOKS_DIRECTORY))
                .build();
        component.inject(this);

        // Caution here: sets an apparently a device-global, persistent setting.
        CrashWrapper.start(applicationContext, globalSettings.getAnalytics());

        mediaStoreUpdateObserver = new MediaStoreUpdateObserver(new Handler(getMainLooper()));
        getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaStoreUpdateObserver);

        HomeActivity.setEnabled(this, globalSettings.isAnyKioskModeEnabled());

        if (Build.VERSION.SDK_INT >= 26)
            NotificationUtil.API26.registerPlaybackServiceChannel(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        getContentResolver().unregisterContentObserver(mediaStoreUpdateObserver);
        mediaStoreUpdateObserver = null;
        Speaker.shutdown();
    }

    public static ApplicationComponent getComponent(Context context) {
        return ((AesopPlayerApplication) context.getApplicationContext()).component;
    }

    public static Context getAppContext() {
        return applicationContext;
    }
}
