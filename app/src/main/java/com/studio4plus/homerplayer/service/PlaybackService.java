package com.studio4plus.homerplayer.service;

import android.app.Notification;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import androidx.annotation.NonNull;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import androidx.core.content.ContextCompat;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.HomerPlayerApplication;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.events.CurrentBookChangedEvent;
import com.studio4plus.homerplayer.events.PlaybackFatalErrorEvent;
import com.studio4plus.homerplayer.events.PlaybackProgressedEvent;
import com.studio4plus.homerplayer.events.PlaybackStoppedEvent;
import com.studio4plus.homerplayer.events.PlaybackStoppingEvent;
import com.studio4plus.homerplayer.model.AudioBook;
import com.studio4plus.homerplayer.player.DurationQueryController;
import com.studio4plus.homerplayer.player.PlaybackController;
import com.studio4plus.homerplayer.player.Player;

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
        PLAYBACK
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
    private final SleepFadeOut sleepFadeOut = new SleepFadeOut();
    private final Vector<DurationQuery> queries = new Vector<>();

    @Override
    public IBinder onBind(Intent intent) {
        return new ServiceBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HomerPlayerApplication.getComponent(getApplicationContext()).inject(this);

        handler = new Handler(getMainLooper());
    }

    @Override
    public void onDestroy() {
        Crashlytics.log(Log.DEBUG, TAG, "PlaybackService.onDestroy");
        super.onDestroy();
        stopPlayback();
    }

    public void startPlayback(AudioBook book) {
        Preconditions.checkState(playbackInProgress == null);
        Preconditions.checkState(durationQueryInProgress == null);
        Preconditions.checkState(player == null);

        requestAudioFocus();
        player = HomerPlayerApplication.getComponent(getApplicationContext()).createAudioBookPlayer();
        player.setPlaybackSpeed(globalSettings.getPlaybackSpeed());

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
            // Repurpose it to start playback (almost always there's only one, but...)
            Crashlytics.log(Log.DEBUG, TAG,"PlaybackService.startPlayback: create DurationQuery");
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
            Crashlytics.log(Log.DEBUG, TAG,"PlaybackService.startPlayback: create AudioBookPlayback");
            playbackInProgress = new AudioBookPlayback(
                    player, handler, book, globalSettings.getJumpBackPreferenceMs());
        }
    }

    public void computeDuration(AudioBook book) {
        // With debug enabled exoplayer can take a very long time to do this operation.
        // (As in 10s of seconds.)
        if (book.getTotalDurationMs() == AudioBook.UNKNOWN_POSITION) {
            Crashlytics.log(Log.DEBUG, TAG,"PlaybackService.computeDuration: create DurationQuery");
            Player queryPlayer = HomerPlayerApplication.getComponent(getApplicationContext()).createAudioBookPlayer();
            DurationQuery queryQuery = new DurationQuery(queryPlayer, book, true);
            queries.add(queryQuery);
        }
    }

    public State getState() {
        if (player == null) {
            return State.IDLE;
        } else if (durationQueryInProgress != null) {
            return State.PREPARATION;
        } else {
            Preconditions.checkNotNull(playbackInProgress);
            return State.PLAYBACK;
        }
    }

    public void pauseForRewind() {
        Preconditions.checkNotNull(playbackInProgress);
        playbackInProgress.pauseForRewind();
    }

    public void resumeFromRewind() {
        Preconditions.checkNotNull(playbackInProgress);
        playbackInProgress.resumeFromRewind();
    }

    public void pauseForPause() {
        Preconditions.checkNotNull(playbackInProgress);
        playbackInProgress.pauseForPause();
    }

    public void resumeFromPause() {
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
        else if (playbackInProgress != null)
            playbackInProgress.stop();

        Crashlytics.log(Log.DEBUG, TAG, "PlaybackService.stopPlayback");
        onPlaybackEnded();
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        // TRANSIENT loss is reported on phone calls.
        // Notifications should request TRANSIENT_CAN_DUCK so they won't interfere.
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            Crashlytics.log(Log.DEBUG, TAG, "PlaybackService.onAudioFocusChange");
            stopPlayback();
        }
    }

    public class ServiceBinder extends Binder {
        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    private void onPlaybackEnded() {
        Crashlytics.log(Log.DEBUG, TAG, "PlaybackService.onPlaybackEnded");
        durationQueryInProgress = null;
        playbackInProgress = null;

        stopSleepTimer();
        dropAudioFocus();
        eventBus.post(PLAYBACK_STOPPING_EVENT);
    }

    private void onPlayerReleased() {
        Crashlytics.log(Log.DEBUG, TAG, "PlaybackService.onPlayerReleased");
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
                audioBook.updatePosition(controller.getCurrentPosition());
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
            AudioBook.Position position = audioBook.getLastPosition();
            long startPositionMs = Math.max(0, position.seekPosition - jumpBackMs);
            controller.start(position.file, startPositionMs);
            handler.postDelayed(updatePosition, UPDATE_TIME_MS);
            resetSleepTimer();
        }

        void stop() {
            controller.stop();
        }

        void pauseForRewind() {
            handler.removeCallbacks(updatePosition);
            stopSleepTimer();
            controller.pause();
        }

        void pauseForPause() {
            pauseForRewind();
        }

        void resumeFromRewind() {
            AudioBook.Position position = audioBook.getLastPosition();
            controller.start(position.file, position.seekPosition);
            handler.postDelayed(updatePosition, UPDATE_TIME_MS);
            resetSleepTimer();
        }

        void resumeFromPause() {
            AudioBook.Position position = audioBook.getLastPosition();
            long startPositionMs = Math.max(0, position.seekPosition - jumpBackMs);
            controller.start(position.file, startPositionMs);
            handler.postDelayed(updatePosition, UPDATE_TIME_MS);
            resetSleepTimer();
        }

        long getCurrentTotalPositionMs() {
            return audioBook.getLastPositionTime(controller.getCurrentPosition());
        }

        @Override
        public void onPlaybackProgressed(long currentPositionMs) {
            eventBus.post(new PlaybackProgressedEvent(
                    audioBook, audioBook.getLastPositionTime(currentPositionMs)));
        }

        @Override
        public void onDuration(File file, long durationMs) {
            audioBook.offerFileDuration(file, durationMs);
        }

        @Override
        public void onPlaybackEnded() {
            boolean hasMoreToPlay = audioBook.advanceFile();
            Crashlytics.log(Log.DEBUG, TAG, "PlaybackService.AudioBookPlayback.onPlaybackEnded: " +
                    (hasMoreToPlay ? "more to play" : "finished"));
            if (hasMoreToPlay) {
                AudioBook.Position position = audioBook.getLastPosition();
                controller.start(position.file, position.seekPosition);
            } else {
                audioBook.resetPosition();
                PlaybackService.this.onPlaybackEnded();
                controller.release();
            }
        }

        @Override
        public void onPlaybackStopped(long currentPositionMs) {
            audioBook.updatePosition(currentPositionMs);
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
            Crashlytics.log(Log.DEBUG, TAG, "PlaybackService.DurationQuery.onFinished");
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
                Crashlytics.log(Log.DEBUG, TAG, "SleepFadeOut stop");
                stopPlayback();
            } else {
                handler.postDelayed(this, STEP_INTERVAL_MS);
            }
        }
    }
}
