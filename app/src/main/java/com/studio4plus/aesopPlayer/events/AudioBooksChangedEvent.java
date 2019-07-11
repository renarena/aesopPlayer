package com.studio4plus.aesopPlayer.events;

import com.studio4plus.aesopPlayer.model.LibraryContentType;

/**
 * Posted when audio books are added or removed.
 */
public class AudioBooksChangedEvent {

    public final LibraryContentType contentType;

    public AudioBooksChangedEvent(LibraryContentType contentType) {
        this.contentType = contentType;
    }
}
