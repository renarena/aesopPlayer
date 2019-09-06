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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Toast;

import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.ui.BookListUi;
import com.donnKey.aesopPlayer.ui.InitUi;
import com.donnKey.aesopPlayer.ui.MainUi;
import com.donnKey.aesopPlayer.ui.NoBooksUi;

import java.io.File;

import javax.inject.Inject;

// TODO: ideally this would be a View.
class ClassicMainUi implements MainUi {

    private final @NonNull AppCompatActivity activity;

    @Inject
    ClassicMainUi(@NonNull AppCompatActivity activity) {
        this.activity = activity;
    }

    @NonNull @Override
    public BookListUi switchToBookList(boolean animate) {
        ClassicBookList bookList = new ClassicBookList();
        showPage(bookList, animate);
        return bookList;
    }

    @NonNull @Override
    public NoBooksUi switchToNoBooks(boolean animate) {
        ClassicNoBooksUi noBooks = new ClassicNoBooksUi();
        showPage(noBooks, animate);
        return noBooks;
    }

    @NonNull @Override
    public InitUi switchToInit() {
        ClassicInitUi init = new ClassicInitUi();
        showPage(init,false);
        return init;
    }

    @NonNull @Override
    public ClassicPlaybackUi switchToPlayback(boolean animate) {
        return new ClassicPlaybackUi(activity, this, animate);
    }

    @Override
    public void onPlaybackError(File path) {
        String message = activity.getString(R.string.playbackErrorToast, path.toString());
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    void showPlayback(@NonNull FragmentPlayback playbackUi, boolean animate) {
        showPage(playbackUi, animate);
    }

    private void showPage(@NonNull Fragment pageFragment, boolean animate) {
        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (animate)
            transaction.setCustomAnimations(R.animator.flip_right_in, R.animator.flip_right_out);
        transaction.replace(R.id.mainContainer, pageFragment);
        // Startup can occasionally fail due to an out-of-order call that interacts with
        // the Start/Stop/Resume stuff. I haven't figured out exactly what's wrong, but you
        // get a failure that some action cannot be taken after onSaveInstance is called.
        // In our case, there's no state to save, but there doesn't appear a way to say
        // that in onSaveInstance. However, we can avoid the failure here by calling the
        // "AllowingStateLoss" form of commit. It still has to be "Now". The failure is made
        // possible or more common when starting with the debugger when the screen is off.
        // onSaveInstance is overridden in MainActivity as a do-nothing.
        // See TO-DO above... it might help.
        transaction.commitNowAllowingStateLoss();
        //transaction.commitNow();
    }
}
