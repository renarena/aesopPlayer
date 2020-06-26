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
package com.donnKey.aesopPlayer.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.analytics.AnalyticsTracker;
import com.donnKey.aesopPlayer.service.DemoSamplesInstallerService;
import com.donnKey.aesopPlayer.events.DemoSamplesInstallationStartedEvent;

import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

import de.greenrobot.event.EventBus;

public class UiControllerNoBooks {

    public static class Factory {
        private final @NonNull AppCompatActivity activity;
        private final @NonNull Uri samplesDownloadUrl;
        private final @NonNull EventBus eventBus;
        private final @NonNull AnalyticsTracker analyticsTracker;

        @Inject
        public Factory(@NonNull AppCompatActivity activity,
                       @NonNull @Named("SAMPLES_DOWNLOAD_URL") Uri samplesDownloadUrl,
                       @NonNull EventBus eventBus,
                       @NonNull AnalyticsTracker analyticsTracker) {
            this.activity = activity;
            this.samplesDownloadUrl = samplesDownloadUrl;
            this.eventBus = eventBus;
            this.analyticsTracker = analyticsTracker;
        }

        public UiControllerNoBooks create(@NonNull NoBooksUi ui) {
            return new UiControllerNoBooks(activity, ui, samplesDownloadUrl, eventBus, analyticsTracker);
        }
    }

    private static final String TAG = "UiControllerBooks";
    static final int PERMISSION_REQUEST_DOWNLOADS = 100;

    private final @NonNull AppCompatActivity activity;
    private final @NonNull NoBooksUi ui;
    private final @NonNull Uri samplesDownloadUrl;
    private final @NonNull EventBus eventBus;
    private final @NonNull AnalyticsTracker analyticsTracker;

    private @Nullable DownloadProgressReceiver progressReceiver;

    private UiControllerNoBooks(@NonNull AppCompatActivity activity,
                                @NonNull NoBooksUi ui,
                                @NonNull Uri samplesDownloadUrl,
                                @NonNull EventBus eventBus,
                                @NonNull AnalyticsTracker analyticsTracker) {
        this.activity = activity;
        this.ui = ui;
        this.samplesDownloadUrl = samplesDownloadUrl;
        this.eventBus = eventBus;
        this.analyticsTracker = analyticsTracker;

        ui.initWithController(this);

        boolean isInstalling = DemoSamplesInstallerService.isInstalling();
        if (DemoSamplesInstallerService.isDownloading() || isInstalling)
            showInstallProgress(isInstalling);
    }

    public void startSamplesInstallation() {
        final boolean permissionsAlreadyGranted = PermissionUtils.checkAndRequestPermission(
                activity,
                new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE },
                PERMISSION_REQUEST_DOWNLOADS);
        CrashWrapper.log(TAG, "startSamplesInstallation, "
                + (permissionsAlreadyGranted ? "has permissions" : "requesting permissions"));
        if (permissionsAlreadyGranted)
            doStartSamplesInstallation();
    }

    private void doStartSamplesInstallation() {
        eventBus.post(new DemoSamplesInstallationStartedEvent());
        showInstallProgress(false);
        activity.startService(DemoSamplesInstallerService.createDownloadIntent(
                activity, samplesDownloadUrl));
    }

    public void abortSamplesInstallation() {
        Preconditions.checkState(DemoSamplesInstallerService.isDownloading()
                || DemoSamplesInstallerService.isInstalling());
        CrashWrapper.log(TAG, "abortSamplesInstallation, isDownloading: " +
                DemoSamplesInstallerService.isDownloading());
        // Can't cancel installation.
        if (DemoSamplesInstallerService.isDownloading()) {
            activity.startService(DemoSamplesInstallerService.createCancelIntent(
                    activity));
            stopProgressReceiver();
        }
    }

    void shutdown() {
        if (progressReceiver != null)
            stopProgressReceiver();
        ui.shutdown();
    }

    void onRequestPermissionResult(int code, @NonNull int[] grantResults) {
        Preconditions.checkArgument(code == PERMISSION_REQUEST_DOWNLOADS);
        Preconditions.checkArgument(grantResults.length == 1);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            doStartSamplesInstallation();
        } else {
            boolean canRetry = ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            AlertDialog.Builder dialogBuilder = PermissionUtils.permissionRationaleDialogBuilder(
                    activity, R.string.permission_rationale_download_samples);
            if (canRetry) {
                dialogBuilder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> dialogInterface.dismiss());
            } else {
                analyticsTracker.onPermissionRationaleShown("downloadSamples");
                dialogBuilder.setPositiveButton(
                        R.string.permission_rationale_settings, (dialogInterface, i) -> PermissionUtils.openAppSettings(activity));
                dialogBuilder.setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> dialogInterface.dismiss());
            }
            dialogBuilder.create().show();
        }
    }

    private void showInstallProgress(boolean isAlreadyInstalling) {
        Preconditions.checkState(progressReceiver == null);
        CrashWrapper.log(TAG, "showInstallProgress, " +
                (isAlreadyInstalling ? "installation in progress" : "starting installation"));
        NoBooksUi.InstallProgressObserver uiProgressObserver =
                ui.showInstallProgress(isAlreadyInstalling);
        progressReceiver = new DownloadProgressReceiver(uiProgressObserver);
        IntentFilter filter = new IntentFilter();
        filter.addAction(DemoSamplesInstallerService.BROADCAST_DOWNLOAD_PROGRESS_ACTION);
        filter.addAction(DemoSamplesInstallerService.BROADCAST_INSTALL_STARTED_ACTION);
        filter.addAction(DemoSamplesInstallerService.BROADCAST_FAILED_ACTION);
        filter.addAction(DemoSamplesInstallerService.BROADCAST_INSTALL_FINISHED_ACTION);
        LocalBroadcastManager.getInstance(activity).registerReceiver(progressReceiver, filter);
    }

    private void stopProgressReceiver() {
        Preconditions.checkState(progressReceiver != null);
        CrashWrapper.log(TAG, "stopProgressReceiver");
        LocalBroadcastManager.getInstance(activity).unregisterReceiver(progressReceiver);
        progressReceiver.stop();
        progressReceiver = null;
    }

    private class DownloadProgressReceiver extends BroadcastReceiver {

        private @Nullable NoBooksUi.InstallProgressObserver observer;

        DownloadProgressReceiver(@NonNull NoBooksUi.InstallProgressObserver observer) {
            this.observer = observer;
        }

        void stop() {
            this.observer = null;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Preconditions.checkNotNull(Objects.requireNonNull(intent.getAction()));
            // Workaround for intents being sent after the receiver is unregistered:
            // https://code.google.com/p/android/issues/detail?id=191546
            if (observer == null)
                return;

            CrashWrapper.log(TAG, "progress receiver: " + intent.getAction());
            if (DemoSamplesInstallerService.BROADCAST_DOWNLOAD_PROGRESS_ACTION.equals(
                    intent.getAction())) {
                int transferredBytes = intent.getIntExtra(
                        DemoSamplesInstallerService.PROGRESS_BYTES_EXTRA, 0);
                int totalBytes = intent.getIntExtra(
                        DemoSamplesInstallerService.TOTAL_BYTES_EXTRA, -1);
                observer.onDownloadProgress(transferredBytes, totalBytes);
            } else if (DemoSamplesInstallerService.BROADCAST_INSTALL_STARTED_ACTION.equals(
                    intent.getAction())) {
                observer.onInstallStarted();
            } else if (DemoSamplesInstallerService.BROADCAST_INSTALL_FINISHED_ACTION.equals(
                    intent.getAction())) {
                stopProgressReceiver();
            } else if (DemoSamplesInstallerService.BROADCAST_FAILED_ACTION.equals(
                    intent.getAction())) {
                observer.onFailure();
                stopProgressReceiver();
            } else {
                //noinspection ConstantConditions - getting here is an error
                Preconditions.checkState(false,
                        "Unexpected intent action: " + intent.getAction());
            }
        }
    }
}
