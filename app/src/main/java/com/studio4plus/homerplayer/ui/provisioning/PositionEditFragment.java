package com.studio4plus.homerplayer.ui.provisioning;

import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.model.AudioBook;
import com.studio4plus.homerplayer.ui.UiUtil;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;

public class PositionEditFragment extends Fragment {
    private AppCompatEditText editor;
    private Provisioning provisioning;
    private AudioBook book;

    public PositionEditFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.provisioning = ViewModelProviders.of(Objects.requireNonNull(getActivity())).get(Provisioning.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        book = (AudioBook)provisioning.fragmentParameter;
        View view = inflater.inflate(R.layout.fragment_position_edit, container, false);

        ActionBar actionBar = ((AppCompatActivity) Objects.requireNonNull(getActivity())).getSupportActionBar();
        Preconditions.checkNotNull(actionBar);
        actionBar.setTitle(provisioning.windowTitle);
        actionBar.setSubtitle(R.string.fragment_subtitle_adjust_position);

        TextView title = view.findViewById(R.id.book_title);
        TextView oldDuration = view.findViewById(R.id.old_duration);
        Button doneButton = view.findViewById(R.id.button_done);
        Button cancelButton = view.findViewById(R.id.button_cancel);
        editor = view.findViewById(R.id.edit_duration);

        AudioBook.Position position = book.getLastPosition();
        long currentMs = book.getLastPositionTime(position.seekPosition);

        ((ProvisioningActivity) Objects.requireNonNull(getActivity())).navigation.
                setVisibility(View.GONE);

        title.setText(book.getTitle());

        String positionStr = UiUtil.formatDurationShort(currentMs);
        positionStr = getString(R.string.pref_current_position_note, positionStr);
        oldDuration.setText(positionStr);

        editor.setSingleLine(true);
        editor.setFilters(new InputFilter[]{filter});
        editor.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                if (event != null
                    && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER)
                ) {
                    InputMethodManager imm = (InputMethodManager)
                            Objects.requireNonNull(getActivity()).getSystemService(Context.INPUT_METHOD_SERVICE);

                    imm.hideSoftInputFromWindow(v.getApplicationWindowToken(), 0);
                    actionId = EditorInfo.IME_ACTION_DONE;
                }
            }
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String durationString = Objects.requireNonNull(editor.getText()).toString();
                if (durationString.length() != 0)
                {
                    long duration = timeToMillis(durationString);
                    editor.setText(UiUtil.formatDurationShort(duration));
                }
                // We didn't process it, just a side-effect, so return false
            }
            return false;
        });

        doneButton.setOnClickListener((v) -> setNewTime());

        cancelButton.setOnClickListener((v) -> {
            FragmentManager fragmentManager = Objects.requireNonNull(getActivity()).getSupportFragmentManager();
            fragmentManager.popBackStack();
        });

        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // This has to happen late (after layout is finished) to get the keyboard up immediately.
        editor.requestFocus();
        InputMethodManager imm = (InputMethodManager)
                Objects.requireNonNull(getActivity()).getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editor, InputMethodManager.SHOW_FORCED);
    }

    @Override
    public void onStop() {
        // Defer below until stop so we catch ALL stops
        // Method for hiding the keyboard is obscure at best, but it works
        InputMethodManager imm = (InputMethodManager)
                Objects.requireNonNull(getActivity()).getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        super.onStop();
    }

    private long timeToMillis(String durationString) {
        // We know this string's format fairly accurately due to the filter below. However,
        // we can't use the time scanner because there are a few books longer than 24 hours.
        if (durationString.length() == 0)
        {
            // Ignore it.
            return 0;
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
            break;
        }
        if (duration > 35000) {
            // A number slightly larger than that will overflow when converted to Millis, so
            // don't allow the overflow. (That's a bit under 600 hours.)
            duration = 35000;
        }
        return TimeUnit.MINUTES.toMillis(duration);
    }

    private void setNewTime() {

        String durationString = Objects.requireNonNull(editor.getText()).toString();

        if (durationString.length() == 0)
        {
            // Ignore it.
            return;
        }

        long durationMs = timeToMillis(durationString);

        book.updatePosition(durationMs);
        book.setCompleted(false);

        FragmentManager fragmentManager = Objects.requireNonNull(getActivity()).getSupportFragmentManager();
        fragmentManager.popBackStack();
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
