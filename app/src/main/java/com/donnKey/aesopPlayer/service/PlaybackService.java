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
package com.donnKey.aesopPlayer.service;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaRouter;
import android.os.Handler;
import androidx.annotation.NonNull;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import androidx.core.content.ContextCompat;
import android.util.Log;

import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.model.BookPosition;
import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.events.CurrentBookChangedEvent;
import com.donnKey.aesopPlayer.events.PlaybackFatalErrorEvent;
import com.donnKey.aesopPlayer.events.PlaybackProgressedEvent;
import com.donnKey.aesopPlayer.events.PlaybackStoppedEvent;
import com.donnKey.aesopPlayer.events.PlaybackStoppingEvent;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.player.DurationQueryController;
import com.donnKey.aesopPlayer.player.PlaybackController;
import com.donnKey.aesopPlayer.player.Player;

import java.io.File;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

public class PlaybackService
        extends Service
        implements AudioManager.OnAudioFocusChangeListener {

    public enum State {
        IDLE,
        PREPARATION,
        PLAYBACK,
        PAUSED
    }

    private static final long FADE_OUT_DURATION_MS = TimeUnit.SECONDS.toMillis(10);

    private static final String TAG = "PlaybackService";
    private static final int NOTIFICATION_ID = R.string.playback_service_notification;
    private static final PlaybackStoppingEvent PLAYBACK_STOPPING_EVENT = new PlaybackStoppingEvent();
    private static final PlaybackStoppedEvent PLAYBACK_STOPPED_EVENT = new PlaybackStoppedEvent();

    @SuppressWarnings("WeakerAccess")
    @Inject public GlobalSettings globalSettings;
    @SuppressWarnings("WeakerAccess")
    @Inject public EventBus eventBus;

    private Player player;
    private DurationQuery durationQueryInProgress;
    private AudioBookPlayback playbackInProgress;
    private Handler handler;
    private boolean userPaused;
    private final SleepFadeOut sleepFadeOut = new SleepFadeOut();
    private final Vector<DurationQuery> queries = new Vector<>();

    @Override
    public IBinder onBind(Intent intent) {
        return new ServiceBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AesopPlayerApplication.getComponent(getApplicationContext()).inject(this);

        handler = new Handler(getMainLooper());
    }

    @Override
    public void onDestroy() {
        CrashWrapper.log(Log.DEBUG, TAG, "PlaybackService.onDestroy");
        super.onDestroy();
        stopPlayback();
    }

    public void startPlayback(AudioBook book) {
        Preconditions.checkState(durationQueryInProgress == null);

        if (playbackInProgress != null) {
            Preconditions.checkState(player != null);
            // Just believe whatever's already there
        }
        else {
            Preconditions.checkState(player == null);
            requestAudioFocus();
            player = AesopPlayerApplication.getComponent(getApplicationContext()).createAudioBookPlayer();
            player.setPlaybackSpeed(globalSettings.getPlaybackSpeed());
            restoreSoundInfo();

            // Needed to notify the user of the service that's handling the Audio and to keep
            // (Oreo or greater) from shutting it down after a while.
            Notification notification = NotificationUtil.createForegroundServiceNotification(
                    getApplicationContext(),
                    R.string.playback_service_notification,
                    android.R.drawable.ic_media_play);
            ContextCompat.startForegroundService(
                    this, new Intent(this, PlaybackService.class));
            startForeground(NOTIFICATION_ID, notification);

            if (book.getTotalDurationMs() == AudioBook.UNKNOWN_POSITION)
            findQuery: {
                // There should be a query in progress.
                // Re-purpose it to start playback (almost always there's only one, but...)
                CrashWrapper.log(Log.DEBUG, TAG,"PlaybackService.startPlayback: create DurationQuery");
                for (DurationQuery q : queries) {
                    if (q.audioBook == book) {
                        q.isQueryOnly = false;
                        durationQueryInProgress = q;
                        queries.remove(q);
                        break findQuery;
                    }
                }

                // Shouldn't ever happen, but just in case
                durationQueryInProgress = new DurationQuery(player, book, false);
            } else {
                CrashWrapper.log(Log.DEBUG, TAG,"PlaybackService.startPlayback: create AudioBookPlayback");
                playbackInProgress = new AudioBookPlayback(
                        player, handler, book, globalSettings.getJumpBackPreferenceMs());
                playbackInProgress.start();
            }
        }
    }

    public void computeDuration(AudioBook book) {
        // With debug enabled exoplayer can take a very long time to do this operation.
        // (As in 10s of seconds.)
        if (book.getTotalDurationMs() == AudioBook.UNKNOWN_POSITION) {
            CrashWrapper.log(Log.DEBUG, TAG,"PlaybackService.computeDuration: create DurationQuery");
            Player queryPlayer = AesopPlayerApplication.getComponent(getApplicationContext()).createAudioBookPlayer();
            DurationQuery queryQuery = new DurationQuery(queryPlayer, book, true);
            queries.add(queryQuery);
        }
    }

    public State getState() {
        if (player == null) {
            return State.IDLE;
        } else if (durationQueryInProgress != null) {
            return State.PREPARATION;
        } else if (userPaused) {
            return State.PAUSED;
        }
        else {
            Preconditions.checkNotNull(playbackInProgress);
            return State.PLAYBACK;
        }
    }

    public void pauseForRewind() {
        userPaused = true;
        Preconditions.checkNotNull(playbackInProgress);
        playbackInProgress.pauseForRewind();
    }

    public void resumeFromRewind() {
        userPaused = false;
        Preconditions.checkNotNull(playbackInProgress);
        playbackInProgress.resumeFromRewind();
    }

    public void pauseForPause() {
        userPaused = true;
        Preconditions.checkNotNull(playbackInProgress);
        playbackInProgress.pauseForPause();
    }

    public void resumeFromPause() {
        userPaused = false;
        Preconditions.checkNotNull(playbackInProgress);
        playbackInProgress.resumeFromPause();
    }

    public long getCurrentTotalPositionMs() {
        Preconditions.checkNotNull(playbackInProgress);
        return playbackInProgress.getCurrentTotalPositionMs();
    }

    public AudioBook getAudioBookBeingPlayed() {
        Preconditions.checkNotNull(playbackInProgress);
        return playbackInProgress.audioBook;
    }

    public void stopPlayback() {
        if (durationQueryInProgress != null)
            durationQueryInProgress.stop();
        else if (playbackInProgress != null) {
            captureSoundInfo();
            playbackInProgress.stop();
        }

        CrashWrapper.log(Log.DEBUG, TAG, "PlaybackService.stopPlayback");
        onPlaybackEnded();
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        // TRANSIENT loss is reported on phone calls.
        // Notifications should request TRANSIENT_CAN_DUCK so they won't interfere.
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            CrashWrapper.log(Log.DEBUG, TAG, "PlaybackService.onAudioFocusChange");
            stopPlayback();
        }
    }


    public class ServiceBinder extends Binder {
        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    private void onPlaybackEnded() {
        CrashWrapper.log(Log.DEBUG, TAG, "PlaybackService.onPlaybackEnded");
        durationQueryInProgress = null;
        playbackInProgress = null;

        stopSleepTimer();
        dropAudioFocus();
        eventBus.post(PLAYBACK_STOPPING_EVENT);
    }

    private void onPlayerReleased() {
        CrashWrapper.log(Log.DEBUG, TAG, "PlaybackService.onPlayerReleased");
        if (playbackInProgress != null || durationQueryInProgress != null) {
            onPlaybackEnded();
        }
        player = null;
        eventBus.post(PLAYBACK_STOPPED_EVENT);
        stopForeground(true);
        stopSelf();
    }

    private void requestAudioFocus() {
        AudioManager audioManager =
                (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.requestAudioFocus(
                this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    private void dropAudioFocus() {
        AudioManager audioManager =
                (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.abandonAudioFocus(this);
    }

    public void resetSleepTimer() {
        stopSleepTimer();
        long timerMs = globalSettings.getSleepTimerMs();
        if (timerMs > 0)
            sleepFadeOut.scheduleStart(timerMs);
    }

    private void stopSleepTimer() {
        sleepFadeOut.reset();
    }

    private class AudioBookPlayback implements PlaybackController.Observer {

        final @NonNull AudioBook audioBook;
        private final @NonNull PlaybackController controller;
        private final @NonNull Handler handler;
        private final int jumpBackMs;
        private final @NonNull Runnable updatePosition = new Runnable() {
            @Override
            public void run() {
                audioBook.updatePosition(controller.getSegmentPositionMs());
                handler.postDelayed(updatePosition, UPDATE_TIME_MS);
            }
        };

        private final long UPDATE_TIME_MS = TimeUnit.SECONDS.toMillis(10);

        private AudioBookPlayback(
                @NonNull Player player,
                @NonNull Handler handler,
                @NonNull AudioBook audioBook,
                int jBM) {
            this.audioBook = audioBook;
            this.handler = handler;
            jumpBackMs = jBM;

            controller = player.createPlayback();
            controller.setObserver(this);
        }

        void start() {
            userPaused = false;
            BookPosition position = audioBook.getLastPosition();
            long startPositionMs = Math.max(0, position.seekPosition - jumpBackMs);
            resetSleepTimer();
            controller.start(audioBook.getFile(position), startPositionMs, false);
            handler.postDelayed(updatePosition, UPDATE_TIME_MS);
        }

        void stop() {
            controller.stop();
        }

        void pauseForRewind() {
            handler.removeCallbacks(updatePosition);
            stopSleepTimer();
            controller.pause();
        }

        boolean userPaused = false;

        void pauseForPause() {
            userPaused = true;
            pauseForRewind();
        }

        void resumeFromRewind() {
            BookPosition position = audioBook.getLastPosition();
            controller.start(audioBook.getFile(position), position.seekPosition, false);
            handler.postDelayed(updatePosition, UPDATE_TIME_MS);
            resetSleepTimer();
            if (userPaused) {
                controller.pause();
            }
        }

        void resumeFromPause() {
            userPaused = false;
            BookPosition position = audioBook.getLastPosition();
            long startPositionMs = Math.max(0, position.seekPosition - jumpBackMs);
            controller.resume(audioBook.getFile(position), startPositionMs);
            handler.postDelayed(updatePosition, UPDATE_TIME_MS);
            resetSleepTimer();
        }

        long getCurrentTotalPositionMs() {
            return audioBook.getLastTotalPositionTime(controller.getSegmentPositionMs());
        }

        @Override
        public void onPlaybackProgressed(long segmentPositionMs) {
            BookPosition currentPosition = new BookPosition(audioBook, segmentPositionMs);
            eventBus.post(new PlaybackProgressedEvent(
                    audioBook, audioBook.toMs(currentPosition)));
        }

        @Override
        public void onDuration(File file, long durationMs) {
            audioBook.offerFileDuration(file, durationMs);
        }

        @Override
        public void onPlaybackEnded() {
            boolean hasMoreToPlay = audioBook.advanceFile();
            CrashWrapper.log(Log.DEBUG, TAG, "PlaybackService.AudioBookPlayback.onPlaybackEnded: " +
                    (hasMoreToPlay ? "more to play" : "finished"));
            if (hasMoreToPlay) {
                BookPosition position = audioBook.getLastPosition();
                controller.start(audioBook.getFile(position), position.seekPosition, true);
            } else {
                audioBook.resetPosition();
                audioBook.setCompleted(true);
                PlaybackService.this.onPlaybackEnded();
                controller.release();
            }
        }

        @Override
        public void onPlaybackStopped(long currentSegmentPositionMs) {
            audioBook.updatePosition(currentSegmentPositionMs);
        }

        @Override
        public void onPlaybackError(File path) {
            eventBus.post(new PlaybackFatalErrorEvent(path));
        }

        @Override
        public void onPlayerReleased() {
            handler.removeCallbacks(updatePosition);
            PlaybackService.this.onPlayerReleased();
        }
    }

    class DurationQuery implements DurationQueryController.Observer {

        // DurationQueries are a bit tricky:
        // We want to display the total book time, but we must actually have exoplayer
        // scan all the files to get the total time. And that can take a long time.
        // We remember the length after we've done that once, but with a new batch
        // of books, and with the user browsing, we want to do the scans in the background.
        // But if the user actually starts a book, we want the completion of the scan
        // to start the player. We thus create a list of duration scans in progress
        // and if the user starts a book, we find it in the list and convert it to the
        // query that starts playback. We also call back to the display to update
        // the time once we have it. There's frequently a visible delay the first
        // time the book is displayed, but no other effect.
        private final AudioBook audioBook;
        private final DurationQueryController controller;
        private boolean isQueryOnly;

        private DurationQuery(Player player, AudioBook audioBook, boolean queryOnly) {
            this.audioBook = audioBook;
            isQueryOnly = queryOnly;

            List<File> files = audioBook.getFilesWithNoDuration();
            controller = player.createDurationQuery(files);
            controller.start(this);
        }

        void stop() {
            controller.stop();
        }

        @Override
        public void onDuration(File file, long durationMs) {
            audioBook.offerFileDuration(file, durationMs);
        }

        @Override
        public void onFinished() {
            CrashWrapper.log(Log.DEBUG, TAG, "PlaybackService.DurationQuery.onFinished");
            if (isQueryOnly) {
                queries.remove(this);
                EventBus.getDefault().post(new CurrentBookChangedEvent(this.audioBook));
            }
            else {
                Preconditions.checkState(durationQueryInProgress == this);
                durationQueryInProgress = null;
                playbackInProgress = new AudioBookPlayback(
                        player, handler, audioBook, globalSettings.getJumpBackPreferenceMs());
            }
        }

        @Override
        public void onPlayerReleased() {
            PlaybackService.this.onPlayerReleased();
        }

        @Override
        public void onPlayerError(File path) {
            eventBus.post(new PlaybackFatalErrorEvent(path));
        }
    }

    private class SleepFadeOut implements Runnable {
        private float currentVolume = 1.0f;
        private final long STEP_INTERVAL_MS = 100;
        private final float VOLUME_DOWN_STEP =  (float) STEP_INTERVAL_MS / FADE_OUT_DURATION_MS;

        void scheduleStart(long delay) {
            handler.postDelayed(this, delay);
        }

        void reset() {
            handler.removeCallbacks(this);
            currentVolume = 1.0f;

            // The player may have been released already.
            if (player != null)
              player.setPlaybackVolume(currentVolume);
        }

        @Override
        public void run() {
            currentVolume -= VOLUME_DOWN_STEP;
            player.setPlaybackVolume(currentVolume);
            if (currentVolume <= 0) {
                CrashWrapper.log(Log.DEBUG, TAG, "SleepFadeOut stop");
                stopPlayback();
            } else {
                handler.postDelayed(this, STEP_INTERVAL_MS);
            }
        }
    }

    private void captureSoundInfo() {
        // Since changing the sound level may be an issue for many users, and because it
        // gets changed incidentally to other use of the device, let's save/restore the most
        // recent value we were actually using.
        // The key is derived from the MediaRouter's name for the current playback device,
        // thus we collect (or update) a value for each device when it stops.
        // (These are specific to the current device being used!)

        // We'll not try to deal with changing audio devices while playing
        AudioManager audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        MediaRouter mediaRouter = (MediaRouter)getApplicationContext().getSystemService(Context.MEDIA_ROUTER_SERVICE);
        SharedPreferences preferences = globalSettings.appSharedPreferences();

        MediaRouter.RouteInfo routeInfo = mediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);
        String levelKey = "Level:" + routeInfo.getName();
        int levelValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        // No fixed keys... we'll deal with preferences directly
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(levelKey, levelValue);
        editor.apply();
    }

    private void restoreSoundInfo() {
        AudioManager audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        MediaRouter mediaRouter = (MediaRouter)getApplicationContext().getSystemService(Context.MEDIA_ROUTER_SERVICE);
        SharedPreferences preferences = globalSettings.appSharedPreferences();

        MediaRouter.RouteInfo routeInfo = mediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);
        String levelKey = "Level:" + routeInfo.getName();

        int levelValue = preferences.getInt(levelKey, -1);
        if (levelValue < 0) {
            // Default value, do nothing
            return;
        }
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, levelValue, 0);
    }

    public void setVolume(float volume) {
        player.setPlaybackVolume(volume);
    }

    public float getVolume() {
        return player.getPlaybackVolume();
    }

    public void setSpeed(float speed) {
        player.setPlaybackSpeed(speed);
    }

    public float getSpeed() {
        return player.getPlaybackSpeed();
    }
}
