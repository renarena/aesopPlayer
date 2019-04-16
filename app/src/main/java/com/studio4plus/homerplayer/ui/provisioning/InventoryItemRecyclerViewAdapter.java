package com.studio4plus.homerplayer.ui.provisioning;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.model.AudioBook;
import com.studio4plus.homerplayer.ui.UiUtil;

import java.util.Objects;

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
    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        // This can fire for rebuilt offscreen entries, and it sees "unchecked"
        holder.selected.setOnCheckedChangeListener(null);

        holder.book = provisioning.bookList[position].book;
        holder.bookTitle.setText(holder.book.getTitle());
        holder.view.setBackgroundColor(holder.book.getColourScheme().backgroundColour);
        holder.bookDirectory.setText(holder.book.getDirectoryName());
        holder.bookCompleted.setVisibility(holder.book.getCompleted() ? View.VISIBLE : View.INVISIBLE);
        holder.currentPosition.setText(holder.book.thisBookProgress(holder.view.getContext()));
        holder.totalLength.setText(UiUtil.formatDuration(holder.book.getTotalDurationMs()));
        holder.selected.setChecked(provisioning.bookList[position].selected);
        holder.unWritable.setVisibility(provisioning.bookList[position].unWritable ? View.VISIBLE : View.GONE);

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
            Objects.requireNonNull((ProvisioningActivity) parentFragment.getActivity())
                    .updateTitle(holder.book);
            provisioning.booksEvent();
        });

        holder.positionButton.setOnClickListener( (v) ->
        {
            Objects.requireNonNull((ProvisioningActivity) parentFragment.getActivity())
                    .updateProgress(holder.book);
            provisioning.booksEvent();
        });
    }

    @Override
    public int getItemCount() {
        return provisioning.bookList.length;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        final View view;
        final TextView bookTitle;
        final TextView bookDirectory;
        final TextView currentPosition;
        final TextView totalLength;
        final TextView bookCompleted;
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
        }

        @NonNull
        @Override
        public String toString() {
            return super.toString() + " '" + bookTitle.getText() + "'";
        }
    }
}
