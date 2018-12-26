package com.studio4plus.homerplayer.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.CountDownTimer;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import android.support.annotation.NonNull;

import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.R;

import java.util.concurrent.TimeUnit;

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
                    } else {
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
        if (time <= 0) {
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

    // Three ways to get to settings, with thought to addressing accidental
    // mishandling of the phone:
    // 1) Just like most apps, tap the gear icon.
    // 2) Hold one gear icon, then simultaneously press a new one that appears.
    // 3) Multiple taps on the gear icon.
    // If none of those prove resistant to mishandling, settings button followed
    // by a volume key looks like a possibility.
    @SuppressLint("ClickableViewAccessibility") // Accessibility not appropriate for some options
    static public void connectToSettings(View view, @NonNull GlobalSettings globalSettings) {

        // All versions start with this gone, so be sure
        final View settingsButton2box = view.findViewById(R.id.settingsButton2box);
        settingsButton2box.setVisibility(View.GONE);

        final Context context = view.getContext();
        final Activity activity = (Activity)context;
        final Button settingsButton = view.findViewById(R.id.settingsButton);

        switch (globalSettings.getSettingsInterlock()) {
            case NONE: {
                settingsButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        activity.startActivity(new Intent(context, SettingsActivity.class));
                        settingsButton.setEnabled(false);
                    }
                });
                break;
            }
            case DOUBLE_PRESS: {
                settingsButton.setOnTouchListener(new PressListener(view));
                break;
            }
            case MULTI_TAP: {
                settingsButton.setOnTouchListener(new MultitapTouchListener(
                        context, new MultitapTouchListener.Listener() {
                    @Override
                    public void onMultiTap() {
                        activity.startActivity(new Intent(context, SettingsActivity.class));
                    }
                }));
                break;
            }
        }
    }

    static private class PressListener implements View.OnTouchListener {
        private final View pressListener;
        private final @NonNull Context context;
        final Activity activity;
        private Toast lastToast;

        PressListener(@NonNull View view){
            context = view.getContext();
            activity = (Activity)context;
            pressListener = view;
        }

        @SuppressLint("ClickableViewAccessibility") // Accessibility not appropriate for this option
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    lastToast = Toast.makeText(context, R.string.press_other_gear_prompt, Toast.LENGTH_LONG);
                    lastToast.show();

                    final View settingsButton2box = pressListener.findViewById(R.id.settingsButton2box);
                    settingsButton2box.setVisibility(View.VISIBLE);

                    final Button settingsButton2 = pressListener.findViewById(R.id.settingsButton2);
                    settingsButton2.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            lastToast.cancel();
                            if (event.getAction() == MotionEvent.ACTION_UP) {
                                activity.startActivity(new Intent(context, SettingsActivity.class));
                            }
                            return true;
                        }
                    });
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    final View settingsButton2box = pressListener.findViewById(R.id.settingsButton2box);
                    settingsButton2box.setVisibility(View.GONE);
                    break;
                }
                default:
                    return false;
            }
            return true;
        }
    }

    // Where we are in the current book
    @SuppressLint("DefaultLocale")
    static public String formatDuration(long currentMs) {
        long hours = TimeUnit.MILLISECONDS.toHours(currentMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(currentMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(currentMs) % 60;

        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }
}
