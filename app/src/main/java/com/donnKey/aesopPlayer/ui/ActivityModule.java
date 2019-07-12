package com.donnKey.aesopPlayer.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.analytics.AnalyticsTracker;

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
