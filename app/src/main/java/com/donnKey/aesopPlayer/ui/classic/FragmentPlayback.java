/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2020 Donn S. Terry
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
package com.donnKey.aesopPlayer.ui.classic;

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

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.service.DeviceMotionDetector;
import com.donnKey.aesopPlayer.ui.FFRewindTimer;
import com.donnKey.aesopPlayer.ui.HintOverlay;
import com.donnKey.aesopPlayer.ui.PressReleaseDetector;
import com.donnKey.aesopPlayer.ui.RewindSound;
import com.donnKey.aesopPlayer.ui.SimpleAnimatorListener;
import com.donnKey.aesopPlayer.ui.Speaker;
import com.donnKey.aesopPlayer.ui.TouchRateJoystick;
import com.donnKey.aesopPlayer.ui.TwoFingerSwipe;
import com.donnKey.aesopPlayer.ui.UiUtil;
import com.donnKey.aesopPlayer.ui.UiControllerPlayback;
import com.donnKey.aesopPlayer.util.ViewUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.codetail.animation.ViewAnimationUtils;

import static android.media.AudioManager.STREAM_MUSIC;
import static com.donnKey.aesopPlayer.AesopPlayerApplication.getAppContext;
import static com.donnKey.aesopPlayer.model.AudioBook.UNKNOWN_POSITION;

public class FragmentPlayback extends Fragment implements FFRewindTimer.Observer {

    private View view;
    private View wholeScreen;
    private AppCompatImageButton stopButton;
    private AppCompatImageButton rewindButton;
    private AppCompatImageButton ffButton;
    private TextView elapsedTimeView;
    private TextView volumeSpeedView;
    private TextView elapsedTimeRewindFFView;
    private TextView stopText;
    private TextView chapterInfoView;
    private RewindFFHandler rewindFFHandler;
    private Animator elapsedTimeRewindFFViewAnimation;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private UiUtil.SnoozeDisplay snooze;
    private AdjustmentsListener adjustmentsListener = null;

    private @Nullable UiControllerPlayback controller;

    @Inject public GlobalSettings globalSettings;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_playback, container, false);
        AesopPlayerApplication.getComponent(view.getContext()).inject(this);

        // This should be early so no buttons go live before this
        snooze = new UiUtil.SnoozeDisplay(this, view, globalSettings);

        wholeScreen = requireActivity().findViewById(R.id.wholeScreen);

        stopButton = view.findViewById(R.id.stopButton);
        stopButton.setOnClickListener(v -> {
            Objects.requireNonNull(controller);
            controller.stopPlayback();
        });
        adjustmentsListener = new AdjustmentsListener();

        elapsedTimeView = view.findViewById(R.id.elapsedTime);
        volumeSpeedView= view.findViewById(R.id.Volume_speed);
        elapsedTimeRewindFFView = view.findViewById(R.id.elapsedTimeRewindFF);
        chapterInfoView = view.findViewById(R.id.chapterInfo);
        stopText = view.findViewById(R.id.stop_text);

        View etBoxView = view.findViewById(R.id.elapsedTimeBox);
        etBoxView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            RelativeLayout.LayoutParams params =
                    (RelativeLayout.LayoutParams) elapsedTimeRewindFFView.getLayoutParams();
            params.leftMargin = left;
            params.topMargin = top;
            params.width = right - left;
            params.height = bottom - top;
            elapsedTimeRewindFFView.setLayoutParams(params);
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
        CrashWrapper.log("UI: FragmentPlayback resumed");
        rewindButton.setOnTouchListener(new PressReleaseDetector(rewindFFHandler));
        ffButton.setOnTouchListener(new PressReleaseDetector(rewindFFHandler));
        if (globalSettings.isScreenVolumeSpeedEnabled()) {
            stopButton.setOnTouchListener(new TouchRateJoystick(view.getContext(),
                    adjustmentsListener::handleSettings));
        }

        if (globalSettings.isTiltVolumeSpeedEnabled()) {
            DeviceMotionDetector.getDeviceMotionDetector(adjustmentsListener::handleSettings);
        }

        if (globalSettings.isSwipeStopPointsEnabled()) {
            wholeScreen.setOnTouchListener(new TwoFingerSwipe(view.getContext(),
                    this::seekNextStop));
        }

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
        stopButton.setOnTouchListener(null);
        DeviceMotionDetector.getDeviceMotionDetector((TouchRateJoystick.Listener)null);
        wholeScreen.setOnTouchListener(null);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (adjustmentsListener != null) {
            adjustmentsListener.onDestroy();
        }
        super.onDestroy();
    }

    void onPlaybackStopping() {
        disableUiOnStopping();
        rewindFFHandler.onStopping();
    }

    void onPlaybackProgressed(long totalElapsedTimeMs) {
        onTimerUpdated(totalElapsedTimeMs);
        enableUiOnStart();
    }

    void onChangeStopPause(int title) {
        stopText.setText(title);
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

    private String elapsedTime(long totalElapsedMs) {
        Objects.requireNonNull(controller);
        String duration = UiUtil.formatDuration(totalElapsedMs);

        long totalMs = controller.getAudioBookBeingPlayed().getTotalDurationMs();
        String progress = "?";
        if (totalMs == UNKNOWN_POSITION) {
            progress = "??";
        }
        else if (totalElapsedMs == 0) {
            progress = "0";
        }
        else if (totalMs != 0) {
            progress = (Long.valueOf((totalElapsedMs * 100) / totalMs)).toString();
        }
        // Change below is for sleep bug hacking, but consider with respect to other use of this resource
        return getAppContext().getString(R.string.playback_elapsed_time, duration, progress);
    }

    private void showHintIfNecessary() {
        if (isResumed() && isVisible()) {
            if (!globalSettings.flipToStopHintShown()) {
                HintOverlay overlay = new HintOverlay(
                        view, R.id.flipToStopHintOverlayStub, R.string.hint_flip_to_stop,
                        R.drawable.hint_flip_to_stop, globalSettings::setFlipToStopHintShown);
                overlay.show();
            }
        }
    }

    @Override
    public void onTimerUpdated(long totalElapsedTimeMs) {
        Objects.requireNonNull(controller);
        String timeToDisplay = elapsedTime(totalElapsedTimeMs);
        elapsedTimeView.setText(timeToDisplay);
        chapterInfoView.setText(controller.getAudioBookBeingPlayed().getChapter());
        elapsedTimeRewindFFView.setText(timeToDisplay);
    }

    @Override
    public void onTimerLimitReached() {
        if (elapsedTimeRewindFFView.getVisibility() == View.VISIBLE) {
            elapsedTimeRewindFFViewAnimation.start();
        }
    }

    private void seekNextStop(TwoFingerSwipe.Direction direction) {
        if (controller == null) {
            return;
        }

        AudioBook book = controller.getAudioBookBeingPlayed();
        long lastTimeChop = controller.getCurrentPositionMs();
        long newPosition;
        switch (direction) {
        case LEFT:
            // Back search up a little to allow user time to back up again, rather than
            // just backing up to the same place because it advanced a little.
            newPosition = book.getStopBefore(lastTimeChop - TimeUnit.SECONDS.toMillis(5));
            break;
        case RIGHT:
            newPosition = book.getStopAfter(lastTimeChop);
            break;
        default:
            return;
        }

        if (newPosition == lastTimeChop) {
            return;
        }

        controller.pauseForRewind();
        book.updateTotalPosition(newPosition);
        new RewindSound().rewindBurst();
        controller.resumeFromRewind();
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
            Objects.requireNonNull(controller);
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
            Objects.requireNonNull(controller);
            stopRewind();
            controller.resumeFromRewind();
        }

        private void stopRewind() {
            Objects.requireNonNull(controller);
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
        // sounds at a somewhat lower amplitude, so use AudioPlayer for those.

        int oldMax;
        int newTarget;
        int deferChange = -1;

        float speechRate;

        AdjustmentsListener ()
        {
            audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
            mediaPlayerTick = MediaPlayer.create(getContext(), R.raw.tick);
            mediaPlayerComplain = MediaPlayer.create(getContext(), R.raw.limit_hit);
            speaker = Speaker.get();
        }

        void onDestroy()
        {
            mediaPlayerTick.release();
            mediaPlayerComplain.release();
        }

        private void tick() {
            mediaPlayerTick.start();
        }

        private void complain() {
            mediaPlayerComplain.start();
        }

        private void announce(String s)
        {
            // The volume is a property of each utterance; we want to be fairly loud
            // for the single word.
            float oldV = speaker.getVolume();
            speaker.setVolume(1.0f);
            speaker.speak(s);
            speaker.setVolume(oldV);
        }

        // Handle volume and speed settings coming from the "joystick" interface.

        private void handleSettings(TouchRateJoystick.Direction direction, int counter) {
            Objects.requireNonNull(controller);
            if (counter == TouchRateJoystick.RELEASE || counter == TouchRateJoystick.REVERSE) {
                // Wrap up everything.
                switch (direction) {
                case UP:
                case DOWN:
                    // Make sure it sticks
                    globalSettings.setPlaybackSpeed(speechRate);
                    break;

                case LEFT:
                case RIGHT:
                    // Nothing to do, since we've been setting the actual volume.
                    break;
                }
                if (counter == TouchRateJoystick.RELEASE) {
                    volumeSpeedView.setVisibility(View.GONE);
                }
            }
            else if (counter == 0) {
                // Initialize
                switch (direction) {
                case UP:
                case DOWN:
                    announce(direction == TouchRateJoystick.Direction.UP
                            ? getString(R.string.audio_prompt_faster)
                            : getString(R.string.audio_prompt_slower));
                    speechRate = controller.getSpeed();
                    volumeSpeedView.setVisibility(View.VISIBLE);
                    volumeSpeedView.setText(String.format(getString(R.string.display_speed), speechRate));
                    break;

                case LEFT:
                case RIGHT:
                    announce(direction == TouchRateJoystick.Direction.LEFT
                            ? getString(R.string.audio_prompt_softer)
                            : getString(R.string.audio_prompt_louder));
                    oldMax = audioManager.getStreamMaxVolume(STREAM_MUSIC);
                    newTarget = audioManager.getStreamVolume(STREAM_MUSIC);
                    volumeSpeedView.setVisibility(View.VISIBLE);
                    volumeSpeedView.setText(String.format(getString(R.string.display_volume), newTarget));
                    break;
                }
            }
            else {
                // Individual change ticks
                switch (direction) {
                case UP:
                    // This ceiling seems large, but some studies indicate that there are a few
                    // well-practiced people who can use it. See --
                    //  Danielle Bragg, Cynthia Bennett, Katharina Reinecke, and Richard Ladner. 2018.
                    //  A Large Inclusive Study of Human Listening Rates.
                    //  In Proceedings of the 2018 CHI Conference on Human Factors in Computing Systems
                    //  (CHI '18). ACM, New York, NY, USA, Paper 444, 12 pages.
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
                    audioManager.setStreamVolume(STREAM_MUSIC, newTarget, 0);
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
                    audioManager.setStreamVolume(STREAM_MUSIC, newTarget, 0);
                    volumeSpeedView.setText(String.format(getString(R.string.display_volume), newTarget));
                    tick();
                    break;
                }
                }
            }
        }
    }
}
