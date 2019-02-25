package com.studio4plus.homerplayer.ui.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.ui.UiUtil;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.preference.PreferenceDialogFragmentCompat;

public class DurationDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    private AppCompatEditText editor;

    @Override
    protected View onCreateDialogView(Context context) {
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        @SuppressLint("InflateParams")
        View durationView = layoutInflater.inflate(R.layout.preference_dialog_duration, null);
        editor = durationView.findViewById(R.id.editDuration);

        // This is ugly, but it appears to work around an ancient bug: you can't (reliably?)
        // get the keyboard to come up by setting showSoftInput directly at this point, but
        // if you delay a bit it works fine. Even doing it in onResume isn't late enough to fix it.
        // (No, the xml layout doesn't work for this, either.)
        // (From close observation, it might be the case that the keyboard comes up and then
        // is immediately removed, given the way the screen moves when entering the dialog.)
        InputMethodManager imm = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        Handler h = new Handler();
        h.postDelayed(() -> imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT), 250);

        long duration = ((DurationDialogPreference) getPreference()).getValue();
        String durationStr = UiUtil.formatDurationShort(duration);
        durationStr = getString(R.string.pref_current_position_note, durationStr);
        TextView oldDuration = durationView.findViewById(R.id.oldDuration);
        oldDuration.setText(durationStr);

        editor.setSingleLine(true);
        editor.setFilters(new InputFilter[]{filter});

        FrameLayout dialogView = new FrameLayout(context);
        dialogView.addView(durationView);

        return dialogView;
    }

    public static DurationDialogFragmentCompat newInstance(@NonNull String key) {
        DurationDialogFragmentCompat fragment = new DurationDialogFragmentCompat();
        Bundle b = new Bundle(1);
        b.putString("key", key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onDialogClosed(boolean isPositive) {
        DurationDialogPreference durationDialogPreference = (DurationDialogPreference) getPreference();
        if (!isPositive) {
            durationDialogPreference.onDialogClosed(false);
            return;
        }

        String durationString = Objects.requireNonNull(editor.getText()).toString();
        // We know this string's format fairly accurately due to the filter below. However,
        // we can't use the time scanner because there are a few books longer than 24 hours.

        if (durationString.length() == 0)
        {
            // Ignore it.
            durationDialogPreference.onDialogClosed(false);
            return;
        }

        // Ask split to retain a trailing empty string ("hh:" in our usage)
        String parts[] = durationString.split(":", -1);

        long duration = 0;
        switch (parts.length) {
        case 2:
            // ':' present - it's hh:mm (either could be empty)
            if (parts[0].length() > 0) {
                duration = Long.valueOf(parts[0]) * 60;
            }
            if (parts[1].length() > 0) {
                duration += Long.valueOf(parts[1]);
            }
            break;
        case 1:
            // no ':' - it's just mm (or mmm)
            if (parts[0].length() > 0) {
                duration = Long.valueOf(parts[0]); // in minutes
            }
            break;
        default:
            // nothing or nonsense (should be impossible given the filter): zero
            duration = 0;
            break;
        }
        if (duration > 35000) {
            // A number slightly larger than that will overflow when converted to Millis, so
            // don't allow the overflow. (That's a bit under 600 hours.)
            duration = 35000;
        }
        long durationMs = TimeUnit.MINUTES.toMillis(duration);

        durationDialogPreference.setValue(durationMs);
        durationDialogPreference.onDialogClosed(true);
    }

    private static final Pattern legalDuration = Pattern.compile("^\\d*(:[0-5]?\\d?)?$");
    // Without a colon, any number is minutes.
    // With a colon, any number of hours (including none), and none-60 minutes.
    // (We'll interpret 0 and 1 digit minutes as if they were 2-digit with zero[s].)

    private final InputFilter filter = (source, start, end, dest, dStart, dEnd) -> {
        if (source.equals("")) {
            return null; // for backspace
        }
        StringBuilder builder = new StringBuilder(dest);
        builder.replace(dStart, dEnd, source.subSequence(start, end).toString());

        Matcher matcher = legalDuration.matcher(builder);
        if (matcher.matches()) {
            return null;
        }
        else {
            return "";
        }
    };
}
