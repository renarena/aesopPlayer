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
package com.donnKey.aesopPlayer.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;
import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.events.PlaybackErrorEvent;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import de.greenrobot.event.EventBus;

public class Player {

    private final SimpleExoPlayer exoPlayer;
    private final EventBus eventBus;
    private ProgressiveMediaSource.Factory mediaSourceFactory;

    private float playbackSpeed = 1.0f;

    public Player(Context context, EventBus eventBus) {
        exoPlayer = ExoPlayerFactory.newSimpleInstance(context, new DefaultTrackSelector());
        this.eventBus = eventBus;
    }

    public PlaybackController createPlayback() {
        return new PlaybackControllerImpl(new Handler(Objects.requireNonNull(Looper.myLooper())));
    }

    public DurationQueryController createDurationQuery(List<File> files) {
        return new DurationQueryControllerImpl(files);
    }

    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = speed;
        PlaybackParameters params = new PlaybackParameters(speed, 1.0f);
        exoPlayer.setPlaybackParameters(params);
    }

    public float getPlaybackSpeed() {
        return this.playbackSpeed;
    }

    public void setPlaybackVolume(float volume) {
        exoPlayer.setVolume(volume);
    }

    public float getPlaybackVolume() {
        return exoPlayer.getVolume();
    }

    private void prepareAudioFile(File file, long startPositionMs) {
        Uri fileUri = Uri.fromFile(file);
        MediaSource source = getExtractorMediaSourceFactory().createMediaSource(fileUri);

        exoPlayer.seekTo(startPositionMs);
        exoPlayer.prepare(source, false, true);
    }

    private ProgressiveMediaSource.Factory getExtractorMediaSourceFactory() {
        if (mediaSourceFactory == null) {
            DataSource.Factory dataSourceFactory = new FileDataSourceFactory();
            DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
            extractorsFactory.setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);
            mediaSourceFactory = new ProgressiveMediaSource.Factory(
                    dataSourceFactory, extractorsFactory);
        }
        return mediaSourceFactory;
    }

    private class PlaybackControllerImpl implements
            com.google.android.exoplayer2.Player.EventListener,
            PlaybackController {

        private File currentFile;
        private Observer observer;
        private int lastPlaybackState;
        private final Handler handler;
        private final Runnable updateProgressTask = this::updateProgress;

        private PlaybackControllerImpl(Handler handler) {
            this.handler = handler;
            exoPlayer.setPlayWhenReady(true);  // Call before setting the listener.
            exoPlayer.addListener(this);
            lastPlaybackState = exoPlayer.getPlaybackState();
        }

        @Override
        public void setObserver(Observer observer) {
            this.observer = observer;
        }

        boolean isPlaying;

        @Override
        public void start(File currentFile, long startPositionMs, boolean chainFile) {
            Preconditions.checkNotNull(observer);
            this.currentFile = currentFile;
            isPlaying = true;
            // If we're starting from cold (from the user pressing start), delay actually starting
            // audio for a bit to avoid glitches, and to give the "face down" detector time to
            // stop the sound if the device is face down. This can happen when running remotely
            // and doing maintenance, and we don't want to wake a sleeping user.
            // Otherwise, start audio as soon as it's ready, particularly when transitioning
            // between book segments.
            if (chainFile) {
                exoPlayer.setPlayWhenReady(true);
            } else {
                exoPlayer.setPlayWhenReady(false);
                handler.postDelayed(()-> {
                            if (isPlaying) {
                                exoPlayer.setPlayWhenReady(true);
                            }
                        },
                        // 750 seems to work reliably on my slowest device; 500 missed
                        // occasionally. It's still imperceptible when hitting start.
                        750);
            }
            prepareAudioFile(currentFile, startPositionMs);
            updateProgress();
        }

        @Override
        public void pause() {
            isPlaying = false;
            exoPlayer.setPlayWhenReady(false);
            // This ought to be done in onPlayerStateChanged but detecting pause is not as trivial
            // as doing this here directly.
            handler.removeCallbacks(updateProgressTask);
        }

        @Override
        public void resume(File currentFile, long startPositionMs) {
            start(currentFile, startPositionMs, false);
        }

        public void stop() {
            long segmentPosition = exoPlayer.getCurrentPosition();
            isPlaying = false;
            exoPlayer.stop();
            observer.onPlaybackStopped(segmentPosition);
        }

        @Override
        public void release() {
            isPlaying = false;
            exoPlayer.stop();
        }

        @Override
        public long getSegmentPositionMs() {
            return exoPlayer.getCurrentPosition();
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (playbackState == lastPlaybackState)
                return;
            lastPlaybackState = playbackState;

            switch(playbackState) {
                case com.google.android.exoplayer2.Player.STATE_READY:
                    observer.onDuration(currentFile, exoPlayer.getDuration());
                    break;
                case com.google.android.exoplayer2.Player.STATE_ENDED:
                    handler.removeCallbacks(updateProgressTask);
                    observer.onPlaybackEnded();
                    break;
                case com.google.android.exoplayer2.Player.STATE_IDLE:
                    handler.removeCallbacks(updateProgressTask);
                    exoPlayer.release();
                    exoPlayer.removeListener(this);
                    observer.onPlayerReleased();
                    break;
                //case com.google.android.exoplayer2.Player.STATE_BUFFERING:
                //  break;
            }
        }

        @Override
        public void onPlayerError(@NonNull ExoPlaybackException error) {
            eventBus.post(new PlaybackErrorEvent(
                    error.getMessage() != null ? error.getMessage() : "Untitled Player exception",
                    exoPlayer.getDuration(),
                    exoPlayer.getCurrentPosition(),
                    getFormatDescription()));
            if (error.type == ExoPlaybackException.TYPE_SOURCE) {
                IOException exception = error.getSourceException();
                if (exception instanceof FileDataSource.FileDataSourceException
                        && exception.getCause() instanceof EOFException) {
                    // May happen with files that have seeking or length information.
                    observer.onPlaybackEnded();
                    return;
                }
            }
            observer.onPlaybackError(currentFile);
        }

        private void updateProgress() {
            long segmentPositionMs = getSegmentPositionMs();
            observer.onPlaybackProgressed(segmentPositionMs);

            // Aim a moment after the expected second change. It's necessary because the actual
            // playback speed may be slightly different than playbackSpeed when it's different
            // than 1.0.
            long delayMs = (long) ((1200 - (segmentPositionMs % 1000)) * playbackSpeed);
            if (delayMs < 100)
                delayMs += (long) (1000 * playbackSpeed);

            if (isPlaying) {
                // Clearing updateProgressTask from the handler doesn't always work (I think that
                // the runnable, once posted to run, isn't removed). That can cause this to run away, so
                // belt and suspenders...
                handler.postDelayed(updateProgressTask, delayMs);
            }
        }

        private String getFormatDescription() {
            Format format = exoPlayer.getAudioFormat();
            if (format != null) {
                return format.toString();
            } else {
                String fileName = currentFile.getName();
                int suffixIndex = fileName.lastIndexOf('.');
                return suffixIndex != -1 ? fileName.substring(suffixIndex) : "";
            }
        }
    }

    private class DurationQueryControllerImpl implements
            com.google.android.exoplayer2.Player.EventListener,
            DurationQueryController {

        private final Iterator<File> iterator;
        private File currentFile;
        private Observer observer;
        private boolean releaseOnIdle = false;

        private DurationQueryControllerImpl(@NonNull List<File> files) {
            Preconditions.checkArgument(!files.isEmpty());
            this.iterator = files.iterator();
        }

        @Override
        public void start(Observer observer) {
            this.observer = observer;
            exoPlayer.setPlayWhenReady(false);  // Call before setting the listener.
            exoPlayer.addListener(this);
            processNextFile();
        }

        @Override
        public void stop() {
            releaseOnIdle = true;
            exoPlayer.stop();
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            switch(playbackState) {
                case com.google.android.exoplayer2.Player.STATE_READY:
                    observer.onDuration(currentFile, exoPlayer.getDuration());
                    boolean hasNext = processNextFile();
                    if (!hasNext) {
                        exoPlayer.stop();
                    }
                    break;
                case com.google.android.exoplayer2.Player.STATE_IDLE:
                    exoPlayer.removeListener(this);
                    if (releaseOnIdle) {
                        exoPlayer.release();
                        observer.onPlayerReleased();
                    } else {
                        observer.onFinished();
                    }
                    break;
                case com.google.android.exoplayer2.Player.STATE_BUFFERING:
                case com.google.android.exoplayer2.Player.STATE_ENDED:
                    break;
            }
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            releaseOnIdle = true;
            observer.onPlayerError(currentFile);
        }

        private boolean processNextFile() {
            boolean hasNext = iterator.hasNext();
            if (hasNext) {
                currentFile = iterator.next();
                prepareAudioFile(currentFile, 0);
            }
            return hasNext;
        }
    }
}
