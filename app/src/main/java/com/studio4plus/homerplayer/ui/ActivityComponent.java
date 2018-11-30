package com.studio4plus.homerplayer.ui;

import android.app.Activity;

import com.studio4plus.homerplayer.ApplicationComponent;

import dagger.Component;

@ActivityScope
@Component(dependencies = ApplicationComponent.class, modules = ActivityModule.class)
interface ActivityComponent {
    @SuppressWarnings("unused")
    Activity activity();
    void inject(SettingsActivity settingsActivity);
}
