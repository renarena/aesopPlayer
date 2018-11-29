package com.studio4plus.homerplayer.ui;

import android.support.v7.app.AppCompatActivity;

import com.studio4plus.homerplayer.ApplicationComponent;

import dagger.Component;

@ActivityScope
@Component(dependencies = ApplicationComponent.class, modules = ActivityModule.class)
interface ActivityComponent {
    @SuppressWarnings("unused")
    AppCompatActivity activity();
    void inject(SettingsActivity settingsActivity);
}
