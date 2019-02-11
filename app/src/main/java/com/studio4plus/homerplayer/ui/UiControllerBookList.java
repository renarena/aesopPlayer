package com.studio4plus.homerplayer.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.events.AudioBooksChangedEvent;
import com.studio4plus.homerplayer.events.CurrentBookChangedEvent;
import com.studio4plus.homerplayer.model.AudioBook;
import com.studio4plus.homerplayer.model.AudioBookManager;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

public class UiControllerBookList {

    public static class Factory {
        private final @NonNull Context context;
        private final @NonNull AudioBookManager audioBookManager;
        private final @NonNull EventBus eventBus;
        private final @NonNull SpeakerProvider speakerProvider;

        @Inject
        public Factory(@NonNull Context context,
                       @NonNull AudioBookManager audioBookManager,
                       @NonNull EventBus eventBus,
                       @NonNull SpeakerProvider speakerProvider) {
            this.context = context;
            this.audioBookManager = audioBookManager;
            this.eventBus = eventBus;
            this.speakerProvider = speakerProvider;
        }

        @NonNull
        public UiControllerBookList create(
                @NonNull UiControllerMain uiControllerMain, @NonNull BookListUi ui) {
            return new UiControllerBookList(
                    context, audioBookManager, speakerProvider, eventBus, uiControllerMain, ui);
        }
    }

    //private final @NonNull Context context;
    private final @NonNull AudioBookManager audioBookManager;
    //private final @NonNull EventBus eventBus;
    private final @NonNull UiControllerMain uiControllerMain;
    private final @NonNull BookListUi ui;

    private final @NonNull Speaker speaker;

    private UiControllerBookList(@NonNull Context context,
                                 @NonNull AudioBookManager audioBookManager,
                                 @NonNull SpeakerProvider speakerProvider,
                                 @SuppressWarnings("unused") @NonNull EventBus eventBus,
                                 @NonNull UiControllerMain uiControllerMain,
                                 @NonNull BookListUi ui) {
        //this.context = context;
        this.audioBookManager = audioBookManager;
        //this.eventBus = eventBus;
        this.uiControllerMain = uiControllerMain;
        this.ui = ui;

        speaker = Speaker.get(context, speakerProvider);

        ui.initWithController(this);
        updateAudioBooks();

        // We don't need the below because onBookChanged is called during activity startup
        // which occurs when screen-on happens. Left "just in case".
        // When screen-on, announce the book title
        //context.registerReceiver(screenOnReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));

        //We're not using the event bus, but we might?
        //eventBus.register(this);
    }

    // A callback for screen-on
    private static boolean onScreenAnnounced;
    private final @NonNull BroadcastReceiver screenOnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (onScreenAnnounced) {
                // The system gives us a bunch of these if we get any; we only want to announce the title once
                return;
            }
            onScreenAnnounced = true;
            final AudioBook currentBook = audioBookManager.getCurrentBook();
            // The onReceive call is posted from another thread and there may be no books
            // by the time it is executed.
            if (currentBook != null) {
                speak(currentBook.getTitle());
            }
            Handler handler = new Handler();
            handler.postDelayed(()->onScreenAnnounced=false, 2000);
        }
    };

    @SuppressWarnings("EmptyMethod")
    public void shutdown() {
        /* ignore */
        //eventBus.unregister(this);

        //context.unregisterReceiver(screenOnReceiver);
    }

    public void playCurrentAudiobook() {
        uiControllerMain.playCurrentAudiobook();
    }

    private static long lastTitleAnnouncedAt = 0;
    private static String previousBook = "";
    private final static long MINIMUM_INTERVAL_BETWEEN_TITLES = TimeUnit.SECONDS.toMillis(30);

    public void changeBook(@NonNull String bookId) {
        audioBookManager.setCurrentBook(bookId);
        AudioBook book = audioBookManager.getById(bookId);
        Preconditions.checkNotNull(book);
        Preconditions.checkNotNull(uiControllerMain);

        long now = System.currentTimeMillis();

        // If the book actually changed, always say it.
        if (!previousBook.equals(bookId)) {
            speak(book.getTitle());
            previousBook = bookId;
        }
        // Otherwise, don't do it very often (This is policy that might change)
        else if (now - lastTitleAnnouncedAt > MINIMUM_INTERVAL_BETWEEN_TITLES) {
            speak(book.getTitle());
        }
        lastTitleAnnouncedAt = now;
        uiControllerMain.computeDuration(book);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void onEvent(AudioBooksChangedEvent event) {
        updateAudioBooks();
    }

    @SuppressWarnings("UnusedDeclaration")
    public void onEvent(CurrentBookChangedEvent event) {
        ui.updateCurrentBook(audioBookManager.getCurrentBookIndex());
    }

    private void speak(@NonNull String text) {
        speaker.speak(text);
    }

    public void updateAudioBooks() {
        ui.updateBookList(
                audioBookManager.getAudioBooks(),
                audioBookManager.getCurrentBookIndex());
    }
}
