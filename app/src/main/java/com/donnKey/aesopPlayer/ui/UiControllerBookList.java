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
import androidx.annotation.NonNull;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.model.AudioBookManager;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class UiControllerBookList {

    public static class Factory {
        private final @NonNull Context context;
        private final @NonNull AudioBookManager audioBookManager;
        private final @NonNull SpeakerProvider speakerProvider;

        @Inject
        public Factory(@NonNull Context context,
                       @NonNull AudioBookManager audioBookManager,
                       @NonNull SpeakerProvider speakerProvider) {
            this.context = context;
            this.audioBookManager = audioBookManager;
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

        AesopPlayerApplication.getComponent(AesopPlayerApplication.getAppContext()).inject(this);
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

    @Inject
    public GlobalSettings globalSettings;

    public void changeBook(@NonNull String bookId) {
        audioBookManager.setCurrentBook(bookId);
        AudioBook book = audioBookManager.getById(bookId);
        Preconditions.checkNotNull(book);
        Preconditions.checkNotNull(uiControllerMain);

        long now = System.currentTimeMillis();

        // If the book actually changed, always say it.
        // Except in maintenance mode, so it doesn't chatter when being remotely maintained.
        if (!previousBook.equals(bookId)) {
            if (!globalSettings.isMaintenanceMode()) {
                speak(book.getDisplayTitle());
            }
            previousBook = bookId;
        }
        // Otherwise, don't do it very often (This is policy that might change)
        else if (now - lastTitleAnnouncedAt > MINIMUM_INTERVAL_BETWEEN_TITLES) {
            if (!globalSettings.isMaintenanceMode()) {
                speak(book.getDisplayTitle());
            }
        }
        lastTitleAnnouncedAt = now;

        uiControllerMain.computeDuration(book);
    }

    static public void suppressAnnounce() {
        lastTitleAnnouncedAt = System.currentTimeMillis();
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
