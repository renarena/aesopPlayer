package com.studio4plus.homerplayer.ui;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.studio4plus.homerplayer.GlobalSettings;

public class OrientationActivityDelegate
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final AppCompatActivity activity;
    private final GlobalSettings globalSettings;

    public OrientationActivityDelegate(@NonNull AppCompatActivity activity, GlobalSettings globalSettings) {
        this.activity = activity;
        this.globalSettings = globalSettings;
    }

    public void onStart() {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(activity);
        updateOrientation();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    public void onStop() {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(activity);
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateOrientation();
    }

    private void updateOrientation() {
        activity.setRequestedOrientation(globalSettings.getScreenOrientation());
    }
}
