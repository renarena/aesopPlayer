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
package com.donnKey.aesopPlayer.ui.provisioning;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.donnKey.aesopPlayer.R;

import java.util.List;

class DirectoriesViewAdapter extends BaseAdapter {
    private final List<String> directories;
    private final LayoutInflater layoutInflater;
    private final OnClickButtonListener nameClickListener;
    private final OnClickButtonListener deleteClickListener;

    DirectoriesViewAdapter(Activity activity, List<String> directories,
                           @NonNull OnClickButtonListener nameClickListener,
                           @NonNull OnClickButtonListener deleteClickListener){
        this.directories = directories;
        layoutInflater = activity.getLayoutInflater();
        this.nameClickListener = nameClickListener;
        this.deleteClickListener = deleteClickListener;
    }

    @Override
    public int getCount() {
        // Ignore the 0th element since it's already on-screen as the current one
        return directories.size()-1;
    }

    @Override
    public String getItem(int position) {
        // Ignore the 0th element since it's already on-screen as the current one
        return directories.get(position+1);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @SuppressLint("InflateParams") // False error, null is correct for dialogs
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = layoutInflater.inflate(R.layout.download_dirs_list_item, null);
            holder = new ViewHolder();
            holder.dirName = convertView.findViewById(R.id.directory_name);
            holder.delName = convertView.findViewById(R.id.del);
            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }

        // bind data
        holder.dirName.setText(getItem(position));
        holder.dirName.setOnClickListener((v)-> nameClickListener.onClickButton(getItem(position)));
        holder.delName.setOnClickListener((v)-> deleteClickListener.onClickButton(getItem(position)));
        return convertView;
    }

    private static class ViewHolder{
        private TextView dirName;
        private ImageView delName;
    }

    public interface OnClickButtonListener{
        void onClickButton(String dirName);
    }
}
