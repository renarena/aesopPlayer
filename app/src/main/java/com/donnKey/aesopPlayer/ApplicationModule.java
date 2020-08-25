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

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import com.donnKey.aesopPlayer.analytics.AnalyticsTracker;
import com.donnKey.aesopPlayer.concurrency.BackgroundExecutor;
import com.donnKey.aesopPlayer.ui.SoundBank;

import java.util.Locale;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import org.greenrobot.eventbus.EventBus;

@Module
class ApplicationModule {

    private final Application application;
    private final Uri samplesDownloadUrl;

    public ApplicationModule(Application application, Uri samplesDownloadUrl) {
        this.application = application;
        this.samplesDownloadUrl = samplesDownloadUrl;
    }

    @Provides @ApplicationScope
    Context provideContext() {
        return application;
    }

    @Provides
    Resources provideResources(@NonNull Context context) {
        return context.getResources();
    }

    @Provides
    Locale provideCurrentLocale(@NonNull Resources resources) {
        return resources.getConfiguration().locale;
    }

    @Provides
    SharedPreferences provideSharedPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Provides @Singleton @Named("SAMPLES_DOWNLOAD_URL")
    Uri provideSamplesUrl() {
        return samplesDownloadUrl;
    }

    @Provides
    EventBus provideEventBus() {
        // TODO: provide the EventBus to all classes via Dagger and then switch to a private instance.
        return EventBus.getDefault();
    }

    @Provides @Singleton
    AnalyticsTracker provideAnalyticsTracker(
            Context context, GlobalSettings globalSettings, EventBus eventBus) {
        return new AnalyticsTracker(context, globalSettings, eventBus);
    }

    @Provides @Singleton
    SoundBank provideSoundBank(Resources resources) {
        return new SoundBank(resources);
    }

    @Provides @Singleton @Named("IO_EXECUTOR")
    BackgroundExecutor provideIoExecutor(@NonNull Context applicationContext) {
        HandlerThread ioThread = new HandlerThread("IO");
        ioThread.start();
        Handler ioHandler = new Handler(ioThread.getLooper());
        return new BackgroundExecutor(new Handler(applicationContext.getMainLooper()), ioHandler);
    }
}
