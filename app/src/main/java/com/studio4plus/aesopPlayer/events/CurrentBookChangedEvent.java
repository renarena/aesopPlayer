package com.studio4plus.aesopPlayer.events;

import com.studio4plus.aesopPlayer.model.AudioBook;

/**
 * Sent when the current audio book is changed in the AudioBookManager.
 */
public class CurrentBookChangedEvent {
    public final AudioBook audioBook;

    public CurrentBookChangedEvent(AudioBook audioBook) {
        this.audioBook = audioBook;
    }
}
