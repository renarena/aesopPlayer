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

import androidx.annotation.NonNull;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.ui.PlaybackUi;
import com.donnKey.aesopPlayer.ui.RewindSound;
import com.donnKey.aesopPlayer.ui.UiControllerPlayback;

public class ClassicPlaybackUi implements PlaybackUi {

    private final @NonNull FragmentPlayback fragment;
    private final @NonNull ClassicMainUi mainUi;
    private final boolean animateOnInit;
    private final @NonNull RewindSound rewindSound;
    private final boolean snooze;

    ClassicPlaybackUi(
            @NonNull ClassicMainUi mainUi, boolean animateOnInit, boolean snooze) {
        this.fragment = new FragmentPlayback();
        this.mainUi = mainUi;
        this.animateOnInit = animateOnInit;
        this.snooze = snooze;
        AesopPlayerApplication.getComponent().inject(this);

        rewindSound = new RewindSound();
    }

    @Override
    public void initWithController(@NonNull UiControllerPlayback controller) {
        fragment.setController(controller);
        mainUi.showPlayback(fragment, animateOnInit, snooze );
    }

    @Override
    public void onPlaybackProgressed(long playbackTotalPositionMs) {
        fragment.onPlaybackProgressed(playbackTotalPositionMs);
    }

    @Override
    public void onPlaybackStopping() {
        fragment.onPlaybackStopping();
    }

    @Override
    public void onFFRewindSpeed(RewindSound.SpeedLevel speedLevel) {
        rewindSound.setFFRewindSpeed(speedLevel);
    }

    public void onChangeStopPause(int title) {
        fragment.onChangeStopPause(title);
    }
}
