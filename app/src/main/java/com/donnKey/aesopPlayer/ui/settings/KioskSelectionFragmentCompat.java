/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2019 Donn S. Terry
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
package com.donnKey.aesopPlayer.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.R;

import java.util.Objects;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.donnKey.aesopPlayer.AesopPlayerApplication.getAppContext;

@SuppressWarnings("WeakerAccess") // Android requires public
public class KioskSelectionFragmentCompat extends PreferenceDialogFragmentCompat {
    KioskSettingsFragment.KioskPolicy[] kioskPolicies;
    GlobalSettings.SettingsKioskMode currentSelected;
    GlobalSettings.SettingsKioskMode originalSelected;
    KioskSelectionPreference fragment;
    CustomArrayAdapter listAdapter;

    static KioskSelectionFragmentCompat newInstance(@NonNull String key) {
        KioskSelectionFragmentCompat fragment = new KioskSelectionFragmentCompat();
        Bundle b = new Bundle(1);
        b.putString("key", key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    protected View onCreateDialogView(Context context) {
        fragment = (KioskSelectionPreference) getPreference();
        kioskPolicies = fragment.getPolicies();
        return super.onCreateDialogView(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Wide screen for popup
        currentSelected = fragment.getMode();
        originalSelected = currentSelected;
        WindowManager.LayoutParams params = Objects.requireNonNull(Objects.requireNonNull(getDialog()).getWindow()).getAttributes();
        params.width = MATCH_PARENT;
        Objects.requireNonNull(getDialog().getWindow()).setAttributes(params);
    }

    void deviceOwnerChanged() {
        currentSelected = GlobalSettings.SettingsKioskMode.NONE;
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDialogClosed(boolean isPositive) {
        if (isPositive && currentSelected != originalSelected) {
            ((KioskSelectionPreference) getPreference()).onDialogClosed(currentSelected);
        }
    }

    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        listAdapter = new CustomArrayAdapter(getContext(),
                R.layout.kiosk_dialog_listview,
                android.R.id.text1,
                getAppContext().getResources().getStringArray(R.array.kiosk_selection_entries));

        builder.setAdapter(listAdapter, this);
    }

    public class CustomArrayAdapter extends ArrayAdapter<CharSequence> {
        private final int SELECTION_SIZE = 4;

        public CustomArrayAdapter(Context context, int resource,
                                  int textViewResourceId, CharSequence[] objects) {
            super(context, resource, textViewResourceId, objects);
        }

        @Override
        public @NonNull
        View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            // Iterated through all positions during creation.

            View view = super.getView(position, convertView, parent);
            // text1 is set by defaults/xml
            TextView text1 = view.findViewById(android.R.id.text1);
            TextView text2 = view.findViewById(android.R.id.text2);
            RadioButton button = view.findViewById(R.id.k_selected);

            KioskSettingsFragment.KioskPolicy policy = kioskPolicies[position];
            text2.setText(policy.subTitle);
            if (!policy.possible) {
                text1.setTextColor(getResources().getColor(android.R.color.darker_gray));
                text2.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
            else {
                text1.setTextColor(getResources().getColor(android.R.color.white));
                text2.setTextColor(getResources().getColor(android.R.color.white));

            }
            button.setVisibility(policy.available? View.VISIBLE : View.INVISIBLE);

            button.setChecked(policy.kioskMode == currentSelected);

            view.setOnClickListener((v)-> {
                // Needed to make text and disabled button space un-clickable
            });

            // We have to build our own Click Listener because there's no way (that I can find)
            // to create two-line radio buttons where the RadioButtons are direct children
            // of the RadioGroup. Many of the "obvious" solutions fail because RadioButton cannot
            // be cast to a ViewGroup or ConstraintLayout.

            // Technically, 'position' would need to be adjusted for context, but since the
            // context is always the same 4 rows... Adding more rows might be a problem.
            button.setOnClickListener((v)-> {
                for (int i = 0; i < SELECTION_SIZE; i++) {
                    View v2  = parent.getChildAt(i);
                    RadioButton rb = v2.findViewById(R.id.k_selected);
                    if (kioskPolicies[i].slot == position) {
                        rb.setChecked(true);
                        currentSelected = kioskPolicies[i].kioskMode;
                    }
                    else {
                        rb.setChecked(false);
                    }
                }
            });

            return view;
        }
    }
}
