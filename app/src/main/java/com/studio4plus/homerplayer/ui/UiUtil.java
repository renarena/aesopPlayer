package com.studio4plus.homerplayer.ui;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.os.CountDownTimer;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.ViewFlipper;
import android.support.annotation.NonNull;

import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.R;

import javax.inject.Singleton;

@Singleton
public class UiUtil {

    @SuppressWarnings("deprecation") // of Fragment
    static public class SnoozeDisplay {
        private View snoozeOverlay;
        private TextView snoozeCounter;

        @SuppressLint("ClickableViewAccessibility")
        private void SnoozeDisplayFor(
                final Fragment fragment,
                final View view,
                final int time) {

            if (time <= 0) {
                return;
            }

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
                }
            }.start();
        }

        public SnoozeDisplay(Fragment fragment, View view, @NonNull GlobalSettings globalSettings) {
            int time = globalSettings.getSnoozeDelay();
            SnoozeDisplayFor(fragment, view, time);
        }
    }

    static public void startBlinker(View view, @NonNull GlobalSettings globalSettings) {
        ViewFlipper flipper;

        // time in MS for a cycle
        int time = globalSettings.getBlinkRate();
        if (time <= 0){
            return;
        }

        flipper = view.findViewById(R.id.flipper);
        if (time > 300) {
            flipper.setInAnimation(AnimationUtils.loadAnimation(view.getContext(), android.R.anim.fade_in));
            flipper.setOutAnimation(AnimationUtils.loadAnimation(view.getContext(), android.R.anim.fade_out));
        }
        flipper.setFlipInterval(time);
        flipper.startFlipping();
    }
}
