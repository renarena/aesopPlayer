package com.studio4plus.homerplayer.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.analytics.AnalyticsTracker;
import com.studio4plus.homerplayer.events.AudioBooksChangedEvent;
import com.studio4plus.homerplayer.events.PlaybackFatalErrorEvent;
import com.studio4plus.homerplayer.events.PlaybackStoppedEvent;
import com.studio4plus.homerplayer.model.AudioBook;
import com.studio4plus.homerplayer.model.AudioBookManager;
import com.studio4plus.homerplayer.service.PlaybackService;
import com.studio4plus.homerplayer.service.DeviceMotionDetector;

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
    private final @NonNull GlobalSettings globalSettings;

    private static final int PERMISSION_REQUEST_FOR_BOOK_SCAN = 1;
    public static final int PERMISSION_REQUEST_FOR_SIMPLE_KIOSK = 2;
    private static final String TAG = "UiControllerMain";

    private @Nullable PlaybackService playbackService;

    private @NonNull State currentState = new InitState();

    @Inject
    UiControllerMain(@NonNull AppCompatActivity activity,
                     @NonNull MainUi mainUi,
                     @NonNull AudioBookManager audioBookManager,
                     @NonNull EventBus eventBus,
                     @NonNull AnalyticsTracker analyticsTracker,
                     @NonNull UiControllerNoBooks.Factory noBooksControllerFactory,
                     @NonNull UiControllerBookList.Factory bookListControllerFactory,
                     @NonNull UiControllerPlayback.Factory playbackControllerFactory,
                     @NonNull GlobalSettings globalSettings) {
        this.activity = activity;
        this.mainUi = mainUi;
        this.audioBookManager = audioBookManager;
        this.eventBus = eventBus;
        this.analyticsTracker = analyticsTracker;
        this.noBooksControllerFactory = noBooksControllerFactory;
        this.bookListControllerFactory = bookListControllerFactory;
        this.playbackControllerFactory = playbackControllerFactory;
        this.globalSettings = globalSettings;
    }

    void onActivityCreated() {
        eventBus.register(this);
        Intent serviceIntent = new Intent(activity, PlaybackService.class);
        activity.bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);

        DeviceMotionDetector.initDeviceMotionDetector(activity);
    }

    void onActivityStart() {
        Crashlytics.log(Log.DEBUG, TAG,"UI: onActivityStart");
            scanAudioBookFiles();
        maybeSetInitialState();
    }

    private static boolean justPaused; // set when we perform an action that will trigger a pause
                                       // event that we don't want a kiosk toast from in MainActivity
    public boolean justDidPauseActionAndReset() {
        boolean result = justPaused;
        justPaused = false;
        return result;
    }

    void onActivityPause() {
        Crashlytics.log(Log.DEBUG, TAG, "UI: onActivityPause, state: " + currentState.stateId());
        currentState.onActivityPause();
    }

    void onActivityStop() {
        Crashlytics.log(Log.DEBUG, TAG,
                "UI: stopping in state " + currentState.stateId() + " (activity stop)");

        StateFactory priorState = currentState.stateId();
        changeState(StateFactory.INIT_STATE);
        if (priorState != StateFactory.INIT_STATE) {
            DeviceMotionDetector.DetectUserInterest();
        }
    }

    void onActivityDestroy() {
        activity.unbindService(this);
        eventBus.unregister(this);
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void onEvent(AudioBooksChangedEvent event) {
        currentState.onBooksChanged(this);
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void onEvent(PlaybackStoppedEvent event) {
        currentState.onPlaybackStop(this);
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void onEvent(PlaybackFatalErrorEvent event) {
        mainUi.onPlaybackError(event.path);
    }

    void playCurrentAudiobook() {
        Preconditions.checkNotNull(currentAudioBook());
        changeState(StateFactory.PLAYBACK);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Crashlytics.log(Log.DEBUG, TAG, "onServiceConnected");
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
        Crashlytics.log(Log.DEBUG, TAG, "onServiceDisconnected");
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
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        PermissionUtils.checkAndRequestPermission(
                                                activity, permissions, PERMISSION_REQUEST_FOR_BOOK_SCAN);
                                    }
                                });
                    } else {
                        analyticsTracker.onPermissionRationaleShown("audiobooksScan");
                        dialogBuilder.setPositiveButton(
                                R.string.permission_rationale_settings,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        PermissionUtils.openAppSettings(activity);
                                    }
                                });
                    }
                    dialogBuilder.setNegativeButton(
                            R.string.permission_rationale_exit, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    activity.finish();
                                }
                            })
                            .create().show();
                }
                break;
            case UiControllerNoBooks.PERMISSION_REQUEST_DOWNLOADS:
                currentState.onRequestPermissionResult(code, grantResults);
                break;
            case PERMISSION_REQUEST_FOR_SIMPLE_KIOSK:
                // for now, nothing
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

    private void maybeSetInitialState() {
        if (playbackService != null)
        {
            // Already playing somehow (likely some forced restart)
            if (playbackService.getState() == PlaybackService.State.PLAYBACK) {
                changeState(StateFactory.PLAYBACK);
            }
            else if (playbackService.getState() == PlaybackService.State.PAUSED) {
                changeState(StateFactory.PAUSED);
            }
            else {
                changeState(StateFactory.INIT_STATE);
            }
        }
        else if (hasAnyBooks()) {
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

    private void changeState(StateFactory newStateFactory) {
        Crashlytics.log(Log.DEBUG, TAG, "UI: leave state: " + currentState.stateId() + " to " + newStateFactory);
        currentState.onLeaveState();
        currentState = newStateFactory.create(this, currentState);

    }

    @NonNull
    private UiControllerBookList showBookList(boolean animate) {
        analyticsTracker.onBookListDisplayed();
        BookListUi bookListUi = mainUi.switchToBookList(animate);
        return bookListControllerFactory.create(this, bookListUi);
    }

    @NonNull
    private UiControllerNoBooks showNoBooks(boolean animate) {
        NoBooksUi noBooksUi = mainUi.switchToNoBooks(animate);
        return noBooksControllerFactory.create(noBooksUi);
    }

    @NonNull
    private UiControllerPlayback showPlayback(boolean animate) {
        Preconditions.checkNotNull(playbackService);
        PlaybackUi playbackUi = mainUi.switchToPlayback(animate);
        return playbackControllerFactory.create(playbackService, playbackUi);
    }

    private enum StateFactory {
        INIT_STATE {
            @Override
            State create(@NonNull UiControllerMain mainController, @NonNull State previousState) {
                // We'll leave here as soon as the book list is initialized
                return new InitState();
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

        abstract StateFactory stateId();
    }

    private static class InitState extends State {

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
        private @NonNull final State prevState;

        BookListState(@NonNull UiControllerMain mainController, @NonNull State previousState) {
            UiUtil.SnoozeDisplay.resume();
            prevState = previousState;
            bookListController = mainController.showBookList(
                    !(previousState instanceof InitState) && !(previousState instanceof BookListState));
            motionDetector = DeviceMotionDetector.getDeviceMotionDetector(this);

            // We get a free onPause during this transition
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
        public void onFaceDownStill() { /* ignore */ }

        @Override
        public void onFaceUpStill() {
            motionDetector.disable();
            if (prevState instanceof PlaybackState) {
                bookListController.playCurrentAudiobook();
            }
        }

        @Override
        public void onSignificantMotion() { /* ignore */ }

        @Override
        StateFactory stateId() { return StateFactory.BOOK_LIST; }
    }

    private static class PlaybackState extends State
            implements DeviceMotionDetector.Listener {
        private @NonNull final UiControllerMain mainController;
        private @NonNull final DeviceMotionDetector motionDetector;

        PlaybackState(@NonNull UiControllerMain mc, @NonNull State previousState) {
            UiUtil.SnoozeDisplay.resume();
            mainController = mc;
            if (!(previousState instanceof PausedState))
            {
                // If was paused, this is all already set up
                // ...previousState could be playback when awakening from stop
                playbackController = mainController.showPlayback(
                        !(previousState instanceof PlaybackState));
                Preconditions.checkNotNull(playbackController);
                playingAudioBook = mainController.currentAudioBook();
                playbackController.startPlayback(playingAudioBook);
            }
            Preconditions.checkNotNull(playbackController);
            Preconditions.checkNotNull(playingAudioBook);
            motionDetector = DeviceMotionDetector.getDeviceMotionDetector(this);
            motionDetector.enable();
        }

        @Override
        void onActivityPause() {
            Preconditions.checkNotNull(playbackController);
            playbackController.stopRewindIfActive();
        }

        @Override
        void onLeaveState() {
            justPaused = true;
            motionDetector.disable();
            Preconditions.checkNotNull(playbackController);
            playbackController.shutdown();
        }

        @Override
        void onPlaybackStop(@NonNull UiControllerMain mainController) {
            motionDetector.disable();
            mainController.changeState(StateFactory.BOOK_LIST);
        }

        @Override
        void onBooksChanged(@NonNull UiControllerMain mainController) {
            Preconditions.checkNotNull(playbackController);
            if (playingAudioBook != null &&
                    playingAudioBook != mainController.currentAudioBook()) {
                // This will cause a state change
                playbackController.stopPlayback();
                playingAudioBook = null;
            }
        }

        @Override
        public void onFaceDownStill() {
            Preconditions.checkNotNull(playbackController);
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
            Preconditions.checkNotNull(playbackController);
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
            UiUtil.SnoozeDisplay.resume();
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
            mainController.changeState(StateFactory.BOOK_LIST);
        }

        @Override
        void onBooksChanged(@NonNull UiControllerMain mainController) {
            Preconditions.checkNotNull(playbackController);
            if (playingAudioBook != null &&
                    playingAudioBook != mainController.currentAudioBook()) {
                // This will cause a state change
                playbackController.stopPlayback();
                playingAudioBook = null;
            }
        }

        @Override
        public void onFaceDownStill() {
            /* nothing */
        }

        @Override
        public void onFaceUpStill() {
            Preconditions.checkNotNull(playbackController);
            // We only get in this state when it's STOP_RESUME
            playbackController.resumeFromPause();
            playbackController.playbackService.resetSleepTimer();
            mainController.changeState(StateFactory.PLAYBACK);
        }

        @Override
        public void onSignificantMotion() {
            Preconditions.checkNotNull(playbackController);
            playbackController.playbackService.resetSleepTimer();
        }

        @Override
        StateFactory stateId() { return StateFactory.PAUSED; }
    }
}
