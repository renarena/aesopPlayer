package com.donnKey.aesopPlayer.ui;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * The main UI part that handles switching between the main states: no books, list of books,
 * book playback.
 */
public interface MainUi {

    @NonNull BookListUi switchToBookList(boolean animate);
    @NonNull NoBooksUi switchToNoBooks(boolean animate);
    @NonNull PlaybackUi switchToPlayback(boolean animate);
    @NonNull InitUi switchToInit();
    void onPlaybackError(File path);
}
