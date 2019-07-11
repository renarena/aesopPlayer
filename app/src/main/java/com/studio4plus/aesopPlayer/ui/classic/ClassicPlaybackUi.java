package com.studio4plus.aesopPlayer.ui.classic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.studio4plus.aesopPlayer.GlobalSettings;
import com.studio4plus.aesopPlayer.AesopPlayerApplication;
import com.studio4plus.aesopPlayer.ui.PlaybackUi;
import com.studio4plus.aesopPlayer.ui.SoundBank;
import com.studio4plus.aesopPlayer.ui.UiControllerPlayback;

import java.util.EnumMap;

import javax.inject.Inject;

public class ClassicPlaybackUi implements PlaybackUi {

    @SuppressWarnings("WeakerAccess")
    @Inject public SoundBank soundBank;
    @SuppressWarnings("WeakerAccess")
    @Inject public GlobalSettings globalSettings;

    private final @NonNull FragmentPlayback fragment;
    private final @NonNull ClassicMainUi mainUi;
    private final boolean animateOnInit;

    private @Nullable SoundBank.Sound ffRewindSound;

    ClassicPlaybackUi(
            @NonNull AppCompatActivity activity, @NonNull ClassicMainUi mainUi, boolean animateOnInit) {
        this.fragment = new FragmentPlayback();
        this.mainUi = mainUi;
        this.animateOnInit = animateOnInit;
        AesopPlayerApplication.getComponent(activity).inject(this);

        if (globalSettings.isFFRewindSoundEnabled())
            ffRewindSound = soundBank.getSound(SoundBank.SoundId.FF_REWIND);
    }

    @Override
    public void initWithController(@NonNull UiControllerPlayback controller) {
        fragment.setController(controller);
        mainUi.showPlayback(fragment, animateOnInit);
    }

    @Override
    public void onPlaybackProgressed(long playbackPositionMs) {
        fragment.onPlaybackProgressed(playbackPositionMs);
    }

    @Override
    public void onPlaybackStopping() {
        fragment.onPlaybackStopping();
    }

    @Override
    public void onFFRewindSpeed(SpeedLevel speedLevel) {
        if (ffRewindSound != null) {
            if (speedLevel == SpeedLevel.STOP) {
                SoundBank.stopTrack(ffRewindSound.track);
            } else {
                @SuppressWarnings("ConstantConditions") int soundPlaybackFactor = SPEED_LEVEL_SOUND_RATE.get(speedLevel);
                ffRewindSound.track.setPlaybackRate(ffRewindSound.sampleRate * soundPlaybackFactor);
                ffRewindSound.track.play();
            }

        }
    }

    public void onChangeStopPause(int title) {
        fragment.onChangeStopPause(title);
    }

    private static final EnumMap<SpeedLevel, Integer> SPEED_LEVEL_SOUND_RATE =
            new EnumMap<>(SpeedLevel.class);

    static {
        // No value for STOP.
        SPEED_LEVEL_SOUND_RATE.put(SpeedLevel.REGULAR, 1);
        SPEED_LEVEL_SOUND_RATE.put(SpeedLevel.FAST, 2);
        SPEED_LEVEL_SOUND_RATE.put(SpeedLevel.FASTEST, 4);
    }
}
