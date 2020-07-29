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

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.events.AnAudioBookChangedEvent;
import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.analytics.AnalyticsTracker;
import com.donnKey.aesopPlayer.events.AudioBooksChangedEvent;
import com.donnKey.aesopPlayer.events.PlaybackFatalErrorEvent;
import com.donnKey.aesopPlayer.events.PlaybackStoppedEvent;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.model.AudioBookManager;
import com.donnKey.aesopPlayer.service.PlaybackService;
import com.donnKey.aesopPlayer.service.DeviceMotionDetector;

import java.util.Objects;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

public class UiControllerMain implements ServiceConnection {

    private final @NonNull AppCompatActivity activity;
    private final @NonNull MainUi mainUi;
    private final @NonNull AudioBookManager audioBookManager;
    private final @NonNull EventBus eventBus;
    private final @NonNull AnalyticsTracker analyticsTracker;
    private final @NonNull UiControllerNoBooks.Factory noBooksControllerFactory;
    private final @NonNull UiControllerBookList.Factory bookListControllerFactory;
    private final @NonNull UiControllerPlayback.Factory playbackControllerFactory;
    private final @NonNull UiControllerInit.Factory initControllerFactory;
    private final @NonNull GlobalSettings globalSettings;

    private static final int PERMISSION_REQUEST_FOR_BOOK_SCAN = 1;
    private static final String TAG = "UiControllerMain";

    private static @Nullable PlaybackService playbackService;

    private State currentState;

    @Inject
    UiControllerMain(@NonNull AppCompatActivity activity,
                     @NonNull MainUi mainUi,
                     @NonNull AudioBookManager audioBookManager,
                     @NonNull EventBus eventBus,
                     @NonNull AnalyticsTracker analyticsTracker,
                     @NonNull UiControllerNoBooks.Factory noBooksControllerFactory,
                     @NonNull UiControllerBookList.Factory bookListControllerFactory,
                     @NonNull UiControllerPlayback.Factory playbackControllerFactory,
                     @NonNull UiControllerInit.Factory initControllerFactory,
                     @NonNull GlobalSettings globalSettings) {
        this.activity = activity;
        this.mainUi = mainUi;
        this.audioBookManager = audioBookManager;
        this.eventBus = eventBus;
        this.analyticsTracker = analyticsTracker;
        this.noBooksControllerFactory = noBooksControllerFactory;
        this.bookListControllerFactory = bookListControllerFactory;
        this.playbackControllerFactory = playbackControllerFactory;
        this.initControllerFactory = initControllerFactory;
        this.globalSettings = globalSettings;
    }

    void onActivityCreated() {
        eventBus.register(this);
        Intent serviceIntent = new Intent(activity, PlaybackService.class);
        activity.bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);

        currentState = new InitState(this);
    }

    void onActivityStart() {
        CrashWrapper.log(TAG,"UI: onActivityStart");
        if (!audioBookManager.isInitialized()) {
            scanAudioBookFiles();
        }
        maybeSetInitialState();
    }

    private static boolean justPaused; // set when we perform an action that will trigger a pause
                                       // event that we don't want a kiosk toast from in MainActivity
   boolean justDidPauseActionAndReset() {
       boolean result = justPaused;
       justPaused = false;
       return result;
   }

    void onActivityPause() {
        CrashWrapper.log(TAG, "UI: onActivityPause, state: " + currentState.stateId());
        currentState.onActivityPause();
    }

    void onActivityStop() {
        CrashWrapper.log(TAG,
                "UI: stopping in state " + currentState.stateId() + " (activity stop)");

        // Leave the FSM unchanged and let restart do everything
        DeviceMotionDetector.DetectUserInterest();
    }

    void onActivityDestroy() {
        playbackService = null;
        activity.unbindService(this);
        eventBus.unregister(this);
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void onEvent(@NonNull AudioBooksChangedEvent event) {
        if (event.contentType == null) {
           // nothing interesting happened
           return;
        }
        CrashWrapper.log(TAG, "UI: Event: AudioBooksChangedEvent, state: " + currentState.stateId());
        currentState.onBooksChanged(this);
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void onEvent(AnAudioBookChangedEvent event) {
        CrashWrapper.log(TAG, "UI: Event: AnAudioBookChangedEvent, state: " + currentState.stateId());
        currentState.onAnAudioBookChanged(event.book);
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void onEvent(PlaybackStoppedEvent event) {
        currentState.onPlaybackStop(this);
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void onEvent(@NonNull PlaybackFatalErrorEvent event) {
        mainUi.onPlaybackError(event.path);
    }

    void playCurrentAudiobook() {
        Preconditions.checkNotNull(currentAudioBook());
        changeState(StateFactory.PLAYBACK);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        CrashWrapper.log(TAG, "onServiceConnected");
        Preconditions.checkState(playbackService == null);
        playbackService = ((PlaybackService.ServiceBinder) service).getService();
        maybeSetInitialState();
    }

    @NonNull
    private GlobalSettings getGlobalSettings()
    {
        return globalSettings;
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        CrashWrapper.log(TAG, "onServiceDisconnected");
        playbackService = null;
    }

    void onRequestPermissionResult(
            int code, final @NonNull String[] permissions, final @NonNull int[] grantResults) {
        switch (code) {
            case PERMISSION_REQUEST_FOR_BOOK_SCAN:
                if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    scanAudioBookFiles();
                } else {
                    boolean canRetry =
                            ActivityCompat.shouldShowRequestPermissionRationale(
                                    activity, Manifest.permission.READ_EXTERNAL_STORAGE) ||
                                    ActivityCompat.shouldShowRequestPermissionRationale(
                                            activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    AlertDialog.Builder dialogBuilder = PermissionUtils.permissionRationaleDialogBuilder(
                            activity, R.string.permission_rationale_scan_audiobooks);
                    if (canRetry) {
                        dialogBuilder.setPositiveButton(
                                R.string.permission_rationale_try_again,
                                (dialogInterface, i) -> PermissionUtils.checkAndRequestPermission(
                                        activity, permissions, PERMISSION_REQUEST_FOR_BOOK_SCAN));
                    } else {
                        analyticsTracker.onPermissionRationaleShown("audiobooksScan");
                        dialogBuilder.setPositiveButton(
                                R.string.permission_rationale_settings,
                                (dialogInterface, i) -> PermissionUtils.openAppSettings(activity));
                    }
                    dialogBuilder.setNegativeButton(
                            R.string.permission_rationale_exit, (dialogInterface, i) -> activity.finish())
                            .create().show();
                }
                break;
            case UiControllerNoBooks.PERMISSION_REQUEST_DOWNLOADS:
                currentState.onRequestPermissionResult(code, grantResults);
                break;
        }
    }

    private void scanAudioBookFiles() {
        if (PermissionUtils.checkAndRequestPermission(
                activity,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE},
                PERMISSION_REQUEST_FOR_BOOK_SCAN))
            audioBookManager.scanFiles();
    }

    void computeDuration(AudioBook book) {
        // If the playback service isn't ready yet, just ignore it and we'll get it later.
        // (Seems to vary with device speed.)
        if (playbackService == null)
            return;

        playbackService.computeDuration(book);
    }

    private void proceedToPaused() {
        // Paused state requires that it's reached only with a prior Playback state.
        changeState(StateFactory.PLAYBACK);
        Objects.requireNonNull(State.playbackController).pauseForPause();
        changeState(StateFactory.PAUSED);
    }

    private void maybeSetInitialState() {
        if (playbackService == null)
        {
            // We're not set up enough yet (call from onActivityStart during cold startup).
            // We'll get another call when the playback service connects.
            return;
        }

        // Now look at the state.
        if (playbackService.getState() == PlaybackService.State.PLAYBACK) {
            changeState(StateFactory.PLAYBACK);
        }
        else if (playbackService.getState() == PlaybackService.State.PAUSED) {
            proceedToPaused();
        }
        else if (audioBookManager.isInitialized()) {
            // This will end up in one of the two books states
            changeState(StateFactory.BOOK_LIST);
        }
        else {
            Preconditions.checkState(currentState.stateId() == StateFactory.INIT_STATE);
            /* No state change yet: INIT_STATE will do it when the book list is ready */
        }
    }

    private boolean hasAnyBooks() {
        return !audioBookManager.getAudioBooks().isEmpty();
    }

    private AudioBook currentAudioBook() {
        return audioBookManager.getCurrentBook();
    }

    private void changeState(@NonNull StateFactory newStateFactory) {
        // Since this might be a new instance of the class, we have to do a
        // state change even when it's the same state so listeners are right.
        CrashWrapper.log(TAG, "UI: change state: " + currentState.stateId() + " to " + newStateFactory);
        currentState.onLeaveState();
        currentState = newStateFactory.create(this, currentState);

    }

    @NonNull
    private UiControllerBookList showBookList(boolean animate, boolean snooze) {
        analyticsTracker.onBookListDisplayed();
        BookListUi bookListUi = mainUi.switchToBookList(animate, snooze);
        return bookListControllerFactory.create(this, bookListUi);
    }

    @NonNull
    private UiControllerNoBooks showNoBooks(boolean animate) {
        NoBooksUi noBooksUi = mainUi.switchToNoBooks(animate);
        return noBooksControllerFactory.create(noBooksUi);
    }

    @SuppressWarnings("UnusedReturnValue")
    private UiControllerInit showInit() {
        InitUi initUi = mainUi.switchToInit();
        return initControllerFactory.create(initUi);
    }

    @NonNull
    private UiControllerPlayback showPlayback(boolean animate, boolean snooze) {
        Objects.requireNonNull(playbackService);
        PlaybackUi playbackUi = mainUi.switchToPlayback(animate, snooze );
        return playbackControllerFactory.create(playbackService, playbackUi);
    }

    private enum StateFactory {
        INIT_STATE {
            @Override
            State create(@NonNull UiControllerMain mainController, @NonNull State previousState) {
                // We'll leave here as soon as the book list is initialized
                return new InitState(mainController);
            }
        },
        NO_BOOKS {
            @Override
            State create(@NonNull UiControllerMain mainController, @NonNull State previousState) {
                return new NoBooksState(mainController, previousState);
            }
        },
        BOOK_LIST {
            @Override
            State create(@NonNull UiControllerMain mainController, @NonNull State previousState) {
                if (mainController.hasAnyBooks()) {
                    return new BookListState(mainController, previousState);
                }
                else {
                    CrashWrapper.log(TAG, "UI: ...BOOK_LIST forced to NO_BOOKS");
                    return new NoBooksState(mainController, previousState);
                }
            }
        },
        PLAYBACK {
            @Override
            State create(@NonNull UiControllerMain mainController, @NonNull State previousState) {
                return new PlaybackState(mainController, previousState);
            }
        },
        PAUSED {
            @Override
            State create(@NonNull UiControllerMain mainController, @NonNull State previousState) {
                return new PausedState(mainController, previousState);
            }
        };

        abstract State create(
                @NonNull UiControllerMain mainController, @NonNull State previousState);
    }

    private static abstract class State {
        abstract void onLeaveState();
        static @Nullable UiControllerPlayback playbackController;
        static @Nullable AudioBook playingAudioBook;

        void onPlaybackStop(@NonNull UiControllerMain mainController) {
            //noinspection ConstantConditions - getting here is an error
            Preconditions.checkState(false);
        }

        void onBooksChanged(@NonNull UiControllerMain mainController) {
            //noinspection ConstantConditions - getting here is an error
            Preconditions.checkState(false);
        }

        void onActivityPause() { }

        void onRequestPermissionResult(int code, @NonNull int[] grantResults) {
            //noinspection ConstantConditions - getting here is an error
            Preconditions.checkState(false);
        }

        void onAnAudioBookChanged(AudioBook book) { }

        abstract StateFactory stateId();
    }

    private static class InitState extends State {

        InitState (@NonNull UiControllerMain mainController) {
            mainController.showInit();
        }

        @Override
        void onPlaybackStop(@NonNull UiControllerMain mainController) { }

        // Put up the right initial window once we've figured out whether there are
        // any books at all.  Because this can be asynchronously delayed due to the amount
        // of work, and because there are some system-initiated start/stop pairs before we're
        // set up, this happens here when it's really ready.
        @Override
        void onBooksChanged(@NonNull UiControllerMain mainController) {
            mainController.changeState(StateFactory.BOOK_LIST);
        }

        @Override
        void onLeaveState() { }

        @Override
        StateFactory stateId() { return StateFactory.INIT_STATE; }
    }

    private static class NoBooksState extends State {
        private @NonNull final UiControllerNoBooks noBooksController;

        NoBooksState(@NonNull UiControllerMain mainController, @NonNull State previousState) {
            noBooksController = mainController.showNoBooks(!(previousState instanceof InitState));
        }

        @Override
        public void onLeaveState() {
            noBooksController.shutdown();
        }

        @Override
        public void onBooksChanged(@NonNull UiControllerMain mainController) {
            mainController.changeState(StateFactory.BOOK_LIST);
        }

        @Override
        void onRequestPermissionResult(int code, @NonNull int[] grantResults) {
            noBooksController.onRequestPermissionResult(code, grantResults);
        }

        @Override
        StateFactory stateId() { return StateFactory.NO_BOOKS; }
    }

    private static class BookListState extends State
            implements DeviceMotionDetector.Listener {
        private @NonNull final UiControllerBookList bookListController;
        private @NonNull final DeviceMotionDetector motionDetector;

        BookListState(@NonNull UiControllerMain mainController, @NonNull State previousState) {
            bookListController = mainController.showBookList(
                    !(previousState instanceof InitState) && !(previousState instanceof BookListState),
                    !(previousState instanceof BookListState));
            motionDetector = DeviceMotionDetector.getDeviceMotionDetector(this);
            // Motion detector doesn't get enabled here until we can give it a meaning.

            // We can get a free onPause during this transition
            justPaused = true;
        }

        @Override
        void onLeaveState() {
            motionDetector.disable();
            bookListController.shutdown();
        }

        @Override
        void onBooksChanged(@NonNull UiControllerMain mainController) {
            motionDetector.disable();
            mainController.changeState(StateFactory.BOOK_LIST);
        }

        @Override
        void onAnAudioBookChanged(AudioBook book) {
            // This could focus on just one book if that became useful,
            // but shotgunning it works for now.
            bookListController.updateAudioBooks();
        }

        @SuppressWarnings("EmptyMethod")
        @Override
        public void onFaceDownStill() { /* ignore */ }

        @SuppressWarnings("EmptyMethod")
        @Override
        public void onFaceUpStill() { /* ignore*/ }

        @SuppressWarnings("EmptyMethod")
        @Override
        public void onSignificantMotion() { /* ignore */ }

        @SuppressWarnings("SameReturnValue")
        @Override
        StateFactory stateId() { return StateFactory.BOOK_LIST; }
    }

    private static class PlaybackState extends State
            implements DeviceMotionDetector.Listener {
        private @NonNull final UiControllerMain mainController;
        private @NonNull final DeviceMotionDetector motionDetector;

        PlaybackState(@NonNull UiControllerMain mc, @NonNull State previousState) {
            mainController = mc;
            if (!(previousState instanceof PausedState))
            {
                // If was paused, this is all already set up
                // ...previousState could be playback when awakening from stop
                playbackController = mainController.showPlayback(
                        !(previousState instanceof PlaybackState), !(previousState instanceof PlaybackState));
                Preconditions.checkNotNull(playbackController);
                playingAudioBook = mainController.currentAudioBook();
                playbackController.startPlayback(playingAudioBook);
            }
            Objects.requireNonNull(playbackController);
            Objects.requireNonNull(playingAudioBook);
            motionDetector = DeviceMotionDetector.getDeviceMotionDetector(this);
            motionDetector.enable();
        }

        @Override
        void onActivityPause() {
            Objects.requireNonNull(playbackController);
            playbackController.stopRewindIfActive();
        }

        @Override
        void onLeaveState() {
            justPaused = true;
            motionDetector.disable();
            Objects.requireNonNull(playbackController);
            playbackController.shutdown();
        }

        @Override
        void onPlaybackStop(@NonNull UiControllerMain mainController) {
            motionDetector.disable();
            mainController.changeState(StateFactory.BOOK_LIST);
        }

        @Override
        void onBooksChanged(@NonNull UiControllerMain mainController) {
            Objects.requireNonNull(playbackController);
            if (playingAudioBook != null &&
                    playingAudioBook != mainController.currentAudioBook()) {
                // This will cause a state change to BOOK_LIST (see just above)
                playbackController.stopPlayback();
                playingAudioBook = null;
            }
        }

        @Override
        void onAnAudioBookChanged(AudioBook book) {
            if (playingAudioBook == book) {
                // If the book that just got updated is the current book,
                // refresh the screen with the new data
                mainController.changeState(StateFactory.PLAYBACK);
            }
        }

        @Override
        public void onFaceDownStill() {
            Objects.requireNonNull(playbackController);
            switch (mainController.getGlobalSettings().getStopOnFaceDown()) {
            case NONE:
                break;
            case STOP_ONLY:
                motionDetector.disable();
                playbackController.stopPlayback();
                mainController.changeState(StateFactory.BOOK_LIST);
                break;
            case STOP_RESUME:
                motionDetector.disable();
                playbackController.pauseForPause();
                mainController.changeState(StateFactory.PAUSED);
                break;
            }
        }

        @Override
        public void onFaceUpStill() {
            /* ignore */
        }

        @Override
        public void onSignificantMotion() {
            Objects.requireNonNull(playbackController);
            playbackController.playbackService.resetSleepTimer();
        }

        @Override
        StateFactory stateId() { return StateFactory.PLAYBACK; }
    }


    private static class PausedState extends State
            implements DeviceMotionDetector.Listener {
        private @NonNull final UiControllerMain mainController;
        private @NonNull final DeviceMotionDetector motionDetector;

        PausedState(@NonNull UiControllerMain mainController, @SuppressWarnings("unused") @NonNull State previousState) {
            this.mainController = mainController;
            // We're using the FSM global playbackController

            motionDetector = DeviceMotionDetector.getDeviceMotionDetector(this);
            motionDetector.enable();
        }

        @Override
        void onLeaveState() {
            motionDetector.disable();
        }

        @Override
        void onPlaybackStop(@NonNull UiControllerMain mainController) {
            // If books changed, we might stop and come here (this is Paused).
            mainController.changeState(StateFactory.BOOK_LIST);
        }

        @Override
        void onBooksChanged(@NonNull UiControllerMain mainController) {
            Objects.requireNonNull(playbackController);
            if (playingAudioBook != null &&
                    playingAudioBook != mainController.currentAudioBook()) {
                // This will cause a state change
                playbackController.stopPlayback();
                playingAudioBook = null;
            }
        }

        @Override
        void onAnAudioBookChanged(AudioBook book) {
            if (playingAudioBook == book) {
                // If the book that just got updated is the current book,
                // refresh the screen with the new data
                mainController.changeState(StateFactory.PAUSED);
            }
        }

        @SuppressWarnings("EmptyMethod")
        @Override
        public void onFaceDownStill() {
            /* nothing */
        }

        @Override
        public void onFaceUpStill() {
            Objects.requireNonNull(playbackController);
            // We only get in this state when it's STOP_RESUME
            playbackController.resumeFromPause();
            playbackController.playbackService.resetSleepTimer();
            mainController.changeState(StateFactory.PLAYBACK);
        }

        @Override
        public void onSignificantMotion() {
            Objects.requireNonNull(playbackController);
            playbackController.playbackService.resetSleepTimer();
        }

        @SuppressWarnings("SameReturnValue")
        @Override
        StateFactory stateId() { return StateFactory.PAUSED; }
    }

    @Nullable
    public static PlaybackService getPlaybackService()
    {
        return playbackService;
    }
}
