package com.studio4plus.homerplayer.ui.classic;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.Fragment;

import com.studio4plus.homerplayer.R;

/**
 * A class implementing a workaround for https://code.google.com/p/android/issues/detail?id=55228
 *
 * Inspired by http://stackoverflow.com/a/23276145/3892517
 */

// TODO: Fragment etc. are deprecated. See DT change 11/29/2018 that ...
// almost fixes this, but crashes on 4.4.2 (but not Pie). Fix requires
// changing to AppCompatActivity (which the web in some places says is
// preferred), and that seems to be the cause of the crash which
// occurs deep in the Settings library with an illegal cast between
// Activity and AppCompatActivity. Suppressing the warning here
// and elsewhere but with a TO-DO (only here) to remind us.
@SuppressWarnings("deprecation")
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
