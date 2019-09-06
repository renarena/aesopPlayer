/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2019 Donn S. Terry
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.donnKey.aesopPlayer.analytics.AnalyticsTracker;
import javax.inject.Inject;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class UiControllerInit {

    public static class Factory {
        private final @NonNull AppCompatActivity activity;
        private final @NonNull AnalyticsTracker analyticsTracker;

        @Inject
        public Factory(@NonNull AppCompatActivity activity,
                       @NonNull AnalyticsTracker analyticsTracker) {
            this.activity = activity;
            this.analyticsTracker = analyticsTracker;
        }

        public UiControllerInit create(@NonNull InitUi ui) {
            return new UiControllerInit(activity, ui, analyticsTracker);
        }
    }

    private final @NonNull AppCompatActivity activity;
    private final @NonNull InitUi ui;
    private final @NonNull AnalyticsTracker analyticsTracker;

    private UiControllerInit(@NonNull AppCompatActivity activity,
                                @NonNull InitUi ui,
                                @NonNull AnalyticsTracker analyticsTracker) {
        this.activity = activity;
        this.ui = ui;
        this.analyticsTracker = analyticsTracker;

        ui.initWithController(this);
    }
}
