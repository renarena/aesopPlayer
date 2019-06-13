package com.studio4plus.homerplayer.ui.settings;

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
