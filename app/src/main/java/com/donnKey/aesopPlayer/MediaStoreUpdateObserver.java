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
package com.donnKey.aesopPlayer;

import android.database.ContentObserver;
import android.os.Handler;

import com.donnKey.aesopPlayer.events.MediaStoreUpdateEvent;

import de.greenrobot.event.EventBus;

/**
 * Observe for changes to media files and post a MediaStoreUpdateEvent to the event bus to trigger
 * a scan for new audiobooks.
 *
 * The onChange method may be called a number of times as media files are being changed on the
 * device. To avoid rescanning often, a rescan is triggered only after RESCAN_DELAY_MS milliseconds
 * have passed since the last onChange call.
 */
class MediaStoreUpdateObserver extends ContentObserver {

    private static final int RESCAN_DELAY_MS = 5000;

    private final Handler mainThreadHandler;

    MediaStoreUpdateObserver(Handler mainThreadHandler) {
        super(mainThreadHandler);
        this.mainThreadHandler = mainThreadHandler;
    }

    @Override
    public void onChange(boolean selfChange) {
        mainThreadHandler.removeCallbacks(delayedRescanTask);
        mainThreadHandler.postDelayed(delayedRescanTask, RESCAN_DELAY_MS);
    }

    private final Runnable delayedRescanTask = () -> EventBus.getDefault().post(new MediaStoreUpdateEvent());
}
