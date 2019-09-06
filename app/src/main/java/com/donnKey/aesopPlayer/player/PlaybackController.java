/**
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
package com.donnKey.aesopPlayer.player;

import java.io.File;

public interface PlaybackController {

    interface Observer {
        void onDuration(File file, long durationMs);

        /**
         * Playback position progressed. Called more or less once per second of playback in media
         * time (i.e. affected by the playback speed).
         */
        void onPlaybackProgressed(long currentPositionMs);

        /**
         * Playback ended because it reached the end of track
         */
        void onPlaybackEnded();

        /**
         * Playback stopped on request.
         */
        void onPlaybackStopped(long currentPositionMs);

        /**
         * Error playing file.
         * @param path File with error
         */
        void onPlaybackError(File path);

        /**
         * The player has been released.
         */
        void onPlayerReleased();
    }

    void setObserver(Observer observer);
    void start(File file, long positionPosition);
    void pause();
    void resume(File file, long positionPosition);
    void stop();
    void release();
    long getCurrentPosition();
}
