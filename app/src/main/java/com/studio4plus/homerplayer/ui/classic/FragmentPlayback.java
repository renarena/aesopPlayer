package com.studio4plus.homerplayer.ui.classic;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.HomerPlayerApplication;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.ui.FFRewindTimer;
import com.studio4plus.homerplayer.ui.HintOverlay;
import com.studio4plus.homerplayer.ui.PressReleaseDetector;
import com.studio4plus.homerplayer.ui.SimpleAnimatorListener;
import com.studio4plus.homerplayer.ui.Speaker;
import com.studio4plus.homerplayer.ui.TouchRateJoystick;
import com.studio4plus.homerplayer.ui.UiUtil;
import com.studio4plus.homerplayer.ui.UiControllerPlayback;
import com.studio4plus.homerplayer.util.ViewUtils;

import java.util.Objects;

import javax.inject.Inject;

import io.codetail.animation.ViewAnimationUtils;

import static android.media.AudioManager.STREAM_MUSIC;

public class FragmentPlayback extends Fragment implements FFRewindTimer.Observer {

    private View view;
    private AppCompatImageButton stopButton;
    private AppCompatImageButton rewindButton;
    private AppCompatImageButton ffButton;
    private TextView elapsedTimeView;
    private TextView volumeSpeedView;
    private TextView elapsedTimeRewindFFView;
    private TextView chapterInfoView;
    private RewindFFHandler rewindFFHandler;
    private Animator elapsedTimeRewindFFViewAnimation;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private UiUtil.SnoozeDisplay snooze;

    private @Nullable UiControllerPlayback controller;

    @SuppressWarnings("WeakerAccess")
    @Inject public GlobalSettings globalSettings;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_playback, container, false);
        HomerPlayerApplication.getComponent(view.getContext()).inject(this);

        // This should be early so no buttons go live before this
        snooze = new UiUtil.SnoozeDisplay(this, view, globalSettings);

        stopButton = view.findViewById(R.id.stopButton);
        stopButton.setOnClickListener(v -> {
            Preconditions.checkNotNull(controller);
            controller.stopPlayback();
        });
        if (globalSettings.isScreenVolumeSpeedEnabled()) {
            AdjustmentsListener adjustmentsListener = new AdjustmentsListener();
            stopButton.setOnTouchListener(new TouchRateJoystick(view.getContext(),
                    adjustmentsListener::handleSettings));
        }

        elapsedTimeView = view.findViewById(R.id.elapsedTime);
        volumeSpeedView= view.findViewById(R.id.Volume_speed);
        elapsedTimeRewindFFView = view.findViewById(R.id.elapsedTimeRewindFF);
        chapterInfoView = view.findViewById(R.id.chapterInfo);

        elapsedTimeView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(
                    View v,
                    int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                RelativeLayout.LayoutParams params =
                        (RelativeLayout.LayoutParams) elapsedTimeRewindFFView.getLayoutParams();
                params.leftMargin = left;
                params.topMargin = top;
                params.width = right - left;
                params.height = bottom - top;
                elapsedTimeRewindFFView.setLayoutParams(params);
            }
        });

        rewindButton = view.findViewById(R.id.rewindButton);
        ffButton = view.findViewById(R.id.fastForwardButton);

        View rewindFFOverlay = view.findViewById(R.id.rewindFFOverlay);
        rewindFFHandler = new RewindFFHandler(
                (View) rewindFFOverlay.getParent(), rewindFFOverlay);
        rewindButton.setEnabled(false);
        ffButton.setEnabled(false);

        rewindFFOverlay.setOnTouchListener((v, event) -> {
            // Don't let any events "through" the overlay.
            return true;
        });

        elapsedTimeRewindFFViewAnimation =
                AnimatorInflater.loadAnimator(view.getContext(), R.animator.bounce);
        elapsedTimeRewindFFViewAnimation.setTarget(elapsedTimeRewindFFView);

        UiUtil.startBlinker(view, globalSettings);

        return view;
    }

    // TODO: can press-and-hold be made accessible?
    @SuppressLint("ClickableViewAccessibility") // This is press-and-hold, so click not meaningful
    @Override
    public void onResume() {
        super.onResume();
        Crashlytics.log("UI: FragmentPlayback resumed");
        rewindButton.setOnTouchListener(new PressReleaseDetector(rewindFFHandler));
        ffButton.setOnTouchListener(new PressReleaseDetector(rewindFFHandler));
        UiUtil.startBlinker(view, globalSettings);
        showHintIfNecessary();
    }

    @SuppressLint("ClickableViewAccessibility") // This is press-and-hold, so click not meaningful
    // TODO: can press-and-hold be made accessible?
    @Override
    public void onPause() {
        // Remove press-release detectors and tell rewindFFHandler directly that we're paused.
        rewindButton.setOnTouchListener(null);
        ffButton.setOnTouchListener(null);
        rewindFFHandler.onPause();
        super.onPause();
    }

    void onPlaybackStopping() {
        disableUiOnStopping();
        rewindFFHandler.onStopping();
    }

    void onPlaybackProgressed(long playbackPositionMs) {
        onTimerUpdated(playbackPositionMs);
        enableUiOnStart();
    }

    private void enableUiOnStart() {
        rewindButton.setEnabled(true);
        ffButton.setEnabled(true);
    }

    private void disableUiOnStopping() {
        rewindButton.setEnabled(false);
        stopButton.setEnabled(false);
        ffButton.setEnabled(false);
    }

    private String elapsedTime(long elapsedMs) {
        Preconditions.checkNotNull(controller);
        String duration = UiUtil.formatDuration(elapsedMs);

        long total = controller.getAudioBookBeingPlayed().getTotalDurationMs();
        long progress = 0;
        if (total != 0) {
            progress = (100 * elapsedMs) / total;
        }

        return getString(R.string.playback_elapsed_time, duration, progress);
    }

    private void showHintIfNecessary() {
        if (isResumed() && isVisible()) {
            if (!globalSettings.flipToStopHintShown()) {
                HintOverlay overlay = new HintOverlay(
                        view, R.id.flipToStopHintOverlayStub, R.string.hint_flip_to_stop, R.drawable.hint_flip_to_stop);
                overlay.show();
                globalSettings.setFlipToStopHintShown();
            }
        }
    }

    @Override
    public void onTimerUpdated(long displayTimeMs) {
        Preconditions.checkNotNull(controller);
        elapsedTimeView.setText(elapsedTime(displayTimeMs));
        chapterInfoView.setText(controller.getAudioBookBeingPlayed().getChapter());
        elapsedTimeRewindFFView.setText(elapsedTime(displayTimeMs));
    }

    @Override
    public void onTimerLimitReached() {
        if (elapsedTimeRewindFFView.getVisibility() == View.VISIBLE) {
            elapsedTimeRewindFFViewAnimation.start();
        }
    }

    void setController(@NonNull UiControllerPlayback controller) {
        this.controller = controller;
    }

    private class RewindFFHandler implements PressReleaseDetector.Listener {

        private final View commonParent;
        private final View rewindOverlay;
        private Animator currentAnimator;
        private boolean isRunning;

        private RewindFFHandler(@NonNull View commonParent, @NonNull View rewindOverlay) {
            this.commonParent = commonParent;
            this.rewindOverlay = rewindOverlay;
        }

        @Override
        public void onPressed(final View v, float x, float y) {
            Preconditions.checkNotNull(controller);
            if (currentAnimator != null) {
                currentAnimator.cancel();
            }

            final boolean isFF = (v == ffButton);
            rewindOverlay.setVisibility(View.VISIBLE);
            currentAnimator = createAnimation(v, x, y, true);
            currentAnimator.addListener(new SimpleAnimatorListener() {
                private boolean isCancelled = false;

                @Override
                public void onAnimationEnd(Animator animator) {

                    currentAnimator = null;
                    if (!isCancelled)
                        controller.startRewind(isFF, FragmentPlayback.this);
                }

                @Override
                public void onAnimationCancel(Animator animator) {
                    isCancelled = true;
                    resumeFromRewind();
                }
            });
            currentAnimator.start();

            controller.pauseForRewind();
            isRunning = true;
        }

        @Override
        public void onReleased(View v, float x, float y) {
            if (currentAnimator != null) {
                currentAnimator.cancel();
                rewindOverlay.setVisibility(View.GONE);
                currentAnimator = null;
            } else {
                currentAnimator = createAnimation(v, x, y, false);
                currentAnimator.addListener(new SimpleAnimatorListener() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        rewindOverlay.setVisibility(View.GONE);
                        currentAnimator = null;
                    }
                });
                currentAnimator.start();
                resumeFromRewind();
            }
        }

        void onPause() {
            if (currentAnimator != null) {
                // Cancelling the animation calls resumeFromRewind.
                currentAnimator.cancel();
                currentAnimator = null;
            } else if (isRunning) {
                resumeFromRewind();
            }
        }

        void onStopping() {
            if (isRunning)
                stopRewind();
        }

        private void resumeFromRewind() {
            Preconditions.checkNotNull(controller);
            stopRewind();
            controller.resumeFromRewind();
        }

        private void stopRewind() {
            Preconditions.checkNotNull(controller);
            controller.stopRewind();
            isRunning = false;
        }

        private Animator createAnimation(View v, float x, float y, boolean reveal) {
            Rect viewRect = ViewUtils.getRelativeRect(commonParent, v);
            float startX = viewRect.left + x;
            float startY = viewRect.top + y;

            // Compute final radius
            float dx = Math.max(startX, commonParent.getWidth() - startX);
            float dy = Math.max(startY, commonParent.getHeight() - startY);
            float finalRadius = (float) Math.hypot(dx, dy);

            float initialRadius = reveal ? 0f : finalRadius;
            if (!reveal)
                finalRadius = 0f;

            final int durationResId = reveal
                    ? R.integer.ff_rewind_overlay_show_animation_time_ms
                    : R.integer.ff_rewind_overlay_hide_animation_time_ms;
            Animator animator = ViewAnimationUtils.createCircularReveal(
                    rewindOverlay, Math.round(startX), Math.round(startY), initialRadius, finalRadius);
            animator.setDuration(getResources().getInteger(durationResId));
            animator.setInterpolator(new AccelerateDecelerateInterpolator());

            return animator;
        }
    }

    class AdjustmentsListener {
        final AudioManager audioManager;
        final Speaker speaker;

        private final MediaPlayer mediaPlayerComplain;
        private final MediaPlayer mediaPlayerTick;

        // I looked at both AudioPlayer and SoundPool for playing the recorded ticking sounds.
        // SoundPool appears to be a convenience class over AudioPlayer, but it renders the
        // sounds at a somewhat lower amplitude, so we AudioPlayer for those.

        final int tickStream = STREAM_MUSIC;

        int oldMax;
        int oldCurr;
        int newTarget;
        int deferChange = -1;

        float speechRate;

        AdjustmentsListener ()
        {
            audioManager = (AudioManager) Objects.requireNonNull(getContext()).getSystemService(Context.AUDIO_SERVICE);
            mediaPlayerTick = MediaPlayer.create(getContext(), R.raw.tick);
            mediaPlayerComplain = MediaPlayer.create(getContext(), R.raw.limit_hit);
            speaker = Speaker.get();
        }

        // Convert from 0-max volume integers to 0.0-1.0 that tracks what the volume buttons
        // do on Android.
        float scaleGain(int x)
        {
            final float log2 = (float)Math.log(2);
            if (x == 0) return 0.0f;
            // The formula is arbitrary, and almost certainly isn't what Android actually uses,
            // but I haven't been able to find that (in spite of many claims on Stackoverflow
            // to the contrary). This matches pretty well (experimentally) but isn't perfect;
            // the user will naturally "fix" any minor problems.
            return (float) (
                Math.pow(20, Math.log(x)/log2)
                        /
                Math.pow(20, Math.log(oldMax)/log2)
            );
        }

        private void tick() {
            final float vol = scaleGain(newTarget);
            mediaPlayerTick.setVolume(vol, vol);
            mediaPlayerTick.start();
        }

        private void complain() {
            final float vol = scaleGain(newTarget);
            mediaPlayerComplain.setVolume(vol, vol);
            mediaPlayerComplain.start();
        }

        private void announce(String s)
        {
            // The volume is a property of each utterance, so we don't need to worry about
            // tracking the volume ourselves.
            float oldV = speaker.getVolume();
            speaker.setVolume(scaleGain(newTarget));
            speaker.speak(s);
            speaker.setVolume(oldV);
        }

        // Handle volume and speed settings comping from the "joystick" interface.
        //
        // For level changes: On first call (counter == 0), set things up and emit message as to
        // what will happen.  Hand off the volume from the audioManager (set it to max) to
        // the (book) player, and adjust it's volume to match the current audioManager setting.
        // Subsequent (counter >= 1) calls: emit tick sounds or limit-hit sounds while adjusting the
        // volume. Final call (counter < 0), hand off volume control back to the audioManager (and
        // the phone's buttons). Housekeeping along the way. scaleGain() contains some extra magic
        // to make the hand-offs work well. (We "think" in 0-15 integers, converting the volume each time.)
        //
        // For speed changes: generally similar, but the volume issue isn't as tricky because
        // that's not being changed. It simply works to change the speed on the fly.
        private void handleSettings(TouchRateJoystick.Direction direction, int counter) {
            Preconditions.checkNotNull(controller);
            if (counter < 0) {
                // Wrap up everything.
                switch (direction) {
                case UP:
                case DOWN:
                    controller.setSpeed(speechRate);
                    globalSettings.setPlaybackSpeed(speechRate);
                    break;

                case LEFT:
                case RIGHT:
                    // Hand off volume control back to the system
                    audioManager.setStreamVolume(tickStream, newTarget, 0);
                    controller.setVolume(1.0f);
                    break;
                }
                volumeSpeedView.setVisibility(View.GONE);
            }
            else if (counter == 0) {
                switch (direction) {
                case UP:
                    newTarget = 15; // so ticks match current volume
                    speechRate = controller.getSpeed();
                    announce(getString(R.string.audio_prompt_faster));
                    volumeSpeedView.setVisibility(View.VISIBLE);
                    volumeSpeedView.setText(String.format(getString(R.string.display_speed), speechRate));
                    break;
                case DOWN:
                    newTarget = 15; // so ticks match current volume
                    speechRate = controller.getSpeed();
                    announce(getString(R.string.audio_prompt_slower));
                    volumeSpeedView.setVisibility(View.VISIBLE);
                    volumeSpeedView.setText(String.format(getString(R.string.display_speed), speechRate));
                    break;

                case LEFT:
                    oldMax = audioManager.getStreamMaxVolume(tickStream);
                    oldCurr = audioManager.getStreamVolume(tickStream);
                    newTarget = oldCurr;
                    // Hand off volume control to the playback controller, setting the
                    // device to maximum.
                    audioManager.setStreamVolume(tickStream, oldMax, 0);
                    controller.setVolume(scaleGain(oldCurr));
                    announce(getString(R.string.audio_prompt_softer));
                    volumeSpeedView.setVisibility(View.VISIBLE);
                    volumeSpeedView.setText(String.format(getString(R.string.display_volume), newTarget));
                    break;
                case RIGHT:
                    oldMax = audioManager.getStreamMaxVolume(tickStream);
                    oldCurr = audioManager.getStreamVolume(tickStream);
                    newTarget = oldCurr;
                    // Hand off volume control to the playback controller, setting the
                    // device to maximum.
                    audioManager.setStreamVolume(tickStream, oldMax, 0);
                    controller.setVolume(scaleGain(oldCurr));
                    announce(getString(R.string.audio_prompt_louder));
                    volumeSpeedView.setVisibility(View.VISIBLE);
                    volumeSpeedView.setText(String.format(getString(R.string.display_volume), newTarget));
                    break;
                }
            }
            else {
                // Individual change ticks
                switch (direction) {
                case UP:
                    if (speechRate >= 4.45f) {
                        complain();
                        break;
                    }
                    tick();
                    if (deferChange-- > 0) {
                        // An audible detent, in effect
                        announce(getString(R.string.audio_prompt_normal));
                        break;
                    }
                    speechRate += .1;
                    if (Math.abs(speechRate - 1.0f) < .01f) {
                        speechRate = 1.0f; // normalize
                        if (deferChange < 0) {
                            deferChange = 2;
                        }
                    }
                    volumeSpeedView.setText(String.format(getString(R.string.display_speed), speechRate));
                    controller.setSpeed(speechRate);
                    break;
                case DOWN:
                    if (speechRate <= 0.51f) {
                        complain();
                        break;
                    }
                    tick();
                    if (deferChange-- > 0) {
                        // An audible detent, in effect
                        announce(getString(R.string.audio_prompt_normal));
                        break;
                    }
                    speechRate -= .1;
                    if (Math.abs(speechRate - 1.0f) < .01f) {
                        speechRate = 1.0f; // normalize
                        if (deferChange < 0) {
                            deferChange = 2;
                        }
                    }
                    volumeSpeedView.setText(String.format(getString(R.string.display_speed), speechRate));
                    controller.setSpeed(speechRate);
                    break;

                case LEFT: {
                    if (newTarget <= 2) {
                        // Leave just a little playing so user isn't confused by total silence
                        complain();
                        break;
                    }
                    newTarget--;
                    controller.setVolume(scaleGain(newTarget));
                    volumeSpeedView.setText(String.format(getString(R.string.display_volume), newTarget));
                    tick();
                    break;
                }
                case RIGHT: {
                    if (newTarget >= oldMax) {
                        complain();
                        break;
                    }
                    newTarget++;
                    controller.setVolume(scaleGain(newTarget));
                    volumeSpeedView.setText(String.format(getString(R.string.display_volume), newTarget));
                    tick();
                    break;
                }
                }
            }
        }
    }
}
