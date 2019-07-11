package com.studio4plus.aesopPlayer.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.studio4plus.aesopPlayer.GlobalSettings;
import com.studio4plus.aesopPlayer.analytics.AnalyticsTracker;

import dagger.Module;
import dagger.Provides;
import de.greenrobot.event.EventBus;

@Module
public class ActivityModule {
    private final AppCompatActivity activity;

    public ActivityModule(@NonNull AppCompatActivity activity) {
        this.activity = activity;
    }

    @Provides @ActivityScope
    AppCompatActivity activity() {
        return activity;
    }

    @Provides @ActivityScope
    KioskModeHandler provideKioskModeHandler(
            AppCompatActivity activity, GlobalSettings settings, AnalyticsTracker analyticsTracker, EventBus eventBus) {
        return new KioskModeHandler(activity, settings, analyticsTracker, eventBus);
    }
}
