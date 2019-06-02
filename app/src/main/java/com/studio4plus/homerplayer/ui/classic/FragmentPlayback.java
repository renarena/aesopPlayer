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
import com.studio4plus.homerplayer.service.DeviceMotionDetector;
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
import static com.studio4plus.homerplayer.HomerPlayerApplication.getAppContext;
import static com.studio4plus.homerplayer.model.AudioBook.UNKNOWN_POSITION;

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
    private AdjustmentsListener adjustmentsListener = null;

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
        adjustmentsListener = new AdjustmentsListener();

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
        if (globalSettings.isScreenVolumeSpeedEnabled()) {
            stopButton.setOnTouchListener(new TouchRateJoystick(view.getContext(),
                    adjustmentsListener::handleSettings));
        }
        if (globalSettings.isTiltVolumeSpeedEnabled()) {
            DeviceMotionDetector.getDeviceMotionDetector(adjustmentsListener::handleSettings);
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
        if (globalSettings.isScreenVolumeSpeedEnabled()) {
            stopButton.setOnTouchListener(null);
        }
        if (globalSettings.isTiltVolumeSpeedEnabled()) {
            DeviceMotionDetector.getDeviceMotionDetector((TouchRateJoystick.Listener)null);
        }
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

        long totalMs = controller.getAudioBookBeingPlayed().getTotalDurationMs();
        String progress = "?";
        if (totalMs == UNKNOWN_POSITION) {
            progress = "??";
        }
        else if (elapsedMs == 0) {
            progress = "0";
        }
        else if (totalMs != 0) {
            progress = (Long.valueOf((elapsedMs * 100) / totalMs)).toString();
        }
        // Change below is for sleep bug hacking, but consider with respect to other use of this resource
        return getAppContext().getString(R.string.playback_elapsed_time, duration, progress);
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
        // sounds at a somewhat lower amplitude, so use AudioPlayer for those.

        int oldMax;
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
            Preconditions.checkNotNull(controller);
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
