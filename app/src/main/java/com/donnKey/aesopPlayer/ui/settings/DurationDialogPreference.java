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
package com.donnKey.aesopPlayer.ui.settings;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

class DurationDialogPreference extends DialogPreference
{
    private long durationMs;

    public long getValue()
    {
        return durationMs;
    }

    public void setValue(long value) {
        durationMs = value;
    }

    interface OnNewValueListener {
        void onNewValue(long newValue);
    }

    @Nullable
    private OnNewValueListener listener;

    @TargetApi(21)
    public DurationDialogPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public DurationDialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DurationDialogPreference(Context context, AttributeSet attrs) {
        // This doesn't do what you'd expect (just chain) but rather fills in the 3d and 4th
        // parameters to match the default Preference style.
        super(context, attrs);
    }

    public DurationDialogPreference(Context context) {
        super(context);
    }

    void setOnNewValueListener(OnNewValueListener listener) {
        this.listener = listener;
    }

    void onDialogClosed(boolean positive) {
        if (positive && listener != null)
            listener.onNewValue(getValue());
    }
}
