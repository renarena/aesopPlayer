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
package com.donnKey.aesopPlayer.ui;

import android.view.View;
import android.view.ViewStub;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import com.donnKey.aesopPlayer.R;

public class HintOverlay {

    private final View parentView;
    private final int viewStubId;
    private final int textResourceId;
    private final int imageResourceId;
    private final Listener listener;

    public HintOverlay(View parentView, int viewStubId, int textResourceId, int imageResourceId,
                       Listener listener) {
        this.parentView = parentView;
        this.viewStubId = viewStubId;
        this.textResourceId = textResourceId;
        this.imageResourceId = imageResourceId;
        this.listener = listener;
    }

    public void show() {
        ViewStub stub = parentView.findViewById(viewStubId);
        if (stub != null) {
            final View hintOverlay = stub.inflate();
            hintOverlay.setVisibility(View.VISIBLE);

            ((ImageView) hintOverlay.findViewById(R.id.image)).setImageResource(imageResourceId);
            ((TextView) hintOverlay.findViewById(R.id.text)).setText(
                    parentView.getResources().getString(textResourceId));

            Animation animation = new AlphaAnimation(0, 1);
            animation.setDuration(750);
            animation.setStartOffset(500);

            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    hintOverlay.setOnClickListener(new HideHintClickListener(hintOverlay));
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            hintOverlay.startAnimation(animation);
            hintOverlay.setOnClickListener(new BlockClickListener());
        }
    }

    private static class BlockClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            // Do nothing.
        }
    }

    private class HideHintClickListener implements View.OnClickListener {

        private final View hintOverlay;

        HideHintClickListener(View hintOverlay) {
            this.hintOverlay = hintOverlay;
        }

        @Override
        public void onClick(View v) {
            Animation animation = new AlphaAnimation(1, 0);
            animation.setDuration(300);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    hintOverlay.setOnClickListener(null);
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    listener.setHintShown();
                    hintOverlay.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            hintOverlay.startAnimation(animation);
        }
    }
    public interface Listener {
        void setHintShown();
    }
}
