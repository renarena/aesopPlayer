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

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.events.AudioBooksChangedEvent;
import com.donnKey.aesopPlayer.events.MediaStoreUpdateEvent;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.model.AudioBookManager;
import com.donnKey.aesopPlayer.ui.UiUtil;
import com.donnKey.aesopPlayer.util.AwaitResume;
import com.donnKey.aesopPlayer.util.FilesystemUtil;
import com.google.android.gms.common.internal.Asserts;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import de.greenrobot.event.EventBus;

import static com.donnKey.aesopPlayer.AesopPlayerApplication.getAppContext;
import static com.donnKey.aesopPlayer.util.FilesystemUtil.isAudioPath;

public class RemoteAuto {
    // Every *interval* check if there's a new instance of the control file or new mail, and if so
    // perform the actions directed by that file.  The parser in process() defines what
    // it can do. It always checks things on first startup (which is when the player goes
    // to bookList (idle)).

    // Note: setting up jekyll serve to serve test files is really easy as long as you want
    // http, not https (you want "clear text"). jekyll serve with certificates is a pain.
    // but to use cleartext, you have to have network_security_config.xml set
    // to allow cleartext; see that file.

    @Inject
    public GlobalSettings globalSettings;
    @Inject
    public AudioBookManager audioBookManager;
    @Inject
    @Named("AUDIOBOOKS_DIRECTORY")
    public String audioBooksDirectoryName;
    @Inject
    public EventBus eventBus;

    private final Provisioning provisioning;

    @SuppressWarnings("FieldCanBeLocal")
    private final String controlFileName = "AesopScript.txt";
    @SuppressWarnings("FieldCanBeLocal")
    private final String resultFileName = "AesopResult.txt";

    private final Context appContext;
    private final AppCompatActivity activity;
    @SuppressWarnings("FieldCanBeLocal")
    private final String DateTimeFormat = "yyyy-MM-dd HH:mm:ss zzz";

    // ?????????????? Make these times sensible for real life, not testing
    // Conditional on DEBUG?
    //private final long interval = TimeUnit.MINUTES.toMillis(10);
    private final long interval = TimeUnit.SECONDS.toMillis(10);
    private final long startUpDelay = TimeUnit.SECONDS.toMillis(3);

    final File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    File controlDir;
    File currentCandidateDir;

    // Global state
    private boolean enabled;

    // Per request state
    private final List<String> resultLog = new ArrayList<>();
    private BufferedReader commands;
    private final ArrayList<String> sendResultTo = new ArrayList<>();
    private DownloadManager downloadManager;
    boolean retainBooks;
    boolean renameFiles;

    // State needed for async downloads
    private long lastDownload = -1;
    private File currentInstallFile;

    // so some inherently async operations look synchronous
    private final AwaitResume downloadCompletes = new AwaitResume();
    private final AwaitResume booksChangedEvent = new AwaitResume();

    @Singleton
    @UiThread
    public RemoteAuto(@NonNull AppCompatActivity activity) {
        //????  Is this leak-safe?
        this.activity = activity;
        appContext = activity.getApplicationContext();
        AesopPlayerApplication.getComponent(appContext).inject(this);

        this.provisioning = new ViewModelProvider(activity).get(Provisioning.class);
        eventBus.register(this);
    }

    @UiThread
    public void start() {
        Log.w("AESOP", "Remote start");
        enabled = true;
        // Do the work on a thread so we can directly call "long" operations,
        // since this is all logically synchronous.
        // Delay starting until everything else settles
        //???????????? Should be longer in real life
        Handler handler = new Handler();
        handler.postDelayed( () -> {
                Thread t = new Thread(this::pollLoop);
                t.start(); },
                startUpDelay);
    }

    @SuppressLint("UsableSpace")
    @WorkerThread
    public void pollLoop() {
        while (true) {
            if (!enabled) {
                Log.w("AESOP" + getClass().getSimpleName(), "not enabled");
                return;
            }
            Log.w("AESOP " + getClass().getSimpleName(), "Poll cycle");

            // Reset to the same initial state each cycle
            // Start downloads in the same place each run
            currentCandidateDir = downloadDir;
            sendResultTo.clear();
            resultLog.clear();
            commands = null;
            // "Almost always" defaults for installing
            retainBooks = false;
            renameFiles = true;

            boolean workAvailable = false;
            if (globalSettings.getFilePollEnabled()) {
                workAvailable = pollControlFile();
            }
            if (!workAvailable && globalSettings.getMailPollEnabled()) {
                workAvailable = pollMail();
            }

            if (workAvailable) {
                downloadManager = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);

                @SuppressLint("SimpleDateFormat")
                String currentDateAndTime = new SimpleDateFormat(DateTimeFormat).format(new Date());
                logActivity("Start: " + currentDateAndTime);

                // ?????????????????????? timeout???
                // make commands a parameter?????????????
                processFSM();

                try {
                    commands.close();
                } catch (IOException e) {
                    // ignored
                }
                commands = null;

                // Tell the user about space remaining
                final List<File>audioBooksDirs = FilesystemUtil.audioBooksDirs(getAppContext());
                for (File activeStorage : audioBooksDirs) {
                    logActivity("Space on " + activeStorage.getParent() + ": Using " +
                        activeStorage.getUsableSpace()/1000000 + "Mb of " +
                        activeStorage.getTotalSpace()/1000000 + "Mb (" +
                            (int)(((float)activeStorage.getUsableSpace()/
                                   (float)activeStorage.getTotalSpace())*100) + "%)");
                }

                // don't need the extra var, but suppress-lint objects ???????????????
                @SuppressLint("SimpleDateFormat")
                String currentDateAndTime2 = new SimpleDateFormat(DateTimeFormat).format(new Date());
                logActivity("End: " + currentDateAndTime2);

                Log.w("AESOP " + getClass().getSimpleName(), "**********************************");
                sendReport();
                Log.w("AESOP " + getClass().getSimpleName(), "**********************************");

                downloadManager = null;
            }

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                // ignored
            }
        }
    }

    @UiThread
    public void stop() {
        Log.w("AESOP", "Remote stop");
        enabled = false;
    }

    // ??????????????????????? All actions here need to turn off the speaker (search for speak); see UiControllerBookList  108
    @WorkerThread
    private boolean pollControlFile() {
        controlDir = globalSettings.getRemoteControlDir();

        File controlFile = new File(controlDir, controlFileName);
        if (!controlFile.exists()) {
            return false;
        }

        long timestamp = controlFile.lastModified();
        if (timestamp <= globalSettings.getSavedControlFileTimestamp()) {
            return false;
        }

        // The file has been changed since last we looked.
        try {
            commands = new BufferedReader(new FileReader(controlFile));
        } catch (FileNotFoundException e) {
            logActivity("Control File failed open " + e);
            return false;
        }

        globalSettings.setSavedControlFileTimestamp(timestamp);
        return true;
    }

    @WorkerThread
    private boolean pollMail() {
        Mail mail = new Mail(activity);
        // Search is for a pattern, case insensitive
        if (!mail.readMail("Aesop")) {
            return false;
        }

        long timestamp = mail.getInboundTimestamp();
        if (timestamp <= globalSettings.getSavedMailTimestamp()) {
            return false;
        }

        commands = mail.getInboundBodyStream();
        sendResultTo.add(mail.getInboundSender());

        globalSettings.setSavedMailTimestamp(timestamp);
        return true;
    }

    @WorkerThread
    void processFSM() {
        // Read and process each line of the input stream.
        while (true) {
            String line;
            try {
                line = commands.readLine();
            } catch (IOException e) {
                Log.w("AESOP " + getClass().getSimpleName(), "EOF catch");
                return;
            }

            Log.w("AESOP " + getClass().getSimpleName(), "-----------------------------------------------------");
            Log.w("AESOP" + getClass().getSimpleName(), "got line '" + line + "'");
            if (line == null) {
                return;
            }
            logActivity(line);

            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            // Split on spaces, honoring quoted strings correctly
            ArrayList<String> operands = new ArrayList<>(Arrays.asList(
                    line.split(" +(?=([^\"]*\"[^\"]*\")*[^\"]*$)")));
            int count;
            for (count = 0; count < operands.size(); count++) {
                if (operands.get(count).indexOf("//") == 0) {
                    // a comment
                    break;
                }
            }

            // Trim the comment words
            while (operands.size() > count) {
                operands.remove(operands.size() - 1);
            }

            if (operands.size() == 0) {
                continue;
            }

            String op0 = operands.get(0);
            int pos = op0.indexOf(':');
            if (pos < 0) {
                logActivityIndented("Command not recognized (missing ':')");
                continue;
            }

            provisioning.clearErrors();

            String key = op0.substring(0, pos + 1).toLowerCase();
            Log.w("AESOP" + getClass().getSimpleName(), "got key '" + key + "'");
            switch (key) {
                case "file:":
                case "ftp:":
                case "http:":
                case "https:": {
                    // Download and/or install a file
                    boolean downloadOnly = checkOperandsFor(operands, "downloadOnly");
                    String newTitle = findOperandString(operands);
                    if (newTitle != null) {
                        newTitle = checkName(newTitle);
                        if (newTitle == null) {
                            break;
                        }
                    }

                    if (errorIfAnyRemaining(operands)) {
                        break;
                    }

                    switch (key) {
                        case "http:":
                        case "https:": {
                            if (downloadOnly && newTitle != null) {
                                logActivityIndented("Cannot use 'downloadOnly' with new title");
                                return;
                            }

                            String uri = downloadHttp(op0, newTitle);

                            if (uri == null) {
                                break;
                            }
                            if (downloadOnly) {
                                logActivityIndented("Download only of " + op0 + " as " + uri + " successful.");
                                break;

                            }

                            install(uri, newTitle, true);
                            break;
                        }
                        case "ftp:": {
                            //??????????? ignoring ftp for now
                            logActivityIndented("Error: ftp:// not supported");
                            break;
                        }
                        case "file:": {
                            // A local file.
                            if (downloadOnly) {
                                logActivityIndented("Error: downloadOnly illegal on file://");
                            } else {
                                install(op0, newTitle, false);
                            }
                            break;
                        }
                    }
                    break;
                }
                case "books:": {
                    // Do inventory related commands
                    booksCommands(operands);
                    break;
                }
                case "downloads:": {
                    // Do download related commands
                    downloadsCommands(operands);
                    break;
                }
                case "settings:": {
                    // Do settings related commands
                    settingsCommands(operands);
                    break;
                }
                case "mailto:": {
                    if (globalSettings.getMailHostname() == null
                        || globalSettings.getMailLogin() == null) {
                            logActivityIndented("Mail connection not set up: mailto: ignored");
                            break;
                    }
                    String to = op0.substring("mailto:".length());
                    sendResultTo.add(to);
                    logActivityIndented("Results will be mailed to " + to);
                    break;
                }
                case "exit:": {
                    return;
                }
                default: {
                    logActivityIndented("Error: unrecognized command ignored: " + key);
                    break;
                }
            }
        }
    }

    // Downloading stuff
    // The file that got the download: null if download isn't complete or unsuccessful
    String downloadedString;

    @WorkerThread
    private String downloadHttp(String requested, String newTitle) {
        downloadCompletes.prepare();
        downloadedString = null;
        downloadHttp_start(requested, newTitle);
        downloadCompletes.await();
        return downloadedString;
    }

    @WorkerThread
    private void downloadHttp_start(String requested, String newTitle) {
        // Deal with full filesystem ????????????????????????????????????????????????????????

        Uri uri = Uri.parse(requested);
        String downloadFile = uri.getLastPathSegment();

        DownloadManager.Request downloadRequest = new DownloadManager.Request(uri);
        downloadRequest.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI
                | DownloadManager.Request.NETWORK_MOBILE)
                .setAllowedOverRoaming(false)
                .setDescription("Aesop Player Download")
                .setTitle(newTitle != null ? newTitle : downloadFile)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        downloadFile);

        lastDownload = downloadManager.enqueue(downloadRequest);

        // Now we wait for the download to complete.
        IntentFilter intentFilter =
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        //????????????
        intentFilter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED);

        activity.registerReceiver(onBroadcastEvent, intentFilter);
    }

    @WorkerThread
    void downloadHttp_end() {
        Cursor c = downloadManager.query(new DownloadManager.Query().setFilterById(lastDownload));

        if (c == null) {
            logActivityIndented("Error: download: no cursor (internal error)");
        }
        else {
            c.moveToFirst();
            int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
            String fileLocalUriString = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
            String fileUriString = c.getString(c.getColumnIndex(DownloadManager.COLUMN_URI));

            switch(status) {
            case DownloadManager.STATUS_SUCCESSFUL: {
                activity.unregisterReceiver(onBroadcastEvent);
                downloadedString = fileLocalUriString;
                downloadCompletes.resume();
                break;
            }
            case DownloadManager.STATUS_FAILED: {
                activity.unregisterReceiver(onBroadcastEvent);
                int reason = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON));
                logActivityIndented("Error: download of " + fileUriString + " failed with http error " + reason);
                downloadedString = null;
                downloadCompletes.resume();
                break;
            }
            default:
                // nothing: ignore (not for us)
                break;
            }
            c.close();
        }
    }

    @WorkerThread
    private boolean install(@NonNull String fileUrl, String title, boolean priorDownload) {
        // Install the book named by the "file:" fileUrl
        // We allow the otherwise illegal file:<relative pathname> (no slash after :) to mean
        // an Android sdcard-local filename.
        Asserts.checkState(fileUrl.substring(0,5).equals("file:"));
        if (fileUrl.charAt(5) != '/') {
            String pathName = fileUrl.substring(5);
            // a relative path - find relative to downloadDir's parent
            currentInstallFile = new File(downloadDir.getParent(), pathName);
        }
        else {
            // Use more "official" conversion ?????????????????????????????????
            currentInstallFile = new File(fileUrl.substring(7));
        }

        if (!currentInstallFile.exists()) {
            logActivityIndented( "File not found: " + currentInstallFile.getPath());
            return false;
        }

        if (title == null) {
           title = currentInstallFile.getName();
        }

        File audioFile;
        if (isAudioPath(currentInstallFile.getName())) {
            audioFile = currentInstallFile;
        }
        else {
            audioFile = FileUtilities.findFileMatching(currentInstallFile, FilesystemUtil::isAudioPath);
            if (audioFile == null) {
                logActivityIndented( "Not an audiobook: no audio files found: " + currentInstallFile.getPath());
                if (FileUtilities.deleteTree(currentInstallFile, this::logResult)) {
                    logActivityIndented("Deleted " + currentInstallFile.getPath());
                }
                postResults(currentInstallFile.getPath());
                return false;
            }
        }

        Provisioning.Candidate candidate = new Provisioning.Candidate();
        candidate.fill(title, currentInstallFile.getPath(), audioFile);
        candidate.isSelected = true;
        candidate.computeDisplayTitle();

        boolean r = retainBooks || !Objects.requireNonNull(currentInstallFile.getParentFile()).canWrite();
        provisioning.moveOneFile_Task(candidate, this::installProgress, r, renameFiles);

        if (!postResults(currentInstallFile.getPath()) && priorDownload) {
            logActivityIndented("BE SURE to delete or finish installing this failed install" );
        }
        bookListChanged();

        return true;
    }

    @WorkerThread
    private void booksCommands(List<String> operands) {
        String key = operands.get(0).toLowerCase();
        switch (key) {
        case "books:books": {
            if (errorIfAnyRemaining(operands)) {
                return;
            }

            provisioning.buildBookList();

            logActivityIndented("Current Books  " + provisioning.getTotalTimeSubtitle());
            logActivityIndented(" Length   Current        C W Title");
            for (Provisioning.BookInfo bookInfo : provisioning.bookList) {
               AudioBook book = bookInfo.book;
               String line = String.format("%1s%-8s %-14s %1s %1s %s",
                   bookInfo.current ? ">" : " ",
                   UiUtil.formatDuration(book.getTotalDurationMs()),
                   book.thisBookProgress(appContext),
                   book.getCompleted() ? "C" : " ",
                   bookInfo.unWritable ? "N" : " ",
                   book.getDisplayTitle());
               if (!book.getDirectoryName().equals(book.getDisplayTitle())) {
                   line += " -> " + book.getDirectoryName();
               }
               logActivityIndented(line);
            }
            logActivityIndented("");
            break;
        }
        case "books:clean": {
            provisioning.buildBookList();
            if (checkOperandsFor(operands, "all")) {
                for (Provisioning.BookInfo b : provisioning.bookList) {
                    if (!b.current) {
                        b.selected = true;
                    }
                }
            }

            if (checkOperandsFor(operands, "current")) {
                for (Provisioning.BookInfo b : provisioning.bookList) {
                    if (b.current) {
                        b.selected = true;
                    }
                }
            }

            if (errorIfAnyRemaining(operands)) {
                return;
            }

            logActivityIndented("Cleaned books (if any found):");
            runDelete();
            break;
        }
        case "books:delete": {
            String partialTitle = findOperandString(operands);
            if (partialTitle == null || partialTitle.isEmpty()) {
                logActivityIndented("Requires exactly one (non-null) operand");
                return;
            }

            if (errorIfAnyRemaining(operands)) {
                return;
            }

            provisioning.buildBookList();
            if (findInBookList(partialTitle) == null) {
                logActivityIndented("No unique match found for "+ partialTitle);
                return;
            }

            runDelete();
            break;
        }
        case "books:rename": {
            String partialTitle = findOperandString(operands);
            String newTitle = findOperandString(operands);

            if (partialTitle == null || partialTitle.isEmpty() || newTitle == null) {
                logActivityIndented("Requires exactly two string operands");
                return;
            }

            newTitle = checkName(newTitle);
            if (newTitle == null) {
                return;
            }

            if (errorIfAnyRemaining(operands)) {
                return;
            }

            provisioning.buildBookList();
            Provisioning.BookInfo bookInfo = findInBookList(partialTitle);
            if (bookInfo == null) {
                logActivityIndented("No unique match found for '"+ partialTitle + "'");
                return;
            }

            logActivityIndented("Rename " + bookInfo.book.getPath().getPath() + " to '" + newTitle + "'");

            bookInfo.book.renameTo(newTitle, this::logResult);
            postResults(bookInfo.book.getPath().getPath());
            bookListChanged();
            return;
        }
        case "books:reset": {
            boolean all = checkOperandsFor(operands, "all");
            String partialTitle = findOperandString(operands);
            String newTimeString = findOperandText(operands);

            //noinspection SimplifiableBooleanExpression
            if (!(all ^ partialTitle != null)) {
                logActivityIndented("Requires exactly one of 'all' or a partial title");
                return;
            }

            long newTime = 0;
            if (newTimeString != null) {
                newTime = AudioBook.timeToMillis(newTimeString);
            }
            if (newTime < 0) {
                logActivityIndented("Cannot interpret time: " + newTimeString);
                return;
            }

            if (errorIfAnyRemaining(operands)) {
                return;
            }

            provisioning.buildBookList();
            if (partialTitle != null) {
                Provisioning.BookInfo bookInfo = findInBookList(partialTitle);
                if (bookInfo == null) {
                    logActivityIndented("No unique match found for " + partialTitle);
                    return;
                }
                bookInfo.book.setNewTime(newTime);
                postResults(bookInfo.book.getPath().getPath());
            }
            else {
                // must be "all"
                for (Provisioning.BookInfo bookInfo : provisioning.bookList) {
                    bookInfo.book.setNewTime(newTime);
                }
            }

            bookListChanged();
            break;
        }
        default:
            logActivityIndented("Unrecognized request ");
            break;
        }
    }

    @WorkerThread
    private void runDelete() {
        provisioning.deleteAllSelected_Task(this::installProgress);
        postResults(null);
        bookListChanged();
    }

    @WorkerThread
    private void downloadsCommands(@NonNull List<String> operands) {
        String key = operands.get(0).toLowerCase();
        switch (key) {
        case "downloads:books": {
            buildCandidateList();
            if (errorIfAnyRemaining(operands)) {
                return;
            }

            logActivityIndented("Current Downloaded books in " + currentCandidateDir.getPath());
            logActivityIndented("C Name [-> Title]");
            for (Provisioning.Candidate candidate : provisioning.candidates) {
                String dirName = new File(candidate.oldDirPath).getName();
                String line = String.format("%1s %s",
                        candidate.collides ? "C" : " ",
                        dirName);
                if (candidate.bookTitle != null) {
                    if (!candidate.bookTitle.equals(dirName)) {
                        line += " -> " + candidate.bookTitle;
                    }
                }
                logActivityIndented(line);
            }
            logActivityIndented("");
            break;
        }
        case "downloads:directory": {
            String dirName = findOperandString(operands);
            if (dirName == null) {
                logActivityIndented( "Directory name required");
                return;
            }
            if (errorIfAnyRemaining(operands)) {
                return;
            }
            File newDir;
            if (dirName.charAt(0) == '/') {
                newDir = new File(dirName);
            }
            else {
                newDir = new File(downloadDir.getParent(), dirName);
            }
            if (!newDir.exists()) {
                logActivityIndented("New download directory " + newDir.getPath() + " not found");
                return;
            }
            logActivityIndented("Download directory set to " + newDir.getPath());
            currentCandidateDir = newDir;
            break;
        }
        case "downloads:install": {
            String partialTitle;
            String newTitle;

            boolean all = checkOperandsFor(operands, "all");
            if (all) {
                partialTitle = null;
                newTitle = null;
            }
            else {
                partialTitle = findOperandString(operands);
                newTitle = findOperandString(operands);
                if (partialTitle == null) {
                    logActivityIndented("'All' or a partial title required");
                    return;
                }
            }

            if (errorIfAnyRemaining(operands)) {
                return;
            }

            if (newTitle != null) {
                newTitle = checkName(newTitle);
                if (newTitle == null) {
                    return;
                }
            }

            buildCandidateList();
            if (partialTitle != null) {
                Provisioning.Candidate candidate = findInCandidatesList(partialTitle);
                if (candidate == null) {
                    logActivityIndented("No unique match found for " + partialTitle);
                    return;
                }
                // check for collisions elsewhere
                candidate.isSelected = true;
                if (newTitle != null) {
                    candidate.newDirName = newTitle;
                }
                candidate.collides = false;
                postResults(candidate.oldDirPath);
            } else {
                // must be 'all'
                for (Provisioning.Candidate candidate : provisioning.candidates) {
                    candidate.isSelected = true;
                }
                postResults(null);
            }

            boolean r = retainBooks || !Objects.requireNonNull(currentInstallFile.getParentFile()).canWrite();
            provisioning.moveAllSelected_Task(this::installProgress, r, renameFiles);

            bookListChanged();
            break;
        }
        case "downloads:delete": {
            boolean all = checkOperandsFor(operands, "all");
            String partialTitle = findOperandString(operands);

            //noinspection SimplifiableBooleanExpression
            if (!(all ^ (partialTitle != null))) {
                logActivityIndented("Requires exactly one of 'all' or a partial title");
                return;
            }

            if (errorIfAnyRemaining(operands)) {
                return;
            }

            buildCandidateList();
            if (partialTitle != null) {
                Provisioning.Candidate candidate = findInCandidatesList(partialTitle);
                if (candidate == null) {
                    logActivityIndented("No unique match found for " + partialTitle);
                    return;
                }
                if (FileUtilities.deleteTree(new File(candidate.oldDirPath), this::logResult)) {
                    logActivityIndented("Deleted " + candidate.oldDirPath);
                }
                postResults(candidate.oldDirPath);
            } else {
                // must be 'all'
                for (Provisioning.Candidate candidate : provisioning.candidates) {
                    if (FileUtilities.deleteTree(new File(candidate.oldDirPath), this::logResult)) {
                        logActivityIndented("Deleted " + candidate.oldDirPath);
                    }
                }
                postResults(null);
            }

            break;
        }
        default: {
            logActivityIndented("Unrecognized request ");
            break;
        }
        }
    }

    @WorkerThread
    private void settingsCommands(@NonNull List<String> operands) {
        String key = operands.get(0).toLowerCase();
        switch (key) {
            case "settings:retain": {
                String r = booleanOperand(operands);
                if (!r.equals("error")) {
                    retainBooks = r.equals("true");
                }
                logActivityIndented("Books will " + (retainBooks?"":"not ") + "be retained.");
                break;
            }
            case "settings:rename": {
                String r = booleanOperand(operands);
                if (!r.equals("error")) {
                    renameFiles = r.equals("true");
                }
                logActivityIndented("Audio filenames will " + (renameFiles?"":"not ") + "be renumbered.");
                break;
            }
            default: {
                logActivityIndented("Unrecognized request ");
                break;
            }
        }
    }

    @WorkerThread
    void buildCandidateList() {
        provisioning.candidates.clear();
        provisioning.buildCandidateList_Task(currentCandidateDir,this::installProgress);
        postResults(null);
        provisioning.joinCandidatesSubTasks();
    }

    @WorkerThread
    void installProgress(@NonNull Provisioning.ProgressKind kind, String string) {
        switch (kind) {
            case SEND_TOAST:
                // Ignore
                break;
            case FILESYSTEMS_FULL:
                Log.w("AESOP " + getClass().getSimpleName(), "PROGRESS " + kind + " " + string);
                //?????????????????????? Deal with this???????????????????????
                break;
            case BOOK_DONE:
            case ALL_DONE:
                break;
        }
    }

    @WorkerThread
    private boolean postResults(String targetFile) {
        boolean errorsFound = false;
        for (Provisioning.ErrorInfo e : provisioning.errorLogs) {
            switch (e.severity) {
            case INFO:
                Log.w("AESOP " + getClass().getSimpleName(), ">>>>log     " + e.text);
                logActivityIndented(e.text);
                break;
            case MILD:
            case SEVERE:
                Log.w("AESOP " + getClass().getSimpleName(), ">>>>log     " + e.severity + " " + e.text);
                Log.w("AESOP " + getClass().getSimpleName(), ">>>>log        ... for file" + currentInstallFile.getAbsolutePath());
                logActivityIndented(e.severity + " " + e.text);
                if (targetFile != null) {
                    logActivityIndented("  ... for file " + targetFile);
                }
                errorsFound = true;
                break;
            }
        }
        return errorsFound;
    }

    @WorkerThread
    void logResult(Provisioning.Severity severity, String text) {
        Log.w("AESOP " + getClass().getSimpleName(), ">>>>log " + severity + " " + text);
        logActivityIndented(severity + " " + text);
    }

    @WorkerThread
    private void logActivity(String s) {
        Log.w("AESOP " + getClass().getSimpleName(), ">>>>log " +s);
        resultLog.add(s);
    }

    @WorkerThread
    private void logActivityIndented(String s) {
        Log.w("AESOP " + getClass().getSimpleName(), ">>>>log " +s);
        resultLog.add("     " + s);
    }

    @WorkerThread
    private void bookListChanged () {
        // Synchronously update the book list, so it's never out of date with
        // respect to what we're doing here.

        booksChangedEvent.prepare(); // Await completion event for Books Changed
        EventBus.getDefault().post(new MediaStoreUpdateEvent());
        booksChangedEvent.await();

        // Wait until any pending duration queries complete
        audioBookManager.awaitDurationQueries();
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void onEvent(AudioBooksChangedEvent event) {
        // We just want to know it completed to move on
        booksChangedEvent.resume();
    }

    @WorkerThread
    private void sendReport() {
        // Send the report of what happened. Write it locally to a file (next to the control file)
        // and mail it (if authorized). Always do both just in case the mail fails.

        // ????????????? Remove when convenient
        for (String s:resultLog) {
            Log.w("AESOP " + getClass().getSimpleName(), "Log: " + s);
        }

        try {
            FileUtils.writeLines(new File(controlDir, resultFileName), resultLog);
        } catch (Exception e) {
            Log.w("AESOP " + getClass().getSimpleName(), "File write exception " + e);
            CrashWrapper.recordException(e);
        }

        if (sendResultTo.size() > 0) {
            // Add a trailing empty line so the join below ends with a newline
            resultLog.add("");

            Mail message = new Mail(activity)
                    .setSubject("Aesop results from request")
                    .setMessageBody(TextUtils.join("\n",resultLog));
            for (String r:sendResultTo) {
                    message.setRecipient(r);
            }
            message.sendEmail();
        }
    }

    @WorkerThread
    Provisioning.BookInfo findInBookList(String str) {
        // Find if there's exactly one entry that matches the string.
        // >1 match means it's ambiguous, so we say no matches
        Provisioning.BookInfo match = null;
        for (Provisioning.BookInfo b : provisioning.bookList) {
            b.selected = false;
            if (StringUtils.containsIgnoreCase(b.book.getDisplayTitle(), str)) {
                if (match != null) {
                    return null;
                }
                match = b;
            }
            else if (StringUtils.containsIgnoreCase(b.book.getDirectoryName(), str)) {
                if (match != null) {
                    return null;
                }
                match = b;
            }
        }
        if (match != null) {
            match.selected = true;
            return match;
        }
        return null;
    }

    @WorkerThread
    Provisioning.Candidate findInCandidatesList(String str) {
        // Find if there's exactly one entry that matches the string.
        // >1 match means it's ambiguous, so we say no matches
        Provisioning.Candidate match = null;
        for (Provisioning.Candidate c : provisioning.candidates) {
            c.isSelected = false;
            if (StringUtils.containsIgnoreCase(c.bookTitle, str)) {
                if (match != null) {
                    return null;
                }
                match = c;
            }
            else if (StringUtils.containsIgnoreCase(c.oldDirPath, str)) {
                if (match != null) {
                    return null;
                }
                match = c;
            }
        }
        if (match != null) {
            match.isSelected = true;
            return match;
        }
        return null;
    }

    @WorkerThread
    boolean checkOperandsFor(List<String> operands, String keyword) {
        for (int i=1; i<operands.size(); i++) {
            if (operands.get(i).equalsIgnoreCase(keyword)) {
                operands.remove(i);
                return true;
            }
        }
        return false;
    }

    @WorkerThread
    private String findOperandString(List<String> operands) {
        // returns (de-quoted) string (and removes it)
        for (int i=1; i<operands.size(); i++) {
            String str = operands.get(i);
            if (str.length() >= 2 && str.charAt(0) == '"' && str.endsWith("\"")) {
                operands.remove(i);
                return str.substring(1,str.length()-1);
            }
        }
        return null;
    }

    @WorkerThread
    private String findOperandText(List<String> operands) {
        // returns the next non-string word
        for (int i=1; i<operands.size(); i++) {
            String str = operands.get(i);
            if (str.charAt(0) != '"') {
                operands.remove(i);
                return str;
            }
        }
        return null;
    }

    String booleanOperand(List<String>operands) {
        if (operands.size() == 1) {
            return "query";
        }
        if (operands.size() > 2) {
            logActivityIndented( "Wrong number of operands for boolean");
        }
        boolean isTrue = checkOperandsFor(operands, "true")
            || checkOperandsFor(operands, "yes");
        checkOperandsFor(operands, "false");
        checkOperandsFor(operands, "no");

        if (errorIfAnyRemaining(operands)) {
            return "error";
        }

        return isTrue?"true":"false";
    }

    @WorkerThread
    boolean errorIfAnyRemaining(@NonNull List<String>operands) {
        // If we haven't consumed all operands (something unexpected), it's an error: print them
        if (operands.size() == 1) {
            return false;
        }
        for (int i = 1; i < operands.size(); i++) {
            String s = operands.get(i);
            logActivityIndented( "Unrecognized operand: " + s);
        }
        return true;
    }

    @WorkerThread
    String checkName(String newName) {
        // Sanity checks possible new names
        if (newName.isEmpty()
                || !newName.matches("^\\p{Print}*$")
                || newName.matches(" +")) {
            logActivityIndented("New name cannot be empty or invisible");
            return null;
        }
        newName = AudioBook.filenameCleanup(newName);

        // enforce the "space" rule
        if (newName.indexOf(' ') < 0) {
            newName += " ";
        }

        provisioning.buildBookList();
        String otherBook = provisioning.scanForDuplicateAudioBook(newName);
        if (otherBook != null) {
            logActivityIndented("New name '" + newName + "' collides with existing book '"
                + otherBook + "'");
            return null;
        }
        return newName;
    }

    private final BroadcastReceiver onBroadcastEvent = new BroadcastReceiver() {
        public void onReceive(Context context, @NonNull Intent i) {
            if (i.getAction() == null) {
                return;
            }
            switch(i.getAction()) {
                case DownloadManager.ACTION_NOTIFICATION_CLICKED:
                    Log.w("AESOP" + getClass().getSimpleName(), "notification clicked");
                    break;
                case DownloadManager.ACTION_DOWNLOAD_COMPLETE:
                    Log.w("AESOP" + getClass().getSimpleName(), "Intent download complete");
                    downloadHttp_end();
                    break;
            }
        }
    };
}
