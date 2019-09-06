/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2019 Donn S. Terry
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.ui.PlaybackUi;
import com.donnKey.aesopPlayer.ui.SoundBank;
import com.donnKey.aesopPlayer.ui.UiControllerPlayback;

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
