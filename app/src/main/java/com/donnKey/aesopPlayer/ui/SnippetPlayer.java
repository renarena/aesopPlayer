/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2019 Donn S. Terry
 * Copyright (c) 2015-2017 Marcin Simonides
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.donnKey.aesopPlayer.ui;

import android.content.Context;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.model.BookPosition;
import com.donnKey.aesopPlayer.player.PlaybackController;
import com.donnKey.aesopPlayer.player.Player;

import java.io.File;

import de.greenrobot.event.EventBus;

/**
 * Plays the current audiobook for a short amount of time. Just to demonstrate.
 */
public class SnippetPlayer implements PlaybackController.Observer {

    private static final long PLAYBACK_TIME_MS = 5000;

    private static final String TAG = "SnippetPlayer";
    private final PlaybackController playbackController;
    private long startPositionMs = -1;
    private boolean isPlaying = false;

    public SnippetPlayer(Context context, EventBus eventBus, float playbackSpeed) {
        Player player = new Player(context, eventBus);
        player.setPlaybackSpeed(playbackSpeed);
        playbackController = player.createPlayback();
        playbackController.setObserver(this);
    }

    public void play(AudioBook audioBook) {
        BookPosition position = audioBook.getLastPosition();

        isPlaying = true;
        playbackController.start(audioBook.getFile(position), position.seekPosition, true);
    }

    public void stop() {
        playbackController.stop();
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    @Override
    public void onDuration(File file, long durationMs) {}

    @Override
    public void onPlaybackProgressed(long segmentPositionMs) {
        if (startPositionMs < 0) {
            startPositionMs = segmentPositionMs;
        } else {
            if (segmentPositionMs - startPositionMs > PLAYBACK_TIME_MS) {
                playbackController.stop();
            }
        }
    }

    @Override
    public void onPlaybackEnded() {}

    @Override
    public void onPlaybackStopped(long currentPositionMs) {}

    @Override
    public void onPlaybackError(File path) {
        Crashlytics.log(Log.DEBUG, TAG,"Unable to play snippet: " + path.toString());
    }

    @Override
    public void onPlayerReleased() {
        isPlaying = false;
    }
}
