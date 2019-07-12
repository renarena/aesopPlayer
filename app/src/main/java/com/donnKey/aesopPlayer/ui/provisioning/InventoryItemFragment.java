package com.donnKey.aesopPlayer.ui.provisioning;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.model.AudioBookManager;
import com.donnKey.aesopPlayer.ui.UiUtil;

import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

/* An inventory of current books, displaying completion status and position and total time
 */
public class InventoryItemFragment extends Fragment {
    @SuppressWarnings("WeakerAccess")
    @Inject
    public GlobalSettings globalSettings;
    @SuppressWarnings("WeakerAccess")
    @Inject
    public AudioBookManager audioBookManager;
    @SuppressWarnings("WeakerAccess")
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

        this.provisioning = ViewModelProviders.of(Objects.requireNonNull(getActivity())).get(Provisioning.class);
        actionBar = ((AppCompatActivity) Objects.requireNonNull(getActivity())).getSupportActionBar();

        if (provisioning.bookList == null) {
            // already done; redo (below) if things change enough
            provisioning.buildBookList();
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

        ((ProvisioningActivity) Objects.requireNonNull(getActivity())).navigation.
                setVisibility(View.VISIBLE);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        provisioning.setListener(() -> handler.post(this::booksChanged));

        actionBar.setTitle(provisioning.windowTitle);
        setTotalSubtitle();
        actionBar.setSubtitle(provisioning.windowSubTitle);

        ((ProvisioningActivity) Objects.requireNonNull(getActivity())).activeInventoryFragment = this;
    }

    @Override
    public void onPause() {
        ((ProvisioningActivity) Objects.requireNonNull(getActivity())).activeInventoryFragment = null;
        provisioning.setListener(null);
        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.inventory_action_bar, menu);
        this.optionsMenu = menu;

        MenuItem all = menu.findItem(R.id.check_all);
        AppCompatCheckBox allCheckBox = (AppCompatCheckBox) MenuItemCompat.getActionView(all);
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
            new AlertDialog.Builder(Objects.requireNonNull(this.getContext()))
                    .setTitle(getString(R.string.dialog_title_reset_books_position))
                    .setIcon(R.mipmap.ic_launcher)
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
            new AlertDialog.Builder(Objects.requireNonNull(this.getContext()))
                    .setTitle(title)
                    .setIcon(R.mipmap.ic_launcher)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> deleteAllSelected())
                    .setNegativeButton(android.R.string.no, null)
                    .show();
            return true;
        }
        case R.id.archive_books: {
            new AlertDialog.Builder(Objects.requireNonNull(this.getContext()))
                    .setTitle(getString(R.string.dialog_title_archive_policy))
                    .setIcon(R.mipmap.ic_launcher)
                    .setMessage(String.format(getString(R.string.dialog_archive_policy), audioBooksDirectoryName))
                    .setPositiveButton(getString(R.string.dialog_policy_yes), (dialog, whichButton) -> {
                        item.setChecked(true);
                        globalSettings.setArchiveBooks(true);
                        Objects.requireNonNull(getActivity()).invalidateOptionsMenu(); // see onPrepare... below
                    })
                    .setNegativeButton(getString(R.string.dialog_policy_no), (dialog, whichButton) -> {
                        item.setChecked(false);
                        globalSettings.setArchiveBooks(false);
                        Objects.requireNonNull(getActivity()).invalidateOptionsMenu(); // see onPrepare... below
                    })
                    .show();
            return true;
        }
        }
        return super.onOptionsItemSelected(item);
    }

    private void booksChanged() {
        provisioning.buildBookList();
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
        ((ProvisioningActivity) Objects.requireNonNull(getActivity())).deleteAllSelected();
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
        provisioning.windowSubTitle = String.format(
                getString(R.string.fragment_subtitle_total_length),
                provisioning.partiallyUnknown ?
                        getString(R.string.fragment_subtitle_total_length_greater_than) : "",
                UiUtil.formatDuration(provisioning.totalTime));
    }
}
