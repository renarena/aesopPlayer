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
package com.donnKey.aesopPlayer.ui;

import android.os.Handler;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

public class FFRewindTimer implements Runnable {

    public interface Observer {
        void onTimerUpdated(long displayTimeMs);
        void onTimerLimitReached();
    }

    private static final int MIN_TICK_INTERVAL_MS = 50;

    private final Handler handler;
    private final List<Observer> observers = new ArrayList<>();
    private final long maxTimeMs;
    private long lastTickAt;
    private long displayTimeMs;
    private int speedMsPerS = 1000;

    public FFRewindTimer(Handler handler, long baseDisplayTimeMs, long maxTimeMs) {
        this.handler = handler;
        this.maxTimeMs = maxTimeMs;
        this.displayTimeMs = baseDisplayTimeMs;
        this.lastTickAt = SystemClock.uptimeMillis();
    }

    public void addObserver(Observer observer) {
        this.observers.add(observer);
    }

    public void removeObserver(Observer observer) {
        this.observers.remove(observer);
    }

    public long getDisplayTimeMs() {
        return displayTimeMs;
    }

    public void changeSpeed(int speedMsPerS) {
        stop();
        this.lastTickAt = SystemClock.uptimeMillis();
        this.speedMsPerS = speedMsPerS;
        run();
    }

    @Override
    public void run() {
        long now = SystemClock.uptimeMillis();
        boolean keepRunning = update(now);

        if (keepRunning) {
            long nextTickAt = lastTickAt + Math.max(Math.abs(speedMsPerS), MIN_TICK_INTERVAL_MS);
            handler.postAtTime(this, nextTickAt);
            lastTickAt = now;
        }
    }

    public void stop() {
        handler.removeCallbacks(this);
    }

    private boolean update(long now) {
        long elapsedMs = now - lastTickAt;
        displayTimeMs += (1000 * elapsedMs) / speedMsPerS;

        boolean limitReached = false;
        if (displayTimeMs < 0) {
            displayTimeMs = 0;
            limitReached = true;
        } else if (displayTimeMs > maxTimeMs) {
            displayTimeMs = maxTimeMs;
            limitReached = true;
        }

        int count = observers.size();
        for (int i = 0; i < count; ++i) {
            Observer observer = observers.get(i);
            observer.onTimerUpdated(displayTimeMs);
            if (limitReached)
                observer.onTimerLimitReached();
        }

        return !limitReached;
    }
}
