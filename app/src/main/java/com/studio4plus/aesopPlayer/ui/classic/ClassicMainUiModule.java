package com.studio4plus.aesopPlayer.ui.classic;

import androidx.appcompat.app.AppCompatActivity;

import com.studio4plus.aesopPlayer.ui.MainActivity;
import com.studio4plus.aesopPlayer.ui.MainUi;
import com.studio4plus.aesopPlayer.ui.ActivityScope;
import com.studio4plus.aesopPlayer.ui.SpeakerProvider;

import dagger.Module;
import dagger.Provides;

@Module
public class ClassicMainUiModule {
    private final MainActivity activity;

    public ClassicMainUiModule(MainActivity activity) {
        this.activity = activity;
    }

    @Provides @ActivityScope
    MainUi mainUi(AppCompatActivity activity) {
        return new ClassicMainUi(activity);
    }

    @Provides @ActivityScope
    SpeakerProvider speakProvider() {
        return activity;
    }
}
