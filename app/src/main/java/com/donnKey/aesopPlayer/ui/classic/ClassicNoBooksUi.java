/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2019 Donn S. Terry
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
package com.donnKey.aesopPlayer.ui.classic;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;

import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.ApplicationComponent;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.ui.NoBooksUi;
import com.donnKey.aesopPlayer.ui.UiControllerNoBooks;
import com.donnKey.aesopPlayer.ui.UiUtil;

import javax.inject.Inject;
import javax.inject.Named;


public class ClassicNoBooksUi extends Fragment implements NoBooksUi {

    @SuppressWarnings("WeakerAccess")
    @Inject public GlobalSettings globalSettings;

    private UiControllerNoBooks controller;
    private View view;
    private ProgressUi progressUi;

    @SuppressWarnings("WeakerAccess")
    public @Inject @Named("AUDIOBOOKS_DIRECTORY") String audioBooksDirectoryName;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_no_books, container, false);
        ApplicationComponent component = AesopPlayerApplication.getComponent(view.getContext());
        component.inject(this);

        TextView noBooksPath = view.findViewById(R.id.noBooksPath);
        String directoryMessage =
                getString(R.string.copyBooksInstructionMessage, audioBooksDirectoryName);
        noBooksPath.setText(Html.fromHtml(directoryMessage));

        AppCompatButton downloadSamplesButton = view.findViewById(R.id.downloadSamplesButton);
        downloadSamplesButton.setOnClickListener(v -> controller.startSamplesInstallation());

        UiUtil.connectToSettings(view, globalSettings);

        /* Doesn't work here with big buttons
        final Context context = view.getContext();
        view.setOnTouchListener(new MultitapTouchListener(
                context, () -> startActivity(new Intent(context, SettingsActivity.class))));
        */

        return view;
    }

    @Override
    public void initWithController(@NonNull UiControllerNoBooks controller) {
        this.controller = controller;
    }

    @Override
    public void onResume() {
        super.onResume();
        CrashWrapper.log("UI: ClassicNoBooks fragment resumed");
    }

    @Override
    public void shutdown() {
        if (progressUi != null) {
            progressUi.shutdown();
            progressUi = null;
        }
    }

    private void onInstallError() {
        Preconditions.checkNotNull(progressUi);
        progressUi.shutdown();
        new AlertDialog.Builder(view.getContext())
                .setTitle(R.string.samplesDownloadErrorTitle)
                .setMessage(R.string.samplesDownloadErrorMessage)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override @NonNull
    public InstallProgressObserver showInstallProgress(boolean isAlreadyInstalling) {
        progressUi = new ProgressUi(view.getContext(), controller, isAlreadyInstalling);
        return progressUi;
    }

    private class ProgressUi implements InstallProgressObserver {
        private final @NonNull Context context;
        private final @NonNull
        UiControllerNoBooks controller;
        private final @NonNull ProgressDialog progressDialog;

        ProgressUi(@NonNull Context context,
                   @NonNull UiControllerNoBooks controller,
                   boolean isAlreadyInstalling) {
            this.context = context;
            this.controller = controller;
            this.progressDialog = createProgressDialog();
            progressDialog.show();
            progressDialog.setIndeterminate(true);
            if (isAlreadyInstalling)
                onInstallStarted();
        }

        @Override
        public void onDownloadProgress(int transferredBytes, int totalBytes) {
            if (totalBytes > -1) {
                int totalKBytes = totalBytes / 1024;
                if (progressDialog.isIndeterminate() || progressDialog.getMax() != totalKBytes) {
                    progressDialog.setIndeterminate(false);
                    progressDialog.setMax(totalKBytes);
                }
                progressDialog.setProgress(transferredBytes / 1024);
            }
        }

        @Override
        public void onInstallStarted() {
            progressDialog.setIndeterminate(true);
            progressDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.INVISIBLE);
        }

        @Override
        public void onFailure() {
            ClassicNoBooksUi.this.onInstallError();
        }

        void shutdown() {
            progressDialog.dismiss();
        }

        private ProgressDialog createProgressDialog() {
            final ProgressDialog progressDialog = new ProgressDialog(context);

            int maxKB = 0;
            int progressKB = 0;

            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setTitle(R.string.samplesDownloadProgressTitle);
            progressDialog.setProgress(progressKB);
            progressDialog.setMax(maxKB);
            progressDialog.setProgressNumberFormat("%1d/%2d KB");
            progressDialog.setCancelable(false);
            progressDialog.setButton(
                    DialogInterface.BUTTON_NEGATIVE,
                    context.getString(android.R.string.cancel),
                    (dialog, which) -> {
                        // Note: the dialog will dismiss itself even if the controller doesn't
                        // abort the installation.
                        controller.abortSamplesInstallation();
                    });
            return progressDialog;
        }
    }

}
