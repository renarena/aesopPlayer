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
package com.donnKey.aesopPlayer.analytics;

import android.content.Context;

import androidx.annotation.NonNull;

import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.events.AudioBooksChangedEvent;
import com.donnKey.aesopPlayer.events.DemoSamplesInstallationFinishedEvent;
import com.donnKey.aesopPlayer.events.DemoSamplesInstallationStartedEvent;
import com.donnKey.aesopPlayer.events.PlaybackErrorEvent;
import com.donnKey.aesopPlayer.events.PlaybackProgressedEvent;
import com.donnKey.aesopPlayer.events.PlaybackStoppingEvent;
import com.donnKey.aesopPlayer.events.SettingsEnteredEvent;
import com.donnKey.aesopPlayer.model.AudioBook;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

public class AnalyticsTracker {
    private static final String BOOKS_INSTALLED = "booksInstalled";
    private static final String BOOKS_INSTALLED_TYPE_KEY = "type";
    private static final String BOOK_PLAYED = "bookPlayed";
    private static final String BOOK_PLAYED_TYPE_KEY = "type";
    private static final String BOOK_PLAYED_DURATION_KEY = "durationBucket";
    private static final String BOOK_PLAYED_TYPE_SAMPLE = "sample";
    private static final String BOOK_PLAYED_TYPE_USER_CONTENT = "userContent";
    private static final String BOOK_SWIPED = "bookSwiped";
    private static final String BOOK_LIST_DISPLAYED = "bookListDisplayed";
    private static final String FF_REWIND = "ffRewind";
    private static final String FF_REWIND_ABORTED = "ffRewindAborted";
    private static final String FF_REWIND_IS_FF_KEY = "isFf";
    private static final String PERMISSION_RATIONALE_SHOWN = "permissionRationaleShown";
    private static final String PERMISSION_RATIONALE_REQUEST_KEY = "permissionRequest";
    private static final String PLAYBACK_ERROR = "playbackError";
    private static final String PLAYBACK_ERROR_MESSAGE_KEY = "message";
    private static final String PLAYBACK_ERROR_FORMAT_KEY = "format";
    private static final String PLAYBACK_ERROR_DURATION_KEY = "durationMs";
    private static final String PLAYBACK_ERROR_POSITION_KEY = "positionMs";
    private static final String SAMPLES_DOWNLOAD_STARTED = "samplesDownloadStarted";
    private static final String SAMPLES_DOWNLOAD_SUCCESS = "samplesDownloadSuccess";
    private static final String SAMPLES_DOWNLOAD_FAILURE = "samplesDownloadFailure";

    private static final NavigableMap<Long, String> PLAYBACK_DURATION_BUCKETS = new TreeMap<>();

    private final GlobalSettings globalSettings;
    private final StatsLogger stats;

    private CurrentlyPlayed currentlyPlayed;

    @Inject
    public AnalyticsTracker(
            Context context, GlobalSettings globalSettings, @NonNull EventBus eventBus) {
        this.globalSettings = globalSettings;
        eventBus.register(this);

        // Not bothering with injecting the stats logger, at least until I need to add a debug
        // implementation.
        stats = new StatsLogger(context);
    }

    @SuppressWarnings("unused")
    public void onEvent(@NonNull AudioBooksChangedEvent event) {
        if (event.contentType == null) {
            // nothing interesting happened
            return;
        }
        if (event.contentType.supersedes(globalSettings.booksEverInstalled())) {
            Map<String, String> data = Collections.singletonMap(
                    BOOKS_INSTALLED_TYPE_KEY, event.contentType.name());
            stats.logEvent(BOOKS_INSTALLED, data);
        }
        globalSettings.setBooksEverInstalled(event.contentType);
    }

    @SuppressWarnings("unused")
    public void onEvent(SettingsEnteredEvent event) {
        globalSettings.setSettingsEverEntered();
    }

    @SuppressWarnings("unused")
    public void onEvent(DemoSamplesInstallationStartedEvent event) {
        stats.logEvent(SAMPLES_DOWNLOAD_STARTED);
    }

    @SuppressWarnings("unused")
    public void onEvent(@NonNull DemoSamplesInstallationFinishedEvent event) {
        if (event.success) {
            stats.logEvent(SAMPLES_DOWNLOAD_SUCCESS);
        } else {
            Map<String, String> data = Collections.singletonMap("error", event.errorMessage);
            stats.logEvent(SAMPLES_DOWNLOAD_FAILURE, data);
        }
    }

    @SuppressWarnings("unused")
    public void onEvent(PlaybackProgressedEvent event) {
        if (currentlyPlayed == null)
            currentlyPlayed = new CurrentlyPlayed(event.audioBook, System.nanoTime());
    }

    @SuppressWarnings("unused")
    public void onEvent(PlaybackStoppingEvent event) {
        if (currentlyPlayed != null) {
            Map<String, String> data = new TreeMap<>();
            data.put(BOOK_PLAYED_TYPE_KEY,
                     currentlyPlayed.audioBook.isDemoSample()
                             ? BOOK_PLAYED_TYPE_SAMPLE
                             : BOOK_PLAYED_TYPE_USER_CONTENT);
            long elapsedTimeS = TimeUnit.NANOSECONDS.toSeconds(
                    System.nanoTime() - currentlyPlayed.startTimeNano);
            Map.Entry<Long, String> bucket = PLAYBACK_DURATION_BUCKETS.floorEntry(elapsedTimeS);
            data.put(BOOK_PLAYED_DURATION_KEY, Objects.requireNonNull(bucket).getValue());
            currentlyPlayed = null;
            stats.logEvent(BOOK_PLAYED, data);
        }
    }

    @SuppressWarnings("unused")
    public void onEvent(@NonNull PlaybackErrorEvent event) {
        Map<String, String> data = new TreeMap<>();
        data.put(PLAYBACK_ERROR_MESSAGE_KEY, event.errorMessage);
        data.put(PLAYBACK_ERROR_FORMAT_KEY, event.format);
        data.put(PLAYBACK_ERROR_DURATION_KEY, event.durationMs + "ms");
        data.put(PLAYBACK_ERROR_POSITION_KEY, event.positionMs + "ms");
        stats.logEvent(PLAYBACK_ERROR, data);
    }

    public void onBookSwiped() {
        stats.logEvent(BOOK_SWIPED);
    }

    public void onBookListDisplayed() {
        stats.logEvent(BOOK_LIST_DISPLAYED);
    }

    public void onFfRewindStarted(boolean isFf) {
        stats.logEvent(FF_REWIND, Collections.singletonMap(FF_REWIND_IS_FF_KEY, Boolean.toString(isFf)));
    }

    @SuppressWarnings("unused")
    public void onFfRewindFinished(long elapsedWallTimeMs) {
        stats.endTimedEvent(FF_REWIND);
    }

    public void onFfRewindAborted() {
        stats.logEvent(FF_REWIND_ABORTED);
    }

    public void onPermissionRationaleShown(String permissionRequest) {
        stats.logEvent(
                PERMISSION_RATIONALE_SHOWN,
                Collections.singletonMap(PERMISSION_RATIONALE_REQUEST_KEY, permissionRequest));
    }

    private static class CurrentlyPlayed {
        final AudioBook audioBook;
        final long startTimeNano;

        private CurrentlyPlayed(AudioBook audioBook, long startTimeNano) {
            this.audioBook = audioBook;
            this.startTimeNano = startTimeNano;
        }
    }

    static {
        PLAYBACK_DURATION_BUCKETS.put(0L, "0 - 30s");
        PLAYBACK_DURATION_BUCKETS.put(30L, "30 - 60s");
        PLAYBACK_DURATION_BUCKETS.put(60L, "1 - 5m");
        PLAYBACK_DURATION_BUCKETS.put(5 * 60L, "5 - 15m");
        PLAYBACK_DURATION_BUCKETS.put(15 * 60L, "15 - 30m");
        PLAYBACK_DURATION_BUCKETS.put(30 * 60L, "> 30m");
    }
}
