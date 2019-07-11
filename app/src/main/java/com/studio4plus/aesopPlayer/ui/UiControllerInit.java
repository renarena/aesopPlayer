package com.studio4plus.aesopPlayer.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.studio4plus.aesopPlayer.analytics.AnalyticsTracker;
import javax.inject.Inject;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class UiControllerInit {

    public static class Factory {
        private final @NonNull AppCompatActivity activity;
        private final @NonNull AnalyticsTracker analyticsTracker;

        @Inject
        public Factory(@NonNull AppCompatActivity activity,
                       @NonNull AnalyticsTracker analyticsTracker) {
            this.activity = activity;
            this.analyticsTracker = analyticsTracker;
        }

        public UiControllerInit create(@NonNull InitUi ui) {
            return new UiControllerInit(activity, ui, analyticsTracker);
        }
    }

    private final @NonNull AppCompatActivity activity;
    private final @NonNull InitUi ui;
    private final @NonNull AnalyticsTracker analyticsTracker;

    private UiControllerInit(@NonNull AppCompatActivity activity,
                                @NonNull InitUi ui,
                                @NonNull AnalyticsTracker analyticsTracker) {
        this.activity = activity;
        this.ui = ui;
        this.analyticsTracker = analyticsTracker;

        ui.initWithController(this);
    }
}
