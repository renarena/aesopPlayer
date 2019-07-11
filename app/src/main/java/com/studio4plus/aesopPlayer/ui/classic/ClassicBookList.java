package com.studio4plus.aesopPlayer.ui.classic;

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

import com.crashlytics.android.Crashlytics;
import com.studio4plus.aesopPlayer.AesopPlayerApplication;
import com.studio4plus.aesopPlayer.GlobalSettings;
import com.studio4plus.aesopPlayer.R;
import com.studio4plus.aesopPlayer.analytics.AnalyticsTracker;
import com.studio4plus.aesopPlayer.model.AudioBook;
import com.studio4plus.aesopPlayer.ui.UiControllerBookList;
import com.studio4plus.aesopPlayer.ui.BookListUi;
import com.studio4plus.aesopPlayer.ui.HintOverlay;
import com.studio4plus.aesopPlayer.ui.UiUtil;

import java.util.List;

import javax.inject.Inject;

public class ClassicBookList extends Fragment implements BookListUi {

    private View view;
    private ViewPager bookPager;
    private BookListPagerAdapter bookAdapter;
    private UiControllerBookList uiControllerBookList;

    @SuppressWarnings("WeakerAccess")
    @Inject public AnalyticsTracker analyticsTracker;
    @SuppressWarnings("WeakerAccess")
    @Inject public GlobalSettings globalSettings;

    // TODO: Fix accessibility issie on setOnTouchListener below if multi-tap remains.
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_book_list, container, false);
        AesopPlayerApplication.getComponent(view.getContext()).inject(this);

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
        // Don't snooze - we just want to update the total time
        UiUtil.SnoozeDisplay.suspend();
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
        Crashlytics.log("UI: ClassicBookList fragment resumed");
        showHintsIfNecessary();
    }

    private void showHintsIfNecessary() {
        if (isResumed() && isVisible() && !isAnyHintVisible()) {
            if (!globalSettings.browsingHintShown()) {
                HintOverlay overlay = new HintOverlay(
                        view, R.id.browseHintOverlayStub, R.string.hint_browsing, R.drawable.hint_horizontal_swipe);
                overlay.show();
                globalSettings.setBrowsingHintShown();
        // TODO: there's more, but this is the primary thing to disable the 5-tap help
        // (This doesn't apply with the gear-icon based settings stuff).
        /*    } else if (!globalSettings.settingsHintShown()) {
                HintOverlay overlay = new HintOverlay(
                        view, R.id.settingsHintOverlayStub, R.string.hint_settings, R.drawable.hint_tap);
                overlay.show();
                globalSettings.setSettingsHintShown();*/
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
            super(fm);
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
