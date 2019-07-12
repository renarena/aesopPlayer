package com.donnKey.aesopPlayer.service;

import android.content.Context;

import com.donnKey.aesopPlayer.player.Player;

import dagger.Module;
import dagger.Provides;
import de.greenrobot.event.EventBus;

@Module
public class AudioBookPlayerModule {
    @Provides
    Player provideAudioBookPlayer(Context context, EventBus eventBus) {
        return new Player(context, eventBus);
    }
}
