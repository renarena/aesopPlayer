package com.donnKey.aesopPlayer.ui;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;

public class PressReleaseDetector implements View.OnTouchListener {

    public interface Listener {
        void onPressed(View v, float x, float y);
        void onReleased(View v, float x, float y);
    }

    private final Listener listener;
    private boolean isPressed;

    public PressReleaseDetector(@NonNull Listener listener) {
        this.listener = listener;
    }

    @SuppressLint("ClickableViewAccessibility") // This is press-and-hold, so click not meaningful
    // TODO: can press-and-hold be made accessible?
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            isPressed = true;
            listener.onPressed(v, event.getX(), event.getY());
            return true;
        } else if (isPressed && event.getAction() == MotionEvent.ACTION_UP) {
            listener.onReleased(v, event.getX(), event.getY());
            return true;
        }
        return false;
    }
}
