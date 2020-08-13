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
import com.donnKey.aesopPlayer.BuildConfig;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.events.AudioBooksChangedEvent;
import com.donnKey.aesopPlayer.events.MediaStoreUpdateEvent;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.model.AudioBookManager;
import com.donnKey.aesopPlayer.ui.UiControllerBookList;
import com.donnKey.aesopPlayer.ui.UiUtil;
import com.donnKey.aesopPlayer.ui.settings.RemoteSettingsFragment;
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
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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

    private boolean continueProcessing = true;
    private boolean deleteMessage = true;
    private boolean generateReport = true;
    private Calendar processingStartTime = null;

    // ?????????????? Make these times sensible for real life, not testing
    // Conditional on DEBUG?
    //private final long interval = TimeUnit.MINUTES.toMillis(10);
    private final long interval = TimeUnit.SECONDS.toMillis(10);
    private final long startUpDelay = TimeUnit.SECONDS.toMillis(3);

    File controlDir;
    final File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    File currentCandidateDir;
    boolean candidatesIsAudioBooks; // above currently is (an) AudioBooks dir.
    boolean audioBooksBeingChanged; // we're making changes to an audioBooks dir right now.

    // Global state
    private boolean enabled;
    // For debugging
    @SuppressWarnings({"FieldCanBeLocal", "CanBeFinal"})
    private boolean consoleLogReport = false;
    @SuppressWarnings("CanBeFinal")
    private boolean consoleLog = false;

    // Per request state
    private final List<String> resultLog = new ArrayList<>();
    private final ArrayList<String> sendResultTo = new ArrayList<>();
    private DownloadManager downloadManager;
    private boolean retainBooks;
    private boolean archiveBooks;
    private boolean renameFiles;
    private Calendar messageSentTime;
    private int lineCounter; // counts non-comment lines

    // State needed for async downloads
    private long lastDownload = -1;

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

        enabled = false;
        this.provisioning = new ViewModelProvider(activity).get(Provisioning.class);
        eventBus.register(this);
        if (BuildConfig.DEBUG) {
            // If debugging, this will always read the file at startup (once), for testing.
            globalSettings.setSavedControlFileTimestamp(0);
            //consoleLog = true;
            //consoleLogReport = true;
        }
    }

    @UiThread
    public void start() {
        if (enabled) {
            // Avoid duplicates
            // ????????????? fix with singleton service?
            return;
        }
        enabled = true;
        // Do the work on a thread so we can directly call "long" operations,
        // since this is all logically synchronous.
        // Delay starting until everything else settles
        //???????????? Should be longer in real life
        Handler handler = new Handler();
        handler.postDelayed( () -> {
                Thread t = new Thread(this::pollLoop);
                t.start();
            },
            startUpDelay);
    }

    @UiThread
    public void stop() {
        Log.w("AESOP", "Remote stop");
        enabled = false;
    }


    @SuppressLint("UsableSpace")
    @WorkerThread
    public void pollLoop() {
        while (true) {
            if (!enabled) {
                // Give up until we get another start
                Log.w("AESOP" + getClass().getSimpleName(), "not enabled");
                return;
            }

            if (!RemoteSettingsFragment.getInRemoteSettings()) {
                controlDir = new File(globalSettings.getRemoteControlDir());

                if (consoleLog) {
                    Log.v("AESOP " + getClass().getSimpleName(), "Poll cycle ========================================================");
                }
                downloadManager = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);

                if (globalSettings.getFilePollEnabled()) {
                    pollControlFile();
                }
                if (globalSettings.getMailPollEnabled()) {
                    pollMail();
                }

                downloadManager = null;
            }
            else Log.w("AESOP " + getClass().getSimpleName(), "Remote settings forces skip");

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                // ignored
            }
        }
    }

    @WorkerThread
    private void pollControlFile() {
        continueProcessing = true;
        deleteMessage = true;
        generateReport = true;
        processingStartTime = Calendar.getInstance();

        File controlFile = new File(controlDir, controlFileName);
        if (!controlFile.exists()) {
            return;
        }

        long timestamp = controlFile.lastModified();
        if (timestamp <= globalSettings.getSavedControlFileTimestamp()) {
            Calendar nextAtTime = globalSettings.getSavedAtTime();
            if (nextAtTime.after(processingStartTime)) {
                return;
            }
        }

        // The file has been changed since last we looked.
        BufferedReader commands;
        try {
            commands = new BufferedReader(new FileReader(controlFile));
        } catch (FileNotFoundException e) {
            logActivity("Control File failed open " + e);
            return;
        }

        messageSentTime = Calendar.getInstance();
        messageSentTime.setTimeInMillis(timestamp);

        processCommands(commands);
        if (generateReport) {
            sendReport();
        }

        try {
            commands.close();
        } catch (IOException e) { /* ignored */ }

        if (deleteMessage) {
            // We can't actually delete it, but we can ignore it until the file
            // is changed
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.YEAR, 100);
            setEarliestSavedAtTime(cal);
        }

        globalSettings.setSavedControlFileTimestamp(timestamp);
    }

    //?????? revisit
    static Calendar lastRun = null;
    @WorkerThread
    private void pollMail() {
        // AFAICT everything done here, and the installs, will time out with an error
        // if something goes wrong, so we don't need to worry about timeouts here.
        Mail mail = new Mail();

        if (mail.open() != Mail.SUCCESS) {
            // The interval between polls is long enough that a back-off is pointless.
            // (Note: We can't get here unless it worked once, so this is probably
            // either an external login change or some external connection problem.)
            return;
        }

        // Search is for a pattern, case insensitive
        if (mail.readMail() != Mail.SUCCESS) {
            return;
        }

        for(Mail.Request request:mail) {
            continueProcessing = true;
            deleteMessage = true;
            generateReport = true;
            processingStartTime = Calendar.getInstance();

            messageSentTime = Calendar.getInstance();
            messageSentTime.setTime(request.sentTime());

            // Can't ignore old mail because it might be an "every"... those
            // could hang around for years. We rely on deletion of ephemeral mail

            // ??????????? DON'T DO THIS WHEN IN PRODUCTION
            // ??????? Thus delete lastRun
            Calendar yesterday = (Calendar) processingStartTime.clone();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);

            if (messageSentTime.before(yesterday)) {
                // more than a day old... ignore
                Log.w("AESOP " + getClass().getSimpleName(), "Ignoring old really old");
                continue;
            }
            if (lastRun != null && lastRun.after(messageSentTime))  {
                Log.w("AESOP " + getClass().getSimpleName(), "SKIP OLD MAIL");
                continue;
            }
            /*
            if (sentTime <= globalSettings.getSavedMailTimestamp()) {
                Log.w("AESOP " + getClass().getSimpleName(), "SKIP OLD MAIL");
                continue;
            }
             */

            BufferedReader commands = request.getInboundBodyStream();
            // If there's no plain-text MIME body section, we'll get null here.
            if (commands != null) {
                processCommands(commands);
                if (deleteMessage) {
                    request.delete();
                }
                if (generateReport) {
                    sendResultTo.add(request.getInboundSender());
                    sendReport();
                }
            }

            lastRun = processingStartTime;
        }
        mail.close();
    }

    @WorkerThread
    void processCommands(BufferedReader commands) {

        Log.w("AESOP " + getClass().getSimpleName(), "Processing script");
        // Reset to the same initial state each cycle
        // Start downloads in the same place each run
        currentCandidateDir = downloadDir;
        candidatesIsAudioBooks = false;
        sendResultTo.clear();
        resultLog.clear();
        lineCounter = 0;

        // "Almost always" defaults for installing
        retainBooks = false;
        archiveBooks = false;
        renameFiles = true;

        String name = globalSettings.getMailDeviceName();
        if (name == null) {
            name = "";
        }
        if (!name.isEmpty()) {
            name = "on: " + name;
        }
        logActivity("Start of request " + name + " at " + processingStartTime.getTime());

        // Read and process each line of the input stream.
        while (continueProcessing) {
            String line;
            try {
                line = commands.readLine();
            } catch (IOException e) {
                Log.w("AESOP " + getClass().getSimpleName(), "EOF catch");
                return;
            }

            if (line == null) {
                Log.w("AESOP " + getClass().getSimpleName(), "script ended");
                return;
            }
            logActivity(line);

            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            // Split on spaces, honoring quoted strings correctly, including handling
            // of escaped quotes. Since NUL is illegal in a filename...
            line = line.replace("\\\"", "\000");
            ArrayList<String> operands = new ArrayList<>(Arrays.asList(
                    line.split(" +(?=([^\"]*\"[^\"]*\")*[^\"]*$)")));
            int count;
            for (count = 0; count < operands.size(); count++) {
                if (operands.get(count).indexOf("//") == 0) {
                    // a comment
                    break;
                }
                operands.set(count, operands.get(count).replace("\000", "\""));
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
            lineCounter++;

            String key = op0.substring(0, pos + 1).toLowerCase();
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
                                break;
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
                case "run:": {
                    runCommands(operands);
                    break;
                }
                case "mailto:": {
                    Mail mail = new Mail();
                    if (mail.testConnection() != Mail.SUCCESS) {
                        logActivityIndented("Mail connection not set up: mailto: ignored");
                        break;
                    }
                    String to = op0.substring("mailto:".length());
                    sendResultTo.add(to);
                    logActivityIndented("Results will be mailed to " + to);
                    break;
                }
                case "exit:": {
                    Log.w("AESOP " + getClass().getSimpleName(), "script ended");
                    return;
                }
                default: {
                    logActivityIndented("Error: unrecognized command ignored: " + key);
                    break;
                }
            }
        }
        Log.w("AESOP " + getClass().getSimpleName(), "script ended");
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
        Log.w("AESOP " + getClass().getSimpleName(), "download start " + requested);
        Uri uri = Uri.parse(requested);
        String downloadFile = uri.getLastPathSegment();

        DownloadManager.Request downloadRequest = new DownloadManager.Request(uri);
        downloadRequest
                //??????????????????? consider if this is really right...
                // Take the default on this - presumably the user's config will take care of it
                //.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE)
                //.setAllowedOverRoaming(false)
                // The default visibility seems right as well
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
        File fileToInstall;
        // Install the book named by the "file:" fileUrl
        // We allow the otherwise illegal file:<relative pathname> (no slash after :) to mean
        // an Android sdcard-local filename.
        Asserts.checkState(fileUrl.substring(0,5).equals("file:"));
        if (fileUrl.charAt(5) != '/') {
            String pathName = fileUrl.substring(5);
            // a relative path - find relative to downloadDir's parent
            fileToInstall = new File(downloadDir.getParent(), pathName);
        }
        else {
            fileToInstall = new File(fileUrl.substring(7));
        }

        if (!fileToInstall.exists()) {
            logActivityIndented( "File not found: " + fileToInstall.getPath());
            return false;
        }

        if (title == null) {
           title = fileToInstall.getName();
        }

        File audioFile;
        if (isAudioPath(fileToInstall.getName())) {
            audioFile = fileToInstall;
        }
        else {
            audioFile = FileUtilities.findFileMatching(fileToInstall, FilesystemUtil::isAudioPath);
            if (audioFile == null) {
                logActivityIndented( "Not an audiobook: no audio files found: " + fileToInstall.getPath());
                if (FileUtilities.deleteTree(fileToInstall, this::logResult)) {
                    logActivityIndented("Deleted " + fileToInstall.getPath());
                }
                postResults(fileToInstall.getPath());
                return false;
            }
        }

        Provisioning.Candidate candidate = new Provisioning.Candidate();
        candidate.fill(title, fileToInstall.getPath(), audioFile);
        candidate.isSelected = true;
        candidate.computeDisplayTitle();

        bookListChanging(true);
        boolean r = retainBooks || !Objects.requireNonNull(fileToInstall.getParentFile()).canWrite();
        provisioning.moveOneFile_Task(candidate, this::installProgress, r, renameFiles);

        if (postResults(fileToInstall.getPath()) && priorDownload) {
            logActivityIndented("BE SURE to delete or finish installing this failed install." );
        }
        bookListChanged();

        return true;
    }

    @SuppressLint("DefaultLocale")
    @WorkerThread
    private void booksCommands(List<String> operands) {
        String key = operands.get(0).toLowerCase();
        switch (key) {
        case "books:books": {
            if (errorIfAnyRemaining(operands)) {
                return;
            }

            provisioning.buildBookList();
            provisioning.selectCompletedBooks();

            logActivityIndented("Current Books  " + provisioning.getTotalTimeSubtitle());
            logActivityIndented(" Length   Current        C W Title");
            Provisioning.BookInfo[] sortedList = provisioning.bookList.clone();
            Arrays.sort(sortedList, (l,r)->{
                // Sort on path names (not display titles)
                String lName = l.book.getPath().getPath();
                String rName = r.book.getPath().getPath();
                return lName.compareTo(rName);
            });
            String dirPath = "";
            for (Provisioning.BookInfo bookInfo : sortedList) {
               AudioBook book = bookInfo.book;
               String dir = book.getPath().getParent();
                assert dir != null;
                if (!dir.equals(dirPath)) {
                   dirPath = dir;
                   logActivityIndented("In " + dir);
               }
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
               if (book.duplicateIdCounter == 1) {
                   line = "     " + line;
               }
               else {
                   line = String.format("  %2d ",book.duplicateIdCounter) + line;
               }
               logActivity(line); // not indented... we did that
            }
            logActivityIndented("");
            break;
        }
        case "books:clean": {
            provisioning.buildBookList();
            provisioning.selectCompletedBooks();
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
            ArrayList<String> partialTitles = new ArrayList<>();
            String current;
            while ((current = findOperandString(operands)) != null) {
                partialTitles.add(current);
            }
            if (errorIfAnyRemaining(operands)) {
                return;
            }
            if (partialTitles.isEmpty()) {
                logActivityIndented("No partial titles found to delete");
                return;
            }

            provisioning.buildBookList();
            boolean OK = true;
            for (String partialTitle: partialTitles) {
                if (findInBookList(partialTitle) == null) {
                    logActivityIndented("No unique match found for "+ partialTitle);
                    OK = false;
                    // Policy: either they all match partial titles, or do nothing.
                }
            }
            if (!OK) {
                // Policy: either they all match partial titles, or do nothing.
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

            bookListChanging(true);
            bookInfo.book.renameTo(newTitle, this::logResult);
            postResults(bookInfo.book.getPath().getPath());
            bookListChanged();
            return;
        }
        case "books:reset": {
            boolean all = checkOperandsFor(operands, "all");
            String newTimeString = findOperandText(operands);
            ArrayList<String> partialTitles = new ArrayList<>();
            String current;
            while ((current = findOperandString(operands)) != null) {
                partialTitles.add(current);
            }

            if (all != partialTitles.isEmpty()) {
                logActivityIndented("Requires either 'all' or a list of partial titles.");
                return;
            }

            if (errorIfAnyRemaining(operands)) {
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

            provisioning.buildBookList();

            if (all) {
                bookListChanging(true);
                for (Provisioning.BookInfo bookInfo : provisioning.bookList) {
                    bookInfo.book.setNewTime(newTime);
                    logActivityIndented("Position in "+ bookInfo.book.getDisplayTitle() +
                            " changed to " + UiUtil.formatDurationShort(newTime));
                }
            }
            else {
                // one or more partial titles
                boolean OK = true;
                for (String partialTitle: partialTitles) {
                    Provisioning.BookInfo bookInfo = findInBookList(partialTitle);
                    if (bookInfo == null) {
                        logActivityIndented("No unique match found for " + partialTitle);
                        OK = false;
                    }
                }
                if (!OK) {
                    // Policy: all recognized or nothing
                    return;
                }
                bookListChanging(true);
                for (String partialTitle: partialTitles) {
                    Provisioning.BookInfo bookInfo = findInBookList(partialTitle);
                    if (bookInfo != null) {
                        bookInfo.book.setNewTime(newTime);
                        logActivityIndented("Position in "+ bookInfo.book.getDisplayTitle() +
                                " changed to " + UiUtil.formatDurationShort(newTime));
                    }
                }
            }
            bookListChanged();
            postResults(null);

            break;
        }
        default:
            logActivityIndented("Unrecognized request ");
            break;
        }
    }

    @WorkerThread
    private void runDelete() {
        bookListChanging(true);
        provisioning.deleteAllSelected_Task(this::installProgress, archiveBooks);
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

            candidatesIsAudioBooks = false;
            final List<File> audioBooksDirs = FilesystemUtil.audioBooksDirs(getAppContext());
            for (File activeStorage : audioBooksDirs) {
                if (currentCandidateDir.equals(activeStorage)) {
                    candidatesIsAudioBooks = true;
                    break;
                }
            }
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
            } else {
                // must be 'all'
                for (Provisioning.Candidate candidate : provisioning.candidates) {
                    candidate.isSelected = true;
                }
            }

            bookListChanging(true); // this op's effects are to the book list!
            boolean r = retainBooks || !currentCandidateDir.canWrite();
            provisioning.moveAllSelected_Task(this::installProgress, r, renameFiles);
            postResults(null);
            bookListChanged();

            break;
        }
        case "downloads:delete": {
            boolean all = checkOperandsFor(operands, "all");
            ArrayList<String> partialTitles = new ArrayList<>();
            String current;
            while ((current = findOperandString(operands)) != null) {
                partialTitles.add(current);
            }

            if (all != partialTitles.isEmpty()) {
                logActivityIndented("Requires either 'all' or a list of partial titles.");
                return;
            }

            if (errorIfAnyRemaining(operands)) {
                return;
            }

            buildCandidateList();

            if (all) {
                bookListChanging(false);
                for (Provisioning.Candidate candidate : provisioning.candidates) {
                    if (FileUtilities.deleteTree(new File(candidate.oldDirPath), this::logResult)) {
                        logActivityIndented("Deleted " + candidate.oldDirPath);
                    }
                }
            } else {
                // one or more partial titles
                boolean OK = true;
                for (String partialTitle: partialTitles) {
                    Provisioning.Candidate candidate = findInCandidatesList(partialTitle);
                    if (candidate == null) {
                        logActivityIndented("No unique match found for " + partialTitle);
                        OK = false;
                    }
                }
                if (!OK) {
                    // Policy: all recognized or nothing
                    return;
                }
                bookListChanging(false);
                for (String partialTitle: partialTitles) {
                    Provisioning.Candidate candidate = findInCandidatesList(partialTitle);
                    if (candidate != null) {
                        if (FileUtilities.deleteTree(new File(candidate.oldDirPath), this::logResult)) {
                            logActivityIndented("Deleted " + candidate.oldDirPath);
                        }
                    }
                }
            }
            bookListChanged();
            postResults(null);

            break;
        }
        case "downloads:group": {
            String target = findOperandString(operands);
            if (target == null) {
                logActivityIndented("A new book name is required");
                return;
            }

            buildCandidateList();

            String partialTitle;
            int candidates = 0;
            Provisioning.Candidate firstCandidate = null;
            while((partialTitle = findOperandString(operands)) != null) {
                Provisioning.Candidate candidate = findInCandidatesList(partialTitle);
                if (candidate == null) {
                    logActivityIndented("No unique match found for " + partialTitle);
                    candidates = -10000;
                }
                if (firstCandidate == null) {
                    firstCandidate  = candidate;
                }
                candidates++;
            }
            if (candidates < 0) {
                // This so we check them all for matches
                return;
            }
            if (candidates == 0) {
                logActivityIndented("No books found to combine.");
                return;
            }
            if (errorIfAnyRemaining(operands)) {
                return;
            }
            if (target.indexOf('/') >= 0) {
                logActivityIndented("New book name must be a name, not a path");
                return;
            }
            assert(firstCandidate != null);

            // The parent plus the new name -> targetFile
            File targetFile = new File(firstCandidate.oldDirPath).getParentFile();
            targetFile = new File(targetFile, target);
            if (targetFile.exists()) {
                logActivityIndented("Book already exists.");
                return;
            } else if (!targetFile.mkdirs()) {
                logActivityIndented("Could not make book directory.");
                return;
            }

            bookListChanging(false);
            logActivityIndented("Grouping " + candidates + " books into " + target);
            provisioning.groupAllSelected_execute(targetFile);
            postResults(null);

            // Update the candidate list since we know it changed.
            // And just in case we were in AudioBooks, the bookList too.
            bookListChanged();
            buildCandidateList();

            break;
        }
        case "downloads:ungroup": {
            String partialTitle = findOperandString(operands);
            if (partialTitle == null) {
                logActivityIndented("An existing book name is required");
                return;
            }

            if (errorIfAnyRemaining(operands)) {
                return;
            }

            buildCandidateList();
            bookListChanging(false);
            Provisioning.Candidate groupDir = findInCandidatesList(partialTitle);
            if (groupDir == null) {
                logActivityIndented("Book to ungroup not uniquely matched.");
                return;
            }
            logActivityIndented("Ungrouping " + groupDir.newDirName);
            provisioning.unGroupSelected_execute();
            postResults(null);
            bookListChanged();

            // Update the candidate list since we know it changed.
            // And just in case we were in AudioBooks, the bookList too.
            buildCandidateList();

            break;
        }
        case "downloads:rawfiles": {
            final String[] files = currentCandidateDir.list();
            if (files == null) {
                logActivityIndented("Directory is empty");
                return;
            }

            Arrays.sort(files, String::compareTo);
            final int[] lengths = new int[(files.length)];

            int max = 0;
            for (int i=0; i<files.length; i++) {
               int len = files[i].length() + StringUtils.countMatches(files[i], '"');
               max = Math.max(len,max);
               lengths[i] = len;
            }

            final int columnWidth = 80;
            final int gutterWidth = 1;
            final int remaining = columnWidth - max;

            // A preliminary number of columns
            int nColumns = (remaining/(gutterWidth + 2)) + 1;  // assume a gutter width, and 2 character filenames.
            int nRows = files.length;
            int[] widthList = new int[nColumns];
            while (nColumns > 1) {
                nRows = (files.length+nColumns)/nColumns;

                for (int col = 0; col < nColumns; col++) {
                    int maxLen = 0; // the longest item in the current column
                    for (int row = 0; row < nRows; row++) {
                        int item = col * nRows + row;
                        if (item >= lengths.length) {
                            break;
                        }
                        maxLen = Math.max(maxLen, lengths[item]);
                    }
                    widthList[col] = maxLen;
                }

                int lengthSum = 0;
                for (int i=0; i<nColumns; i++) {
                    lengthSum += widthList[i];
                }

                if (lengthSum + (nColumns-1)*gutterWidth <= columnWidth) {
                    // It fits, we have the layout
                    break;
                }
                // it won't fit
                nColumns--;
            }

            final StringBuilder text = new StringBuilder(columnWidth+1);
            final String emptySpace = StringUtils.repeat(' ', columnWidth/2);
            for (int row = 0; row < nRows; row++) {
                for (int col = 0; col < nColumns; col++) {
                    int item = col * nRows + row;
                    if (item >= lengths.length) {
                        break;
                    }
                    String fileName = files[item];
                    if (fileName.indexOf('"') >= 0) {
                        // it contains the quotes we allowed for above
                        fileName = fileName.replaceAll("\"", "\\\\\"");
                    }
                    text.append('"');
                    text.append(fileName);
                    text.append('"');
                    int spaces = widthList[col] - lengths[item] + gutterWidth;
                    text.append(emptySpace, 0, spaces);
                }
                logActivityIndented(text.toString());
                text.setLength(0);
            }

            break;
        }
        case "downloads:rawdelete": {
            ArrayList<String> files = new ArrayList<>();
            String current;
            while ((current = findOperandString(operands)) != null) {
                files.add(current);
            }
            if (errorIfAnyRemaining(operands)) {
                return;
            }
            if (files.isEmpty()) {
                logActivityIndented("No file names provided to delete");
                return;
            }

            bookListChanging(false);
            for (String fileName: files) {
                File toDelete = new File(currentCandidateDir, fileName);
                if (!toDelete.exists()) {
                    logActivityIndented("File " + toDelete.getPath() + " already deleted.");
                }
                else {
                    logActivityIndented("Deleting File " + toDelete.getPath());
                    if (!FileUtilities.deleteTree(toDelete, this::logResult)) {
                        postResults(fileName);
                    }
                }
            }
            bookListChanged();

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
            case "settings:archive": {
                String r = booleanOperand(operands);
                if (!r.equals("error")) {
                    archiveBooks = r.equals("true");
                }
                logActivityIndented("Deleted Books will " + (archiveBooks?"":"not ") + "be archived.");
                break;
            }
            case "settings:retain": {
                String r = booleanOperand(operands);
                if (!r.equals("error")) {
                    retainBooks = r.equals("true");
                }
                logActivityIndented("Installed books will " + (retainBooks?"":"not ") + "be retained.");
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
    private void runCommands(@NonNull List<String> operands) {
        // returns true when the script should be run
        // All times in device local time
        String key = operands.get(0).toLowerCase();
        switch (key) {
            case "run:at": {
                if (lineCounter != 1) {
                    logActivityIndented("run:at must be the first command");
                    continueProcessing = false;
                    deleteMessage = true;
                    return;
                }
                String timeString = findOperandText(operands);
                if (timeString == null) {
                    logActivityIndented("run:at requires time-of-day operand(s)");
                    continueProcessing = false;
                    deleteMessage = true;
                    return;
                }
                String more = findOperandText(operands);
                if (more != null) {
                    timeString += more;
                }

                if (errorIfAnyRemaining(operands)) {
                    logActivityIndented("Too many operands for run:at");
                    continueProcessing = false;
                    deleteMessage = true;
                    return;
                }

                Calendar scheduledStartTime = Calendar.getInstance();

                Calendar requestedHHMM = parseTimeOfDay(timeString);
                if (requestedHHMM == null) {
                    continueProcessing = false;
                    deleteMessage = true;
                    return;
                }

                int hour = requestedHHMM.get(Calendar.HOUR_OF_DAY);
                int minute = requestedHHMM.get(Calendar.MINUTE);
                scheduledStartTime.set(Calendar.HOUR_OF_DAY, hour);
                scheduledStartTime.set(Calendar.MINUTE, minute);
                scheduledStartTime.set(Calendar.SECOND, 0);
                if (scheduledStartTime.before(messageSentTime)) {
                    scheduledStartTime.add(Calendar.DAY_OF_YEAR, 1);
                }

                if (processingStartTime.before(scheduledStartTime)) {
                    logActivityIndented("Skipping run until scheduled time of " + scheduledStartTime.getTime());
                    setEarliestSavedAtTime(scheduledStartTime);
                    globalSettings.setSavedControlFileTimestamp(scheduledStartTime.getTimeInMillis()-1);
                    continueProcessing = false;
                    generateReport = false;
                    deleteMessage = false;
                    return;
                }

                logActivityIndented("Delayed Request scheduled for " + scheduledStartTime.getTime() + " runs.");
                return;
            }
            case "run:every": {
                if (lineCounter != 1) {
                    logActivityIndented("run:every must be the first command");
                    continueProcessing = false;
                    deleteMessage = true;
                    return;
                }
                int todayDayName = processingStartTime.get(Calendar.DAY_OF_WEEK);
                DateFormatSymbols dfs = new DateFormatSymbols(Locale.US);
                String[] weekdays = dfs.getWeekdays();

                boolean matchedToday = false;
                for (int d = Calendar.SUNDAY; d <= Calendar.SATURDAY; d++) {
                    if (checkOperandsFor(operands, weekdays[d])) {
                        matchedToday |= d == todayDayName;
                    }
                    if (checkOperandsFor(operands, weekdays[d].substring(0,3))) {
                        matchedToday |= d == todayDayName;
                    }
                }

                matchedToday |= checkOperandsFor(operands, "everyday");
                matchedToday |= checkOperandsFor(operands, "every");

                if (checkOperandsFor(operands, "weekday")
                    || checkOperandsFor(operands, "week")) {
                    matchedToday |= (todayDayName >= Calendar.MONDAY && todayDayName <= Calendar.FRIDAY);
                }

                String timeString = findOperandText(operands);

                if (errorIfAnyRemaining(operands)) {
                    logActivityIndented("Unrecognized operands for run:every");
                    continueProcessing = false;
                    deleteMessage = true;
                    return;
                }

                Calendar scheduledStartTime = Calendar.getInstance();
                //noinspection IfStatementWithIdenticalBranches
                if (timeString != null) {

                    Calendar requestedHHMM = parseTimeOfDay(timeString);
                    if (requestedHHMM == null) {
                        continueProcessing = false;
                        deleteMessage = true;
                        return;
                    }

                    int hour = requestedHHMM.get(Calendar.HOUR_OF_DAY);
                    int minute = requestedHHMM.get(Calendar.MINUTE);
                    scheduledStartTime.set(Calendar.HOUR_OF_DAY, hour);
                    scheduledStartTime.set(Calendar.MINUTE, minute);
                    scheduledStartTime.set(Calendar.SECOND, 0);
                }
                else {
                    // No time parameter, default to 0300
                    scheduledStartTime.set(Calendar.HOUR_OF_DAY, 3);
                    scheduledStartTime.set(Calendar.MINUTE, 0);
                    scheduledStartTime.set(Calendar.SECOND, 0);
                }

                // It's valid, and thus actually runnable. Don't delete.
                deleteMessage = false;

                if (!matchedToday) {
                    // Not today
                    continueProcessing = false;
                    generateReport = false;

                    // Set it to 00:01 tomorrow, and see when/if it should actually run then.
                    scheduledStartTime.set(Calendar.MINUTE, 1);
                    scheduledStartTime.set(Calendar.HOUR_OF_DAY, 24);
                    setEarliestSavedAtTime(scheduledStartTime);
                    // (No log here! -- one isn't even generated)
                    return;
                }

                if (processingStartTime.before(scheduledStartTime)) {
                    // Later today
                    continueProcessing = false;
                    generateReport = false;
                    setEarliestSavedAtTime(scheduledStartTime);
                    logActivityIndented("Run at:every -- run later today at " + scheduledStartTime.getTime() + ".");
                    return;
                }

                // Earlier today (now!)
                logActivityIndented("Run at:every -- Starts at " + scheduledStartTime.getTime() + ".");

                scheduledStartTime.set(Calendar.MINUTE, 1);
                scheduledStartTime.set(Calendar.HOUR_OF_DAY, 24);
                setEarliestSavedAtTime(scheduledStartTime);
                return;
            }
            default: {
                logActivityIndented("Unrecognized request ");
                break;
            }
        }
    }

    Calendar parseTimeOfDay (String timeString) {
        Date result = null;

        DateFormat formatter = new SimpleDateFormat("hh:mma", Locale.US);
        try {
            result = formatter.parse(timeString);
        } catch (ParseException e) {
            // ignore
        }

        if (result == null) {
            if (timeString.matches("\\d\\d\\d\\d")) {
                formatter = new SimpleDateFormat("HHmm", Locale.US);
                try {
                    result = formatter.parse(timeString);
                } catch (ParseException e) {
                    // ignore
                }
            }
        }

        if (result == null) {
            logActivityIndented("Time not parsed: must be either HH:mm [AM|PM] (12 hour time) or HHMM (24 hour time)");
            return null;
        }

        Calendar cal = Calendar.getInstance();
        cal.setTime(result);

        return cal;
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
                // Occurs only on book install.
                // An error was posted just before this error is reported.
                // That error will show up in postResults, where the user will see it,
                // along with a further warning to clean up.
                // Nothing to do here.
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
                logActivityIndented(e.text);
                break;
            case MILD:
            case SEVERE:
                logActivityIndented(e.severity + " " + e.text);
                if (targetFile != null) {
                    logActivityIndented("  ... for file " + targetFile);
                }
                errorsFound = true;
                break;
            }
        }

        provisioning.clearErrors();
        return errorsFound;
    }

    @WorkerThread
    void logResult(Provisioning.Severity severity, String text) {
        if (consoleLog) {
            Log.v("AESOP " + getClass().getSimpleName(), ">>>>log " + severity + " " + text);
        }
        logActivityIndented(severity + " " + text);
    }

    @WorkerThread
    private void logActivity(String s) {
        if (consoleLog) {
            Log.v("AESOP " + getClass().getSimpleName(), ">>>>log " + s);
        }
        resultLog.add(s);
    }

    @WorkerThread
    private void logActivityIndented(String s) {
        if (consoleLog) {
            Log.v("AESOP " + getClass().getSimpleName(), ">>>>log " + s);
        }
        resultLog.add("     " + s);
    }

    @WorkerThread
    private void bookListChanging (boolean alwaysAudioBooks) {
        audioBooksBeingChanged = alwaysAudioBooks;
        if (alwaysAudioBooks || candidatesIsAudioBooks) {
            // The candidates directory is the AudioBooks directory,
            // and this operation changes the candidates directory.
            // Thus it changes the audioBooks directory, and we must deal with that.
            UiControllerBookList.suppressAnnounce();
            audioBooksBeingChanged = true;
        }
    }

    @WorkerThread
    private void bookListChanged () {
        // Synchronously update the book list, so it's never out of date with
        // respect to what we're doing here.
        // ??????????????? is there a cleaner design than the quasi-global?
        if (!audioBooksBeingChanged) {
            return;
        }

        booksChangedEvent.prepare(); // Await completion event for Books Changed
        EventBus.getDefault().post(new MediaStoreUpdateEvent());
        booksChangedEvent.await();

        // Wait until any pending duration queries complete
        audioBookManager.awaitDurationQueries();
        UiControllerBookList.resumeAnnounce();
        audioBooksBeingChanged = false;
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void onEvent(AudioBooksChangedEvent event) {
        // We just want to know it completed to move on
        booksChangedEvent.resume();
    }

    @SuppressLint("UsableSpace")
    @WorkerThread
    private void sendReport() {
        // Send the report of what happened. Write it locally to a file (next to the control file)
        // and mail it (if authorized). Always do both just in case the mail fails.

        // Tell the user about space remaining
        final List<File>audioBooksDirs = FilesystemUtil.audioBooksDirs(getAppContext());
        for (File activeStorage : audioBooksDirs) {
            long remainingSpace = activeStorage.getTotalSpace() - activeStorage.getUsableSpace();
            logActivity("Space on " + activeStorage.getParent() + ": Using " +
                    remainingSpace/1000000 + "Mb of " +
                    activeStorage.getTotalSpace()/1000000 + "Mb (" +
                    (int)(((float)remainingSpace/
                            (float)activeStorage.getTotalSpace())*100) + "%)");
        }

        String name = globalSettings.getMailDeviceName();
        if (name == null) {
            name = "";
        }
        if (!name.isEmpty()) {
            name = "on " + name;
        }
        logActivity("End of request " + name + " at " + Calendar.getInstance().getTime());

        if (consoleLogReport) {
            Log.v("AESOP " + getClass().getSimpleName(), "**********************************");
            for (String s : resultLog) {
                Log.v("AESOP " + getClass().getSimpleName(), "Log: " + s);
            }
            Log.v("AESOP " + getClass().getSimpleName(), "**********************************");
        }


        File resultFile = new File(controlDir, resultFileName);
        try {
            FileUtils.writeLines(resultFile, resultLog);
        } catch (Exception e) {
            Log.w("AESOP " + getClass().getSimpleName(), "File write exception " +  resultFile.getPath() + " " + e);
            CrashWrapper.recordException(e);
        }

        if (sendResultTo.size() > 0) {
            // Add a trailing empty line so the join below ends with a newline
            resultLog.add("");

            Mail message = new Mail()
                    .setSubject("Aesop results from request " + name)
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
        // Can accumulate multiple selected lines if repeatedly called.
        Provisioning.BookInfo match = null;
        for (Provisioning.BookInfo b : provisioning.bookList) {
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
        // Can accumulate multiple selected lines if repeatedly called.
        Provisioning.Candidate match = null;
        for (Provisioning.Candidate c : provisioning.candidates) {
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

    private void setEarliestSavedAtTime(@NonNull Calendar nextStart) {
        if (!globalSettings.getSavedAtTime().after(processingStartTime)) {
            //If the saved time is before "now" (we just ran), just take the new time
            // (!after is <=)
            globalSettings.setSavedAtTime(nextStart);
        }
        else if (nextStart.before(globalSettings.getSavedAtTime())) {
            // The saved time is in the future; if this is sooner, use it instead.
            // (This can happen if there are several at or every commands queued in the mailbox.)
            globalSettings.setSavedAtTime(nextStart);
        }
    }

    private final BroadcastReceiver onBroadcastEvent = new BroadcastReceiver() {
        public void onReceive(Context context, @NonNull Intent i) {
            if (i.getAction() == null) {
                return;
            }
            long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            //Checking if the received broadcast is for our enqueued download by matching download id
            // ??????????? invert order of id check?
            if (lastDownload == id) {
                switch(i.getAction()) {
                    case DownloadManager.ACTION_NOTIFICATION_CLICKED:
                        break;
                    case DownloadManager.ACTION_DOWNLOAD_COMPLETE:
                        downloadHttp_end();
                        break;
                }
            }
        }
    };
}
//??????????????????????? clean up per-app storage on emulator
