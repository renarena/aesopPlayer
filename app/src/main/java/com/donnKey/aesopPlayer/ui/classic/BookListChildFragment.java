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

import android.animation.Animator;
import android.animation.ValueAnimator;
import androidx.fragment.app.Fragment;

import com.donnKey.aesopPlayer.R;

/**
 * A class implementing a workaround for https://code.google.com/p/android/issues/detail?id=55228
 *
 * Inspired by http://stackoverflow.com/a/23276145/3892517
 */

@SuppressWarnings("WeakerAccess") // Android requires public
public class BookListChildFragment extends Fragment {

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        final Fragment parent = getParentFragment();

        // Apply the workaround only if this is a child fragment, and the parent
        // is being removed.
        if (!enter && parent != null && parent.isRemoving()) {
            ValueAnimator nullAnimation = new ValueAnimator();
            nullAnimation.setIntValues(1, 1);
            nullAnimation.setDuration(R.integer.flip_animation_time_half_ms);
            return nullAnimation;
        } else {
            return super.onCreateAnimator(transit, enter, nextAnim);
        }
    }
}
