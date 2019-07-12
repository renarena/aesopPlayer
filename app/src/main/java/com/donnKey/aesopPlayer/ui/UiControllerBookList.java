package com.donnKey.aesopPlayer.ui;

import android.content.Context;
import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.events.AudioBooksChangedEvent;
import com.donnKey.aesopPlayer.events.CurrentBookChangedEvent;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.model.AudioBookManager;

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
                    context, audioBookManager, speakerProvider, uiControllerMain, ui);
        }
    }

    private final @NonNull AudioBookManager audioBookManager;
    private final @NonNull UiControllerMain uiControllerMain;
    private final @NonNull BookListUi ui;

    private final @NonNull Speaker speaker;

    private UiControllerBookList(@NonNull Context context,
                                 @NonNull AudioBookManager audioBookManager,
                                 @NonNull SpeakerProvider speakerProvider,
                                 @NonNull UiControllerMain uiControllerMain,
                                 @NonNull BookListUi ui) {
        this.audioBookManager = audioBookManager;
        this.uiControllerMain = uiControllerMain;
        this.ui = ui;

        speaker = Speaker.get(context, speakerProvider);

        ui.initWithController(this);
        updateAudioBooks();
    }

    @SuppressWarnings("EmptyMethod")
    public void shutdown() { }

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
