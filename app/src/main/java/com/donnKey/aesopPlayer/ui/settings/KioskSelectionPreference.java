/**
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
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.AesopPlayerApplication;

import javax.inject.Inject;

public class KioskSelectionPreference extends ListPreference // implements DialogInterface.OnClickListener
{
    @Inject public GlobalSettings globalSettings;

    @Nullable
    private OnNewValueListener listener;
    private KioskSettingsFragment.KioskPolicy[] kioskPolicies;

    public KioskSelectionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        AesopPlayerApplication.getComponent(context).inject(this);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return super.onGetDefaultValue(a, index);
    }

    public KioskSettingsFragment.KioskPolicy[] getPolicies() {
        return kioskPolicies;
    }

    public void setPolicies(KioskSettingsFragment.KioskPolicy[] policies) {
        kioskPolicies = policies;
    }

    public GlobalSettings.SettingsKioskMode getMode() {
        return globalSettings.getKioskMode();
    }

    void onDialogClosed(GlobalSettings.SettingsKioskMode newKioskMode) {
        if (listener != null) {
            listener.onNewValue(newKioskMode);
        }
    }

    void setOnNewValueListener(OnNewValueListener listener) {
        this.listener = listener;
    }

    public interface OnNewValueListener {
        void onNewValue(GlobalSettings.SettingsKioskMode newValue);
    }
}
