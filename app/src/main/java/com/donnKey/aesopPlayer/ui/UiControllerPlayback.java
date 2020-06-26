/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2020 Donn S. Terry
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

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.analytics.AnalyticsTracker;
import com.donnKey.aesopPlayer.events.PlaybackProgressedEvent;
import com.donnKey.aesopPlayer.events.PlaybackStoppingEvent;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.service.PlaybackService;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

public class UiControllerPlayback {

    private static final String TAG = "UiControllerPlayback";

    static class Factory {
        private final @NonNull EventBus eventBus;
        private final @NonNull AnalyticsTracker analyticsTracker;

        @Inject
        Factory(@NonNull EventBus eventBus, @NonNull AnalyticsTracker analyticsTracker) {
            this.eventBus = eventBus;
            this.analyticsTracker = analyticsTracker;
        }

        UiControllerPlayback create(
                @NonNull PlaybackService playbackService, @NonNull PlaybackUi ui) {
            return new UiControllerPlayback(eventBus, analyticsTracker, playbackService, ui);
        }
    }

    private final @NonNull EventBus eventBus;
    private final @NonNull AnalyticsTracker analyticsTracker;
    private final @NonNull Handler mainHandler;
    final @NonNull PlaybackService playbackService;
    private final @NonNull PlaybackUi ui;

    // Non-null only when rewinding.
    private @Nullable FFRewindController ffRewindController;

    private UiControllerPlayback(@NonNull EventBus eventBus,
                         @NonNull AnalyticsTracker analyticsTracker,
                         @NonNull PlaybackService playbackService,
                         @NonNull PlaybackUi playbackUi) {
        this.eventBus = eventBus;
        this.analyticsTracker = analyticsTracker;
        this.playbackService = playbackService;
        this.ui = playbackUi;
        this.mainHandler = new Handler(Looper.getMainLooper());

        ui.initWithController(this);

        eventBus.register(this);

        if (playbackService.getState() == PlaybackService.State.PLAYBACK) {
            ui.onPlaybackProgressed(playbackService.getCurrentTotalPositionMs());
        }
    }

    void shutdown() {
        // Caution: if this needs to do more, make sure that it pairs nicely with resumeFromPause
        stopRewindIfActive();
        eventBus.unregister(this);
    }

    void stopRewindIfActive() {
        if (ffRewindController != null)
            stopRewind();
    }

    void startPlayback(@NonNull AudioBook book) {
        playbackService.startPlayback(book);
    }

    public void stopPlayback() {
        CrashWrapper.log(TAG, "UiControllerPlayback.stopPlayback");
        getAudioBookBeingPlayed().insertStop(playbackService.getCurrentTotalPositionMs());
        playbackService.stopPlayback();
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void onEvent(PlaybackStoppingEvent event) {
        ui.onPlaybackStopping();
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void onEvent(PlaybackProgressedEvent event) {
        ui.onPlaybackProgressed(event.playbackTotalPositionMs);
    }

    public AudioBook getAudioBookBeingPlayed() {
        return playbackService.getAudioBookBeingPlayed();
    }

    public void pauseForRewind() {
        playbackService.pauseForRewind();
    }

    void pauseForPause() {
        ui.onChangeStopPause(R.string.button_pause);
        getAudioBookBeingPlayed().insertStop(playbackService.getCurrentTotalPositionMs());
        playbackService.pauseForPause();
    }

    public void resumeFromRewind() {
        playbackService.resumeFromRewind();
    }

    void resumeFromPause() {
        ui.onChangeStopPause(R.string.button_stop);
        eventBus.register(this);
        playbackService.resumeFromPause();
    }

    public void startRewind(boolean isForward, @NonNull FFRewindTimer.Observer timerObserver) {
        Preconditions.checkState(ffRewindController == null);

        ffRewindController = new FFRewindController(
                mainHandler,
                ui,
                playbackService.getCurrentTotalPositionMs(),
                playbackService.getAudioBookBeingPlayed().getTotalDurationMs(),
                isForward,
                timerObserver);
        ffRewindController.start();
        analyticsTracker.onFfRewindStarted(isForward);
    }

    public void stopRewind() {
        if (ffRewindController != null) {
            analyticsTracker.onFfRewindFinished(ffRewindController.getRewindWallTimeMs());
            playbackService.getAudioBookBeingPlayed().updateTotalPosition(
                    ffRewindController.getDisplayTimeMs());
            ffRewindController.stop();
            ffRewindController = null;
        } else {
            analyticsTracker.onFfRewindAborted();
        }
    }

    private static class FFRewindController implements FFRewindTimer.Observer {
        private static final int[] SPEED_LEVEL_SPEEDS = { 250, 100, 25  };
        private static final long[] SPEED_LEVEL_THRESHOLDS = { 15_000, 90_000, Long.MAX_VALUE };
        private static final RewindSound.SpeedLevel[] SPEED_LEVELS =
                {RewindSound.SpeedLevel.REGULAR, RewindSound.SpeedLevel.FAST, RewindSound.SpeedLevel.FASTEST };

        private final @NonNull PlaybackUi ui;
        private final @NonNull FFRewindTimer timer;

        private final long startTimeNano;
        private final long initialDisplayTimeMs;
        private int currentSpeedLevelIndex = -1;

        final boolean isFF;

        FFRewindController(
                @NonNull Handler handler,
                @NonNull PlaybackUi ui,
                long currentTotalPositionMs,
                long maxTotalPositionMs,
                boolean isFF,
                @NonNull FFRewindTimer.Observer timerObserver) {
            this.ui = ui;
            this.isFF = isFF;
            startTimeNano = System.nanoTime();
            initialDisplayTimeMs = currentTotalPositionMs;

            timer = new FFRewindTimer(handler, currentTotalPositionMs, maxTotalPositionMs);
            timer.addObserver(timerObserver);
            timer.addObserver(this);
        }

        void start() {
            setSpeedLevel(0);
            timer.run();
        }

        void stop() {
            ui.onFFRewindSpeed(RewindSound.SpeedLevel.STOP);
            timer.removeObserver(this);
            timer.stop();
        }

        @Override
        public void onTimerUpdated(long displayTimeMs) {
            long skippedMs = Math.abs(displayTimeMs - initialDisplayTimeMs);
            if (skippedMs > SPEED_LEVEL_THRESHOLDS[currentSpeedLevelIndex])
                setSpeedLevel(currentSpeedLevelIndex + 1);
        }

        @Override
        public void onTimerLimitReached() {
            ui.onFFRewindSpeed(RewindSound.SpeedLevel.STOP);
        }

        long getDisplayTimeMs() {
            return timer.getDisplayTimeMs();
        }

        long getRewindWallTimeMs() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNano);
        }

        private void setSpeedLevel(int speedLevelIndex) {
            if (speedLevelIndex != currentSpeedLevelIndex) {
                currentSpeedLevelIndex = speedLevelIndex;
                int speed = SPEED_LEVEL_SPEEDS[speedLevelIndex];
                timer.changeSpeed(isFF ? speed : -speed);

                ui.onFFRewindSpeed(SPEED_LEVELS[speedLevelIndex]);
            }
        }
    }

    public void setVolume(float volume) {
        playbackService.setVolume(volume);
    }

    public float getVolume() {
        return playbackService.getVolume();
    }

    public void setSpeed(float speed) {
        playbackService.setSpeed(speed);
    }

    public float getSpeed() {
        return playbackService.getSpeed();
    }

    public long getCurrentPositionMs() {
        return playbackService.getCurrentTotalPositionMs();
    }
}
