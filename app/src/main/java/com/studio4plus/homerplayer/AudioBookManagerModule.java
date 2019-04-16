package com.studio4plus.homerplayer;

import android.content.Context;

import com.studio4plus.homerplayer.model.Storage;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AudioBookManagerModule {

    public static String audioBooksDirectoryName;

    public AudioBookManagerModule(String audioBooksDirectoryNameParam) {
        audioBooksDirectoryName = audioBooksDirectoryNameParam;
    }

    @Provides @Named("AUDIOBOOKS_DIRECTORY")
    String provideAudioBooksDirectoryName() {
        return audioBooksDirectoryName;
    }

    @Provides @Singleton
    Storage provideStorage(Context context) {
        return new Storage(context);
    }
}
