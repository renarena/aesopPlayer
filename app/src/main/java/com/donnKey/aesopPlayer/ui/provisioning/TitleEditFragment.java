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

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.model.AudioBook;

import java.util.Objects;

import static com.donnKey.aesopPlayer.ui.UiUtil.colorFromAttribute;

public class TitleEditFragment extends Fragment {
    private Provisioning provisioning;
    private Button doneButton;
    private String originalTitle;

    private AppCompatEditText finalTitle;

    public TitleEditFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.provisioning = new ViewModelProvider(this.requireActivity()).get(Provisioning.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_title_edit, container, false);

        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        Objects.requireNonNull(actionBar);
        actionBar.setTitle(provisioning.windowTitle);
        actionBar.setSubtitle(R.string.fragment_subtitle_edit_book_title);
        actionBar.setBackgroundDrawable(new ColorDrawable(colorFromAttribute(requireContext(),R.attr.actionBarBackground)));

        ((ProvisioningActivity) requireActivity()).navigation.
                setVisibility(View.GONE);

        finalTitle = view.findViewById(R.id.final_title);
        TextView directoryName = view.findViewById(R.id.directory_name);
        TextView audioFileName = view.findViewById(R.id.audio_filename);
        TextView audioTitle = view.findViewById(R.id.audio_title);
        TextView author = view.findViewById(R.id.author);
        Button normalizeButton = view.findViewById(R.id.button_normalize);
        Button addAuthorButton = view.findViewById(R.id.button_add_author);
        doneButton = view.findViewById(R.id.button_done);
        Button cancelButton = view.findViewById(R.id.button_cancel);
        boolean collides = false;

        if (provisioning.fragmentParameter instanceof Provisioning.Candidate) {
            Provisioning.Candidate candidate = (Provisioning.Candidate)provisioning.fragmentParameter;

            originalTitle = "";
            finalTitle.setText(candidate.bookTitle);
            directoryName.setText(AudioBook.filenameCleanup(candidate.newDirName));
            audioFileName.setText(AudioBook.filenameCleanup(candidate.audioFile));
            audioTitle.setText(AudioBook.filenameCleanup(candidate.metadataTitle));
            author.setText(AudioBook.filenameCleanup(candidate.metadataAuthor));
            collides = candidate.collides;
        }
        else if (provisioning.fragmentParameter instanceof AudioBook) {
            AudioBook book = (AudioBook)provisioning.fragmentParameter;

            AudioBook.TitleAndAuthor titleAndAuthor
                    = AudioBook.metadataTitle(book.getFile(book.getLastPosition()));
            originalTitle = book.getTitle();
            finalTitle.setText(book.getTitle());
            directoryName.setText(AudioBook.filenameCleanup(book.getPath().getName()));
            audioFileName.setText(AudioBook.filenameCleanup(book.getFile(book.getLastPosition()).getName()));
            audioTitle.setText(AudioBook.filenameCleanup(titleAndAuthor.title));
            author.setText(AudioBook.filenameCleanup(titleAndAuthor.author));
        }
        else {
            throw new RuntimeException("Improper Parameter to TitleEditFragment");
        }

        finalTitle.setSingleLine(true);
        finalTitle.addTextChangedListener(new inputFilter());
        // Trim the final title on "done" so that the user sees the trimmed version
        finalTitle.setOnEditorActionListener((v, actionId, e)-> {
                    switch (actionId) {
                    case EditorInfo.IME_ACTION_DONE:
                    case EditorInfo.IME_ACTION_NEXT:
                    case EditorInfo.IME_ACTION_PREVIOUS:
                        String s = Objects.requireNonNull(finalTitle.getText()).toString().trim();
                        finalTitle.setText(s);
                    }
                    return false; // We tweaked the result, but didn't consume the action
                });

        directoryName.setOnClickListener((v) -> finalTitle.setText(directoryName.getText()));
        audioFileName.setOnClickListener((v) -> finalTitle.setText(audioFileName.getText()));
        audioTitle.setOnClickListener((v) -> finalTitle.setText(audioTitle.getText()));
        normalizeButton.setOnClickListener((v) ->
                finalTitle.setText(AudioBook.titleCase(Objects.requireNonNull(finalTitle.getText()).toString())));

        if (author.getText() != null) {
            addAuthorButton.setEnabled(true);
            author.setEnabled(true);
            author.setOnClickListener((v) -> doAddAuthor(author));
            addAuthorButton.setOnClickListener((v) -> doAddAuthor(author));
        }

        doneButton.setOnClickListener(this::doTitleChange);

        if (collides) {
            doneButton.setText(getString(R.string.user_error_duplicate_book_name));
            doneButton.setBackgroundColor(colorFromAttribute(requireContext(),R.attr.buttonStopPressedBackground));
            doneButton.setOnClickListener(null);
        }

        cancelButton.setOnClickListener((v) -> {
            FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
            fragmentManager.popBackStack();
        });

        return view;
    }

    private void doTitleChange(View v) {
        String s = Objects.requireNonNull(finalTitle.getText()).toString();
        s = s.replace('/', '-'); // shouldn't happen, but...
        s = s.trim();
        if (!s.contains(" ")) {
            // In the case of a one word title (e.g. "Kidnapped!") be sure there's a space
            // (Although the author field will usually cover it.)
            // N.B. (6/2020) API 21 and 22 Emulators (but not others) treat a filename
            // with a trailing space as an error (yields an EINVAL). That causes the sample
            // file Hamlet to not install. The PreLaunch reports shows correct installation
            // of Hamlet on Android 5 (API 21), so that seems an emulator bug.
            s += " ";
        }
        if (provisioning.fragmentParameter instanceof Provisioning.Candidate) {
            Provisioning.Candidate candidate = (Provisioning.Candidate)provisioning.fragmentParameter;
            candidate.bookTitle = s;
            candidate.newDirName = s;
            candidate.collides = false;
        }
        else {
            // N.B. Already validated on the way in
            AudioBook book = (AudioBook)provisioning.fragmentParameter;

            if (!book.renameTo(s)) {
                doneButton.setText(getString(R.string.user_error_bad_book_name));
                doneButton.setBackgroundColor(colorFromAttribute(requireContext(),R.attr.buttonStopPressedBackground));
                doneButton.setOnClickListener(null);
                return;
            }
            book.setTitle(s);

            provisioning.booksEvent();
        }

        FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
        fragmentManager.popBackStack();
    }

    private void doAddAuthor(TextView author) {
        String a = author.getText().toString();
        if (a.length() > 0) {
            String s = Objects.requireNonNull(finalTitle.getText()).toString();
            s += " - " + a;
            finalTitle.setText(s);
        }
    }


    class inputFilter implements TextWatcher {
        boolean textChanged;

        @Override
        public void afterTextChanged(Editable editable) {
            if (textChanged) {
                // Recursion prevention
                return;
            }

            String before = editable.toString();
            String errMsg = null;
            if (before.length() <= 0) {
                errMsg = getString(R.string.user_error_title_cannot_be_empty);
            }
            if (before.length() > 255) {
                errMsg = getString(R.string.user_error_title_too_long);
            }
            if (!originalTitle.equals(before)
                && (provisioning.scanForDuplicateAudioBook(before))) {
                errMsg = getString(R.string.user_error_duplicate_book_name);
            }
            String s = before.trim();
            if (s.length() <= 0) {
                errMsg = getString(R.string.user_error_title_cannot_be_blank);
            }
            if (errMsg != null) {
                doneButton.setText(errMsg);
                doneButton.setBackgroundColor(colorFromAttribute(requireContext(),R.attr.buttonStopPressedBackground));
                // (Don't use setEnabled(false) to turn off the button: the error text is unreadable
                // due to the low contrast that yields).
                doneButton.setOnClickListener(null);
                return;
            }

            doneButton.setBackgroundColor(colorFromAttribute(requireContext(), R.attr.buttonStartBackground));
            doneButton.setEnabled(true);
            doneButton.setOnClickListener(TitleEditFragment.this::doTitleChange);
            doneButton.setText(getString(R.string.title_edit_accept_button));

            int curPos = finalTitle.getSelectionStart();

            StringBuilder after = new StringBuilder();
            for (int i = 0; i < before.length(); i++) {
                char c = before.charAt(i);
                if (c == '/') {
                    after.append('-');
                    textChanged = true;
                }
                else {
                    after.append(c);
                }
            }

            if (textChanged) {
                Toast.makeText(getContext(),getString(R.string.toast_warning_character_fixed), Toast.LENGTH_LONG).show();
                finalTitle.setText(after.toString());
                finalTitle.setSelection(curPos);
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            textChanged = false; // recursion prevention
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // ignore
        }
    }
}
