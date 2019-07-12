package com.donnKey.aesopPlayer.ui;

import androidx.annotation.NonNull;

import com.donnKey.aesopPlayer.concurrency.SimpleFuture;

public interface SpeakerProvider {
    @NonNull SimpleFuture<Object> obtainTts();
}
