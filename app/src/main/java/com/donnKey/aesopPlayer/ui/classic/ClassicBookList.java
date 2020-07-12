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

import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.analytics.AnalyticsTracker;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.ui.UiControllerBookList;
import com.donnKey.aesopPlayer.ui.BookListUi;
import com.donnKey.aesopPlayer.ui.HintOverlay;
import com.donnKey.aesopPlayer.ui.UiUtil;

import java.util.List;

import javax.inject.Inject;

public class ClassicBookList extends Fragment implements BookListUi {

    private View view;
    private ViewPager bookPager;
    private BookListPagerAdapter bookAdapter;
    private UiControllerBookList uiControllerBookList;

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private UiUtil.SnoozeDisplay snooze;

    @SuppressWarnings("WeakerAccess")
    @Inject public AnalyticsTracker analyticsTracker;
    @SuppressWarnings("WeakerAccess")
    @Inject public GlobalSettings globalSettings;

    // TODO: Fix accessibility issue on setOnTouchListener below if multi-tap remains.
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_book_list, container, false);
        AesopPlayerApplication.getComponent(view.getContext()).inject(this);

        // This should be early so no buttons go live before this
        snooze = new UiUtil.SnoozeDisplay(this, view, globalSettings);

        bookPager = view.findViewById(R.id.bookListPager);
        bookPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            int currentViewIndex;
            String currentBookId;

            @Override
            public void onPageSelected(int index) {
                FragmentBookItem itemFragment = (FragmentBookItem) bookAdapter.getItem(index);
                if (!itemFragment.getAudioBookId().equals(currentBookId)) {
                    currentBookId = itemFragment.getAudioBookId();
                    uiControllerBookList.changeBook(currentBookId);
                }
                currentViewIndex = index;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager.SCROLL_STATE_IDLE) {
                    int adjustedIndex = bookAdapter.wrapViewIndex(currentViewIndex);
                    if (adjustedIndex != currentViewIndex)
                        bookPager.setCurrentItem(adjustedIndex, false);
                    analyticsTracker.onBookSwiped();
                }
            }
        });

        /* Doesn't work here with big buttons
        final Context context = view.getContext();
        bookPager.setOnTouchListener(new MultitapTouchListener(
                context, () -> startActivity(new Intent(context, SettingsActivity.class))));
        */

        return view;
    }

    @Override
    public void updateBookList(List<AudioBook> audioBooks, int currentBookIndex) {
        bookAdapter = new BookListPagerAdapter(getChildFragmentManager(), audioBooks);
        bookPager.setAdapter(bookAdapter);
        bookPager.setCurrentItem(
                bookAdapter.bookIndexToViewIndex(currentBookIndex), false);
    }

    @Override
    public void updateCurrentBook(int currentBookId) {
        // Simply calls updateBookList (above) with appropriate params.
        uiControllerBookList.updateAudioBooks();
    }

    @Override
    public void initWithController(UiControllerBookList uiControllerBookList) {
        this.uiControllerBookList = uiControllerBookList;
    }

    @Override
    public void onResume() {
        super.onResume();
        CrashWrapper.log("UI: ClassicBookList fragment resumed");
        showHintsIfNecessary();
    }

    private void showHintsIfNecessary() {
        if (isResumed() && isVisible() && !isAnyHintVisible()) {
            if (!globalSettings.browsingHintShown()) {
                HintOverlay overlay = new HintOverlay(
                        view, R.id.browseHintOverlayStub, R.string.hint_browsing,
                        R.drawable.hint_horizontal_swipe, globalSettings::setBrowsingHintShown);
                overlay.show();
        // TODO: there's more, but this is the primary thing to disable the 5-tap help
        // (This doesn't apply with the gear-icon based settings stuff).
        /*    } else if (!globalSettings.settingsHintShown()) {
                HintOverlay overlay = new HintOverlay(
                        view, R.id.settingsHintOverlayStub, R.string.hint_settings,
                        R.drawable.hint_tap, globalSettings::setSettingsHintShown);
                overlay.show();
                */
            }
        }
    }

    private boolean isAnyHintVisible() {
        ViewStub browseHintStub = view.findViewById(R.id.browseHintOverlayStub);
        ViewStub settingsHintStub = view.findViewById(R.id.settingsHintOverlayStub);
        return  browseHintStub == null || settingsHintStub == null;
    }

    private class BookListPagerAdapter extends FragmentStatePagerAdapter {

        private static final int OFFSET = 1;

        private final @NonNull List<AudioBook> audioBooks;

        BookListPagerAdapter(@NonNull FragmentManager fm, @NonNull List<AudioBook> audioBooks) {
            super(fm,  BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.audioBooks = audioBooks;
        }

        int getBookIndex(int viewIndex) {
            int bookIndex = viewIndex - OFFSET;
            if (bookIndex < 0)
                return audioBooks.size() + bookIndex;
            else
                return bookIndex % audioBooks.size();
        }

        @NonNull
        @Override
        public Fragment getItem(int viewIndex) {
            int bookIndex = getBookIndex(viewIndex);
            FragmentBookItem item = FragmentBookItem.newInstance(audioBooks.get(bookIndex).getId());
            item.setController(uiControllerBookList);
            return item;
        }

        @Override
        public int getCount() {
            return audioBooks.size() > 0 ? audioBooks.size() + 2*OFFSET : 0;
        }

        int bookIndexToViewIndex(int bookIndex) {
            return bookIndex + OFFSET;
        }

        int wrapViewIndex(int viewIndex) {
            if (viewIndex < OFFSET) {
                viewIndex += audioBooks.size();
            } else {
                final int audioBookCount = audioBooks.size();
                final int lastBookIndex = audioBookCount - 1;
                if (viewIndex - OFFSET > lastBookIndex)
                    viewIndex -= audioBookCount;
            }
            return viewIndex;
        }
    }
}
