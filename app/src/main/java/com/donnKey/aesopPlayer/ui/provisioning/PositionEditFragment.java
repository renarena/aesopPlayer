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

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
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

import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.model.BookPosition;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import static com.donnKey.aesopPlayer.ui.UiUtil.colorFromAttribute;
import static com.donnKey.aesopPlayer.ui.UiUtil.formatDurationShort;

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
        this.provisioning = Provisioning.getInstance();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        book = (AudioBook)provisioning.fragmentParameter;
        View view = inflater.inflate(R.layout.fragment_position_edit, container, false);

        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        Objects.requireNonNull(actionBar);
        actionBar.setTitle(provisioning.windowTitle);
        actionBar.setSubtitle(R.string.fragment_subtitle_adjust_position);
        actionBar.setBackgroundDrawable(new ColorDrawable(colorFromAttribute(requireContext(),R.attr.actionBarBackground)));

        TextView title = view.findViewById(R.id.book_title);
        TextView oldDuration = view.findViewById(R.id.old_duration);
        Button doneButton = view.findViewById(R.id.button_done);
        Button cancelButton = view.findViewById(R.id.button_cancel);
        TextView stopList = view.findViewById(R.id.stopPoints);
        editor = view.findViewById(R.id.edit_duration);

        BookPosition position = book.getLastPosition();
        long currentTotalMs = book.toMs(position);

        ((ProvisioningActivity) requireActivity()).navigation.
                setVisibility(View.GONE);

        title.setText(book.getDisplayTitle());

        String positionStr = formatDurationShort(currentTotalMs);
        positionStr = getString(R.string.pref_current_position_note, positionStr);
        oldDuration.setText(positionStr);

        List<Long> stops = book.getBookStops();
        StringBuilder stopTimes = new StringBuilder();
        if (stops != null) {
            for (Long s : stops) {
                if (s == 0) {
                    continue;
                }
                if (!stopTimes.toString().equals("")) {
                    stopTimes.append(",  ");
                }
                stopTimes.append(formatDurationShort(s));
            }
        }
        stopTimes.append(" - ").append(formatDurationShort(book.getMaxPosition()));
        stopList.setText(stopTimes.toString());

        editor.setSingleLine(true);
        editor.setFilters(new InputFilter[]{filter});
        editor.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                if (event != null
                    && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER)
                ) {
                    InputMethodManager imm = (InputMethodManager)
                            requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);

                    Objects.requireNonNull(imm);
                    imm.hideSoftInputFromWindow(v.getApplicationWindowToken(), 0);
                    actionId = EditorInfo.IME_ACTION_DONE;
                }
            }
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String durationString = Objects.requireNonNull(editor.getText()).toString();
                if (durationString.length() != 0)
                {
                    // We know this string's format fairly accurately due to the filter below. However,
                    // we can't use the time scanner because there are a few books longer than 24 hours.
                    long duration = AudioBook.timeToMillis(durationString);
                    editor.setText(formatDurationShort(duration));
                }
                // We didn't process it, just a side-effect, so return false
            }
            return false;
        });

        doneButton.setOnClickListener((v) -> {
                long duration = AudioBook.timeToMillis(Objects.requireNonNull(editor.getText()).toString());
                if (duration >= 0) {
                    // We know the format of the actual data pretty well here due to
                    // UI, so this should always be the case.
                    book.setNewTime(duration);
                    FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
                    fragmentManager.popBackStack();
                }
            } );

        cancelButton.setOnClickListener((v) -> {
            FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
            fragmentManager.popBackStack();
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // This has to happen late (after layout is finished) to get the keyboard up immediately.
        editor.requestFocus();
        InputMethodManager imm = (InputMethodManager)
                requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        Objects.requireNonNull(imm);
        imm.showSoftInput(editor, InputMethodManager.SHOW_FORCED);
    }

    @Override
    public void onStop() {
        // Defer below until stop so we catch ALL stops
        // Method for hiding the keyboard is obscure at best, but it works
        InputMethodManager imm = (InputMethodManager)
                requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        Objects.requireNonNull(imm);
        imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        super.onStop();
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
