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

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.recyclerview.widget.RecyclerView;

import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.ui.UiUtil;

import static com.donnKey.aesopPlayer.ui.UiUtil.colorFromAttribute;

public class InventoryItemRecyclerViewAdapter extends RecyclerView.Adapter<InventoryItemRecyclerViewAdapter.ViewHolder> {
    private final Provisioning provisioning;
    private final InventoryItemFragment parentFragment;

    InventoryItemRecyclerViewAdapter(Provisioning provisioning,
                                     InventoryItemFragment parentFragment) {
        this.provisioning = provisioning;
        this.parentFragment = parentFragment;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_inventory_item, parent, false);
        return new ViewHolder(view);
    }

    // Particularly in debug mode, a LOT of stuff happens in the background here that we
    // don't control, so it's slow.
    @SuppressLint("DefaultLocale")
    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        // This can fire for rebuilt offscreen entries, and it sees "unchecked"
        holder.selected.setOnCheckedChangeListener(null);

        holder.book = provisioning.bookList[position].book;
        holder.bookTitle.setText(holder.book.getDisplayTitle());
        holder.view.setBackgroundColor(colorFromAttribute(holder.view.getContext(),
                holder.book.getColourScheme().backgroundColourAttrId));
        holder.bookDirectory.setText(holder.book.getDirectoryName());
        holder.bookCompleted.setVisibility(holder.book.getCompleted() ? View.VISIBLE : View.INVISIBLE);
        holder.currentPosition.setText(holder.book.thisBookProgress(holder.view.getContext()));
        holder.totalLength.setText(UiUtil.formatDuration(holder.book.getTotalDurationMs()));
        holder.selected.setChecked(provisioning.bookList[position].selected);
        holder.unWritable.setVisibility(provisioning.bookList[position].unWritable ? View.VISIBLE : View.GONE);
        int count = provisioning.bookList[position].book.duplicateIdCounter;
        if (count != 1) {
            holder.dupIdCounter.setText(String.format("%2d", count));
        }
        holder.dupIdCounter.setVisibility(count != 1? View.VISIBLE : View.GONE);

        holder.selected.setOnCheckedChangeListener((v,b) -> {
                provisioning.bookList[position].selected = b;
                if (!b) {
                    MenuItem all = parentFragment.optionsMenu.findItem(R.id.check_all);
                    AppCompatCheckBox allCheckBox = (AppCompatCheckBox) all.getActionView();
                    allCheckBox.setChecked(false);
                }
                // Not needed on later releases, but 4.4.4 needs it.
                notifyItemChanged(position);
            });

        holder.titleButton.setOnClickListener( (v) ->
        {
            CrashWrapper.log("PV: Re-title book from Inventory");
            ((ProvisioningActivity) parentFragment.requireActivity()).updateTitle(holder.book);
            provisioning.booksEvent();
        });

        holder.positionButton.setOnClickListener( (v) ->
        {
            CrashWrapper.log("PV: Adjust book time");
            ((ProvisioningActivity) parentFragment.requireActivity()).updateProgress(holder.book);
            provisioning.booksEvent();
        });
    }

    @Override
    public int getItemCount() {
        return provisioning.bookList.length;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final View view;
        final TextView bookTitle;
        final TextView bookDirectory;
        final TextView currentPosition;
        final TextView totalLength;
        final TextView bookCompleted;
        final TextView dupIdCounter;
        final Button titleButton;
        final Button positionButton;
        final CheckBox selected;
        final ImageView unWritable;
        AudioBook book;

        ViewHolder(View view) {
            super(view);
            this.view = view;
            this.bookTitle = view.findViewById(R.id.book_title);
            this.bookDirectory = view.findViewById(R.id.book_directory);
            this.selected = view.findViewById(R.id.selected);
            this.unWritable = view.findViewById(R.id.un_writable);
            this.currentPosition = view.findViewById(R.id.current_position);
            this.totalLength = view.findViewById(R.id.total_length);
            this.bookCompleted = view.findViewById(R.id.book_completed);
            this.titleButton = view.findViewById(R.id.title_button);
            this.positionButton = view.findViewById(R.id.position_button);
            this.dupIdCounter = view.findViewById(R.id.book_count_overlay);
        }

        @NonNull
        @Override
        public String toString() {
            return super.toString() + " '" + bookTitle.getText() + "'";
        }
    }
}
