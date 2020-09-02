package com.donnKey.aesopPlayer.ui.provisioning;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.util.AwaitResume;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static com.donnKey.aesopPlayer.AesopPlayerApplication.getAppContext;
//import static com.donnKey.aesopPlayer.service.DemoSamplesInstallerService.enableTlsOnAndroid4;

class Http {
    private final AwaitResume downloadCompletes = new AwaitResume();
    private final static int DOWNLOAD_BUFFER_SIZE = 32768;
    private final String TAG="Http";

    private final DownloadManager downloadManager;
    private String downloadedUrlString;
    private final Context appContext;
    private String errorStatus = null;
    private Handler handler = null;

    final int RETRIES_MAX = 5;
    final int CANCELS_MAX = 5;
    int retriesDone;
    int cancelsDone;
    enum Actions { ACTION_DONE, ACTION_CONTINUE, ACTION_RETRY, ACTION_CANCEL}
    final long POLL_INTERVAL = 5000; // Millis

    Http(DownloadManager downloadManager) {
        appContext = getAppContext();
        this.downloadManager = downloadManager;

        if (downloadManager != null) {
            try {
                // This is required but one-time only, but we might be running on the same thread
                // a previous incarnation had used, so just ignore any duplication.
                Looper.prepare();
            } catch (RuntimeException e) {
                // ignore
            }
            handler = new Handler();
        }
    }

    @WorkerThread
    File getFile_socket(String requested, File tmpFile) throws Exception{
        byte[] inputBuffer = new byte[DOWNLOAD_BUFFER_SIZE];

        final URL url;
        try {
            url = new URL(requested);
        } catch (MalformedURLException e) {
            throw new Exception("URL is incorrectly formed, could not parse.");
        }

        try {
            OutputStream output = new BufferedOutputStream(new FileOutputStream(tmpFile));
            HttpURLConnection connection;
            connection = (HttpURLConnection)url.openConnection();
            // See comment in RemoteAuto about TLS
            //enableTlsOnAndroid4(connection);

            // Disable gzip, apparently Java and/or Android's okhttp has problems with it
            // (possibly https://bugs.java.com/bugdatabase/view_bug.do?bug_id=7003462).
            connection.setRequestProperty("accept-encoding", "identity");

            InputStream input = new BufferedInputStream(connection.getInputStream());
            int bytesRead;
            while((bytesRead = input.read(inputBuffer, 0, inputBuffer.length)) > 0) {
                output.write(inputBuffer, 0, bytesRead);
            }
            output.close();
            connection.disconnect();
        } catch (IOException e) {
            CrashWrapper.recordException(TAG, e);
            throw e;
        }

        return tmpFile;
    }

    @WorkerThread
    File getFile_manager(String requested) throws Exception {
        errorStatus = null;
        downloadCompletes.prepare();
        downloadedUrlString = null;
        if (downloadUsingManager_start(requested)) {
            downloadCompletes.await();
            if (errorStatus != null) {
                throw new Exception(errorStatus);
            }
        }

        String name = Uri.parse(downloadedUrlString).getPath();
        if (name == null) {
            throw new Exception("Cannot parse downloaded file name");
        }
        return new File(name);
    }

    @WorkerThread
    private boolean downloadUsingManager_start(String requested) {
        Uri uri = Uri.parse(requested);
        String downloadFileName = uri.getLastPathSegment();
        if (downloadFileName == null) {
            errorStatus = "Target filename could not be found";
            return false;
        }

        // We allow mobile data here because we can't get here if the setting prevents it.
        DownloadManager.Request downloadRequest = new DownloadManager.Request(uri);
        downloadRequest
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE)
                .setAllowedOverRoaming(true)
                .setDescription("Aesop Player Download")
                .setTitle(downloadFileName)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, downloadFileName);

        final long lastDownload = downloadManager.enqueue(downloadRequest);
        // Now we wait for the download to complete.
        IntentFilter intentFilter =
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        // We don't do anything with download notifications, so we won't bother
        // actually doing it, but here's the code.
        //intentFilter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED);

        appContext.registerReceiver(
                // No lambda possible here.
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, @NonNull Intent i) {
                        // Note the capture of lastDownload here. That's critical to the
                        // design to keep it from being confused by "old" downloads queued
                        // up by the download manager.

                        if (i.getAction() == null) {
                            // This can get called for "ancient" (long since abandoned) requests
                            return;
                        }

                        long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

                        //Checking if the received broadcast is for our enqueued download
                        if (lastDownload == id) {
                            //noinspection SwitchStatementWithTooFewBranches
                            switch (i.getAction()) {
                                //case DownloadManager.ACTION_NOTIFICATION_CLICKED:
                                //   See above for more
                                //   break;
                                case DownloadManager.ACTION_DOWNLOAD_COMPLETE:
                                    appContext.unregisterReceiver(this);
                                    downloadUsingManager_end(lastDownload);
                                    break;
                            }
                        }
                        else {
                            Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(id));
                            cursor.moveToFirst();
                        /* In case they're needed for debugging someday
                        int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                        int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
                        int bytes = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        int soFar = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        Log.x("AESOP " + getClass().getSimpleName(), "Alternate BCR " + status + " " + reason + " " + bytes + " " + soFar);
                         */
                        }
                        // otherwise, just ignore it -- it's a leftover the manager is finally
                        // telling us about.
                    }
                }, intentFilter);

        retriesDone = 0;
        cancelsDone = 0;
        checkDownloadProgress(requested, lastDownload);
        return true;
    }

    @WorkerThread
    void downloadUsingManager_end(long lastDownload) {
        Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(lastDownload));
        if (cursor == null) {
            CrashWrapper.log(TAG, "Download: no cursor (internal error)");
            // ... and ignore it
        }
        else {
            if (cursor.moveToFirst()) {
                int internalId = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_ID));
                if (internalId == lastDownload) {
                    int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    String fileLocalUriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    String fileUriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI));

                    switch (status) {
                        case DownloadManager.STATUS_SUCCESSFUL: {
                            downloadedUrlString = fileLocalUriString;
                            downloadCompletes.resume();
                            break;
                        }

                        case DownloadManager.STATUS_PAUSED:
                        case DownloadManager.STATUS_FAILED: {
                            int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
                            String reasonText;
                            switch (reason) {
                                case DownloadManager.ERROR_CANNOT_RESUME:
                                    reasonText = "ERROR_CANNOT_RESUME";
                                    break;
                                case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                                    reasonText = "ERROR_DEVICE_NOT_FOUND";
                                    break;
                                case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                                    reasonText = "ERROR_FILE_ALREADY_EXISTS";
                                    break;
                                case DownloadManager.ERROR_FILE_ERROR:
                                    reasonText = "ERROR_FILE_ERROR";
                                    break;
                                case DownloadManager.ERROR_HTTP_DATA_ERROR:
                                    reasonText = "ERROR_HTTP_DATA_ERROR";
                                    break;
                                case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                                    reasonText = "ERROR_INSUFFICIENT_SPACE";
                                    break;
                                case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                                    reasonText = "ERROR_TOO_MANY_REDIRECTS";
                                    break;
                                case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                                    reasonText = "ERROR_UNHANDLED_HTTP_CODE";
                                    break;
                                case DownloadManager.ERROR_UNKNOWN:
                                    reasonText = "ERROR_UNKNOWN";
                                    break;
                                case DownloadManager.PAUSED_WAITING_TO_RETRY:
                                    reasonText = "Too long without progress . Bad host id?";
                                    break;
                                case 404:
                                    reasonText = "File not found on host";
                                    break;
                                default:
                                    reasonText = "Failure Code: " + reason;
                                    break;
                            }

                            downloadedUrlString = null;
                            // Remove failures.  (This removes files, so not for successes.)
                            downloadManager.remove(lastDownload);
                            errorStatus = "Error: download of " + fileUriString + " failed with download error: " + reasonText;
                            downloadCompletes.resume();
                        }
                        default:
                            // nothing: ignore (not for us)
                            break;
                    }
                }
            }
            cursor.close();
        }
    }

    private void checkDownloadProgress(final String requested, final long downloadId) {
        // Note captured downloadId below. As above it's necessary to avoid working in the wrong things
        handler.postDelayed(() -> {
            final DownloadManager.Query query = new DownloadManager.Query();
            // Filter only by ID: adding other filters can (does?) yield an "or" query, and
            // the moveToFirst() won't get our ID.
            query.setFilterById(downloadId);
            final Cursor cursor = downloadManager.query(query);
            if (cursor.moveToFirst()) {
                int internalId = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_ID));
                if (internalId != downloadId) {
                    // In spite of the filter this can happen...
                    return;
                }
                int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                Actions action = Actions.ACTION_DONE;

                //int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
                switch (status) {
                    case DownloadManager.STATUS_PAUSED: {
                        //int bytes = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        int soFar = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        if (soFar > 0) {
                            action = Actions.ACTION_RETRY;
                            break;
                        }
                        // drop thru
                    }
                    case DownloadManager.STATUS_PENDING: {
                        //int bytes = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        //int soFar = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        //Log.z("AESOP " + getClass().getSimpleName(), "P/P " + status + " " + reason + " " + bytes + " " + soFar);

                        // For STATUS_PENDING:
                        // This is "startup". It's possible that the start is very slow
                        // setting up the connection, so we give it an extra chance.

                        // For STATUS_PAUSED:
                        // None of the sub-codes seem to have any useful action but to ultimately
                        // cancel if the download manager doesn't figure it out.
                        action = Actions.ACTION_CANCEL;
                        break;
                    }

                    case DownloadManager.STATUS_RUNNING: {
                        action = Actions.ACTION_CONTINUE;
                        break;
                    }

                    case DownloadManager.STATUS_FAILED:
                    case DownloadManager.STATUS_SUCCESSFUL: {
                        // For STATUS_SUCCESSFUL:
                        // nothing more to do here.

                        // For STATUS_FAILED
                        // If the download manager thinks it's hopeless, we can only agree.
                        action = Actions.ACTION_DONE;
                        break;
                    }
                }

                switch (action) {
                    case ACTION_DONE: {
                        downloadUsingManager_end(downloadId);
                        break;
                    }

                    case ACTION_RETRY: {
                        if (retriesDone >= RETRIES_MAX) {
                            // Failure... give up
                            downloadUsingManager_end(downloadId);
                        }
                        else {
                            retriesDone++;
                            downloadManager.remove(downloadId);
                            // Restart the request (cancel the prior one)
                            downloadUsingManager_start(requested);
                        }
                        break;
                    }

                    case ACTION_CONTINUE: {
                        retriesDone = 0; // Some progress has been made... make full retries available
                        cancelsDone = 0;
                        checkDownloadProgress(requested, downloadId);
                        break;
                    }

                    case ACTION_CANCEL: {
                        if (cancelsDone >= CANCELS_MAX) {
                            downloadUsingManager_end(downloadId);
                        }
                        else {
                            cancelsDone++;
                            checkDownloadProgress(requested, downloadId);
                        }
                        break;
                    }
                }
            }
            // Ignore a call that doesn't match what we're expecting
            cursor.close();
        }, POLL_INTERVAL);
    }
}
