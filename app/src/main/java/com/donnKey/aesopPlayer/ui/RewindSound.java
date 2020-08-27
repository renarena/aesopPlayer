/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Donn S. Terry
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
package com.donnKey.aesopPlayer.ui;
import android.os.Handler;

import androidx.annotation.Nullable;
import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import java.util.EnumMap;
import javax.inject.Inject;

public class RewindSound {
    @Inject public SoundBank soundBank;
    @Inject public GlobalSettings globalSettings;

    public enum SpeedLevel {
        STOP,
        REGULAR,
        FAST,
        FASTEST
        }

    private static final EnumMap<SpeedLevel, Integer> SPEED_LEVEL_SOUND_RATE =
            new EnumMap<>(SpeedLevel.class);

    private final Handler handler = new Handler();

    static {
        // No value for STOP.
        SPEED_LEVEL_SOUND_RATE.put(SpeedLevel.REGULAR, 1);
        SPEED_LEVEL_SOUND_RATE.put(SpeedLevel.FAST, 2);
        SPEED_LEVEL_SOUND_RATE.put(SpeedLevel.FASTEST, 4);
    }

    private @Nullable SoundBank.Sound ffRewindSound;

    public RewindSound() {
        AesopPlayerApplication.getComponent().inject(this);
        if (globalSettings.isFFRewindSoundEnabled())
            ffRewindSound = soundBank.getSound(SoundBank.SoundId.FF_REWIND);
    }

    public void setFFRewindSpeed(SpeedLevel speedLevel) {
        if (ffRewindSound != null) {
            if (speedLevel == SpeedLevel.STOP) {
                SoundBank.stopTrack(ffRewindSound.track);
            } else {
                @SuppressWarnings("ConstantConditions")
                int soundPlaybackFactor = SPEED_LEVEL_SOUND_RATE.get(speedLevel);
                ffRewindSound.track.setPlaybackRate(ffRewindSound.sampleRate * soundPlaybackFactor);
                ffRewindSound.track.play();
            }
        }
    }

    public void rewindBurst() {
        if (ffRewindSound != null) {
            //noinspection ConstantConditions
            ffRewindSound.track.setPlaybackRate(ffRewindSound.sampleRate * SPEED_LEVEL_SOUND_RATE.get(SpeedLevel.REGULAR));
            ffRewindSound.track.play();
            handler.postDelayed(()->SoundBank.stopTrack(ffRewindSound.track),500);
        }
    }
}
