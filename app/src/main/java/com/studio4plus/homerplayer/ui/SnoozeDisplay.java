package com.studio4plus.homerplayer.ui;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.os.CountDownTimer;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.R;

@SuppressWarnings("deprecation") // of Fragment
public class SnoozeDisplay {
    private View snoozeOverlay;
    private TextView snoozeCounter;
    @SuppressWarnings("unused")
    private SnoozeDisplay snoozeForHolder; // Used for lifetime management

    @SuppressLint("ClickableViewAccessibility")
    private void SnoozeDisplayFor(
            final Fragment fragment,
            View view,
            int time) {

        if (time <= 0)
            return;

        snoozeForHolder = this;

        snoozeOverlay = view.findViewById(R.id.snoozeOverlay);
        Preconditions.checkNotNull(snoozeOverlay);

        snoozeCounter = view.findViewById(R.id.snoozeCounter);
        Preconditions.checkNotNull(snoozeCounter);

        snoozeCounter.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Don't let any events "through" the overlay.
                return true;
            }
        });
        snoozeOverlay.setVisibility(View.VISIBLE);

        new CountDownTimer((time + 1) * 1000, 1000) // Wait time secs, tick every 1 sec
        {
            @Override
            public final void onTick(final long millisUntilFinished) {
                long secsRemaining = millisUntilFinished / 1000;
                if (secsRemaining <= 1) {
                    snoozeCounter.setText("");
                }
                else {
                    snoozeCounter.setText(fragment.getString(R.string.snooze_seconds, secsRemaining));
                }
            }

            @Override
            public final void onFinish() {
                snoozeOverlay.setVisibility(View.GONE);
                snoozeOverlay = null;
                snoozeCounter = null;
                snoozeForHolder = null;
            }
        }.start();
    }

    public SnoozeDisplay(Fragment fragment, View view, int time) {
        SnoozeDisplayFor(fragment, view, time);
    }
}
