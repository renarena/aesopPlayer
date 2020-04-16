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
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.constraintlayout.widget.ConstraintLayout;

public class TouchTransparentLayout extends ConstraintLayout {
    private OnTouchListener myListener;

    public TouchTransparentLayout(Context context) {
        super(context);
    }
    public TouchTransparentLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public TouchTransparentLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    public TouchTransparentLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (myListener == null) return false;

        myListener.onTouch(this,ev);
        return ev.getPointerCount() > 1; // false -> keep watching events here
            // true -> steal event stream (until next down event) to our onTouchEvent
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        myListener.onTouch(this,event);
        return true; // false cancels gesture
    }

    @Override
    public void setOnTouchListener(OnTouchListener l) {
        myListener = l;
    }
}
