/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Donn S. Terry
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class TwoFingerSwipe implements View.OnTouchListener {
    public interface Listener {
        void onSwipe(Direction direction);
    }

    public enum Direction {LEFT,RIGHT}
    private final Listener listener;

    private final int minimumScrollMovement;

    // Detect and report a two-finger swipe to the left or right, ignoring ones with too much
    // up/down component.
    public TwoFingerSwipe(Context context, TwoFingerSwipe.Listener listener) {
        this.listener = listener;

        ViewConfiguration configuration = ViewConfiguration.get(context);
        minimumScrollMovement = configuration.getScaledTouchSlop();
    }

    private float X0, Y0;
    private int state = 0;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        // Must ALWAYS return true, or get cancelled

        switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
            state = 1;
            // Ignore (insist on PTR_DOWN)
            return true;

        case MotionEvent.ACTION_POINTER_DOWN:
            if (state == 1) {
                state = 2;
            }
            X0 = event.getX();
            Y0 = event.getY();
            return true;

        case MotionEvent.ACTION_UP:
            // Ignore one-finger part of the action
            return true;

        case MotionEvent.ACTION_POINTER_UP:
            if (state != 2) {
                // Ignore bounces
                return true;
            }
            state = 0;
            float x = event.getX();
            float y = event.getY();

            final float deltaX = x -X0;
            final float deltaY = y -Y0;
            if (Math.abs(deltaX) < minimumScrollMovement) {
                return true;
            }
            if (Math.abs(deltaY) > Math.abs(deltaX)/2.0) {
                // Ignore if not clearly horizontal
                return true;
            }
            listener.onSwipe(deltaX < 0 ? Direction.LEFT : Direction.RIGHT);
            return true;

        default:
            return true;
        }
    }
}
