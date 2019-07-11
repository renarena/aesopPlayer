package com.studio4plus.aesopPlayer.events;

import java.io.File;

public class PlaybackFatalErrorEvent {
    public final File path;

    public PlaybackFatalErrorEvent(File path) {
        this.path = path;
    }
}
