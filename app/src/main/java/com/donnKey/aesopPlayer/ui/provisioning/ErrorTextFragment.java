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

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.R;

import java.util.Objects;

import javax.inject.Inject;

import static com.donnKey.aesopPlayer.ui.UiUtil.colorFromAttribute;

public class ErrorTextFragment extends Fragment {
    @Inject
    public Provisioning provisioning;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        AesopPlayerApplication.getComponent(requireContext()).inject(this);
        View view = inflater.inflate(R.layout.error_text_fragment, container, false);

        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        Objects.requireNonNull(actionBar);
        actionBar.setTitle(provisioning.windowTitle);
        actionBar.setSubtitle(provisioning.errorTitle);
        actionBar.setBackgroundDrawable(new ColorDrawable(colorFromAttribute(requireContext(),R.attr.actionBarBackground)));

        ArrayAdapter<Provisioning.ErrorInfo> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, provisioning.errorLogs);
        ListView listView = view.findViewById(R.id.string_list);
        listView.setAdapter(adapter);

        ((ProvisioningActivity) requireActivity()).navigation.
                setVisibility(View.GONE);

        return view;
    }
}
