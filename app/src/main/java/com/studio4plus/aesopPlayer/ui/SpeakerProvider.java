package com.studio4plus.aesopPlayer.ui;

import androidx.annotation.NonNull;

import com.studio4plus.aesopPlayer.concurrency.SimpleFuture;

public interface SpeakerProvider {
    @NonNull SimpleFuture<Object> obtainTts();
}
