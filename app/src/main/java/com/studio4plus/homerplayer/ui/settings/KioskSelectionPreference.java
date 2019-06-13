package com.studio4plus.homerplayer.ui.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.HomerPlayerApplication;

import javax.inject.Inject;

public class KioskSelectionPreference extends ListPreference // implements DialogInterface.OnClickListener
{
    @Inject public GlobalSettings globalSettings;

    @Nullable
    private OnNewValueListener listener;
    private KioskSettingsFragment.KioskPolicy[] kioskPolicies;

    public KioskSelectionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        HomerPlayerApplication.getComponent(context).inject(this);
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
