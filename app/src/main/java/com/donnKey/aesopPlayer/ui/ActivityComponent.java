package com.donnKey.aesopPlayer.ui;

import androidx.appcompat.app.AppCompatActivity;

import com.donnKey.aesopPlayer.ApplicationComponent;
import com.donnKey.aesopPlayer.ui.settings.SettingsActivity;

import dagger.Component;

@ActivityScope
@Component(dependencies = ApplicationComponent.class, modules = ActivityModule.class)
public interface ActivityComponent {
    @SuppressWarnings("unused")
    AppCompatActivity activity();
    void inject(SettingsActivity settingsActivity);
}
