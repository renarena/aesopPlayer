package com.studio4plus.aesopPlayer.ui.settings;

import android.annotation.TargetApi;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import android.util.AttributeSet;

import com.studio4plus.aesopPlayer.R;

class ConfirmDialogPreference extends DialogPreference {

    public interface OnConfirmListener {
        void onConfirmed();
    }

    @Nullable
    private OnConfirmListener listener;

    @TargetApi(21)
    public ConfirmDialogPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ConfirmDialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ConfirmDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ConfirmDialogPreference(Context context) {
        super(context);
    }

    @Override
    public int getDialogLayoutResource() {
        return R.layout.preference_dialog_confirm;
    }

    void setOnConfirmListener(OnConfirmListener listener) {
        this.listener = listener;
    }

    void onDialogClosed(boolean positive) {
        if (positive && listener != null)
            listener.onConfirmed();
    }
}
