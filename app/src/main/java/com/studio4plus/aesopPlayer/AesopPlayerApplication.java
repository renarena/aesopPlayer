package com.studio4plus.aesopPlayer;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.studio4plus.aesopPlayer.analytics.AnalyticsTracker;
import com.studio4plus.aesopPlayer.ui.HomeActivity;
import com.studio4plus.aesopPlayer.service.NotificationUtil;
import com.studio4plus.aesopPlayer.ui.Speaker;

import javax.inject.Inject;

import io.fabric.sdk.android.Fabric;

public class AesopPlayerApplication extends Application {

    private static final String AUDIOBOOKS_DIRECTORY = "AudioBooks";
    // For the moment we continue to use the Homer samples.
    private static final String DEMO_SAMPLES_URL =
            "https://homer-player.firebaseapp.com/samples.zip";

    private ApplicationComponent component;
    private MediaStoreUpdateObserver mediaStoreUpdateObserver;
    private static Context applicationContext;

    @SuppressWarnings("WeakerAccess")
    @Inject public GlobalSettings globalSettings;
    @Inject public AnalyticsTracker analyticsTracker;  // Force creation of the tracker early.

    @Override
    public void onCreate() {
        super.onCreate();

        applicationContext = getApplicationContext();

        CrashlyticsCore core = new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build();
        Fabric.with(this, new Crashlytics.Builder().core(core).build());

        component = DaggerApplicationComponent.builder()
                .applicationModule(new ApplicationModule(this, Uri.parse(DEMO_SAMPLES_URL)))
                .audioBookManagerModule(new AudioBookManagerModule(AUDIOBOOKS_DIRECTORY))
                .build();
        component.inject(this);

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
