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
package com.donnKey.aesopPlayer.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import android.widget.Toast;

import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.R;

import java.util.Objects;

import static com.donnKey.aesopPlayer.ui.UiUtil.colorFromAttribute;

abstract class BaseSettingsFragment
        extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onStart() {
        super.onStart();
        getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        final ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        Preconditions.checkNotNull(Objects.requireNonNull(actionBar));
        actionBar.setTitle(getTitle());
        actionBar.setBackgroundDrawable(new ColorDrawable(colorFromAttribute(requireContext(),R.attr.actionBarBackground)));
    }

    @Override
    public void onStop() {
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onStop();
    }

    @NonNull
    SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(requireActivity());
    }

    @StringRes
    protected abstract int getTitle();

    void updateListPreferenceSummary(@NonNull SharedPreferences sharedPreferences,
                                     @NonNull String key,
                                     int default_value_res_id) {
        String stringValue = sharedPreferences.getString(key, getString(default_value_res_id));
        ListPreference preference =
                findPreference(key);
        int index = Objects.requireNonNull(preference).findIndexOfValue(stringValue);
        if (index < 0)
            index = 0;
        preference.setSummary(preference.getEntries()[index]);
    }

    static public void openUrl(Context context, @SuppressWarnings("SameParameterValue") @NonNull String url) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        try {
            context.startActivity(i);
        }
        catch(ActivityNotFoundException noActivity) {
            Toast.makeText(context,
                    R.string.pref_no_browser_toast, Toast.LENGTH_LONG).show();
        }
    }
}
