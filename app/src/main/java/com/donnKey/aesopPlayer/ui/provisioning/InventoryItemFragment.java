/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2020 Donn S. Terry
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
package com.donnKey.aesopPlayer.ui.provisioning;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.model.AudioBookManager;

import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

import static com.donnKey.aesopPlayer.ui.UiUtil.colorFromAttribute;

/* An inventory of current books, displaying completion status and position and total time
 */
public class InventoryItemFragment extends Fragment {
    @Inject
    public GlobalSettings globalSettings;
    @Inject
    public AudioBookManager audioBookManager;
    @Inject
    @Named("AUDIOBOOKS_DIRECTORY")
    public String audioBooksDirectoryName;

    Menu optionsMenu;
    private Provisioning provisioning;
    private InventoryItemRecyclerViewAdapter recycler;
    private ActionBar actionBar;

    private final Handler handler = new Handler();

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public InventoryItemFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inventory_item_list, container, false);
        AesopPlayerApplication.getComponent(view.getContext()).inject(this);

        this.provisioning = Provisioning.getInstance();
        actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        Objects.requireNonNull(actionBar).setBackgroundDrawable(new ColorDrawable(colorFromAttribute(requireContext(),R.attr.actionBarBackground)));

        if (provisioning.bookList == null) {
            // already done; redo (below) if things change enough
            provisioning.buildBookList();
            provisioning.selectCompletedBooks();
        }

        setHasOptionsMenu(true);

        // Set the adapter
        if (view instanceof RecyclerView) {
            Context context = view.getContext();
            RecyclerView recyclerView = (RecyclerView) view;
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
            recycler = new InventoryItemRecyclerViewAdapter(provisioning, this);
            recyclerView.setAdapter(recycler);
        }

        provisioning.windowTitle = getString(R.string.fragment_title_inventory);
        setTotalSubtitle();

        ((ProvisioningActivity) requireActivity()).navigation.
                setVisibility(View.VISIBLE);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        provisioning.setListener(() -> handler.post(this::booksChanged));

        setTotalSubtitle();

        View actionBarTitleFrame = actionBar.getCustomView();
        actionBarTitleFrame.setClickable(false);
        TextView clickableTitle = actionBarTitleFrame.findViewById(R.id.title);
        clickableTitle.setText(provisioning.windowTitle);
        TextView clickableSubTitle = actionBarTitleFrame.findViewById(R.id.subtitle);
        clickableSubTitle.setText(provisioning.windowSubTitle);
        ImageView downIcon = actionBarTitleFrame.findViewById(R.id.downIcon);
        downIcon.setVisibility(View.GONE);

        ((ProvisioningActivity) requireActivity()).activeInventoryFragment = this;
    }

    @Override
    public void onPause() {
        ((ProvisioningActivity) requireActivity()).activeInventoryFragment = null;
        provisioning.setListener(null);
        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.inventory_action_bar, menu);
        this.optionsMenu = menu;

        MenuItem all = menu.findItem(R.id.check_all);
        AppCompatCheckBox allCheckBox = (AppCompatCheckBox) all.getActionView();
        allCheckBox.setText(getString(R.string.action_bar_word_all));
        allCheckBox.setOnCheckedChangeListener((v, b) -> {
                    if (v.isPressed()) {
                        setAllSelected(b);
                    }

                    allCheckBox.setChecked(b);
                    // The following otherwise pointless line makes the check box display it's current
                    // checked state correctly. On most releases it isn't necessary, but on (my)
                    // 4.4.4 (API20) it's needed, and doesn't do what it looks like it does.
                    all.setIcon(R.drawable.battery_0);
                }
        );

        MenuItem archiveBox = menu.findItem(R.id.archive_books);
        archiveBox.setChecked(globalSettings.getArchiveBooks());

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
        case R.id.reset_books: {
            int count = getSelectedCount();
            Resources res = getResources();
            String books = res.getQuantityString(R.plurals.numberOfBooks, count, count);
            new AlertDialog.Builder(this.requireContext())
                    .setTitle(getString(R.string.dialog_title_reset_books_position))
                    .setIcon(R.drawable.ic_launcher)
                    .setMessage(String.format(getString(R.string.dialog_ok_to_rewind), books))
                    .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> rewindAllSelected())
                    .setNegativeButton(android.R.string.no, null)
                    .show();
            return true;
        }
        case R.id.delete_books: {
            int count = getSelectedCount();
            Resources res = getResources();
            String books = res.getQuantityString(R.plurals.numberOfBooks, count, count);
            String message;
            String title;
            if (globalSettings.getArchiveBooks()) {
                title = getString(R.string.dialog_title_archive_books);
                message = String.format(getString(R.string.dialog_ok_to_move), books, audioBooksDirectoryName);
            }
            else {
                title = getString(R.string.dialog_title_delete_books);
                message = String.format(getString(R.string.dialog_ok_to_delete), books);
            }
            new AlertDialog.Builder(this.requireContext())
                    .setTitle(title)
                    .setIcon(R.drawable.ic_launcher)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> deleteAllSelected())
                    .setNegativeButton(android.R.string.no, null)
                    .show();
            return true;
        }
        case R.id.archive_books: {
            new AlertDialog.Builder(this.requireContext())
                    .setTitle(getString(R.string.dialog_title_archive_policy))
                    .setIcon(R.drawable.ic_launcher)
                    .setMessage(String.format(getString(R.string.dialog_archive_policy), audioBooksDirectoryName))
                    .setPositiveButton(getString(R.string.dialog_policy_yes), (dialog, whichButton) -> {
                        item.setChecked(true);
                        globalSettings.setArchiveBooks(true);
                        requireActivity().invalidateOptionsMenu(); // see onPrepare... below
                    })
                    .setNegativeButton(getString(R.string.dialog_policy_no), (dialog, whichButton) -> {
                        item.setChecked(false);
                        globalSettings.setArchiveBooks(false);
                        requireActivity().invalidateOptionsMenu(); // see onPrepare... below
                    })
                    .show();
            return true;
        }
        }
        return super.onOptionsItemSelected(item);
    }

    private void booksChanged() {
        provisioning.buildBookList();
        provisioning.selectCompletedBooks();
        recycler.notifyDataSetChanged();
        if (this.isVisible()) {
            setTotalSubtitle();
            actionBar.setSubtitle(provisioning.windowSubTitle);
        }
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);

        MenuItem deleteBooks = menu.findItem(R.id.delete_books);
        if (globalSettings.getArchiveBooks()) {
            deleteBooks.setIcon(R.drawable.ic_baseline_delete_24px);
        }
        else {
            deleteBooks.setIcon(R.drawable.ic_baseline_delete_forever_24px);
        }
    }

    private void deleteAllSelected() {
        CrashWrapper.log("PV: Delete Books");
        ((ProvisioningActivity) requireActivity()).deleteAllSelected();
    }

    void notifyDataSetChanged() {
        recycler.notifyDataSetChanged();
    }

    void refreshSubtitle() {
        setTotalSubtitle();
        actionBar.setSubtitle(provisioning.windowSubTitle);
    }

    private void rewindAllSelected()
    {
        CrashWrapper.log("PV: Rewind Books");
        for (Provisioning.BookInfo b : provisioning.bookList) {
            if (b.selected)
            {
                b.book.resetPosition();
                b.book.setCompleted(false);
                b.selected = false;
            }
        }
        recycler.notifyItemRangeChanged(0, provisioning.bookList.length);
    }

    private int getSelectedCount() {
        int count = 0;
        for (Provisioning.BookInfo b : provisioning.bookList) {
            if (b.selected) count++;
        }
        return count;
    }

    private void setAllSelected(boolean f)
    {
        for (Provisioning.BookInfo b : provisioning.bookList) {
            b.selected = f;
        }
        recycler.notifyItemRangeChanged(0, provisioning.bookList.length);
    }

    private void setTotalSubtitle() {
        provisioning.windowSubTitle = provisioning.getTotalTimeSubtitle();
    }
}
