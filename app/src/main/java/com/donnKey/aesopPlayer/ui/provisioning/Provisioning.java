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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.MediaStoreUpdateObserver;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.events.AudioBooksChangedEvent;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.model.AudioBookManager;
import com.donnKey.aesopPlayer.service.PlaybackService;
import com.donnKey.aesopPlayer.ui.UiUtil;
import com.donnKey.aesopPlayer.util.AwaitResume;
import com.donnKey.aesopPlayer.util.FilesystemUtil;
import com.google.common.base.Preconditions;

import org.apache.commons.compress.utils.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import static android.os.Looper.getMainLooper;
import static com.donnKey.aesopPlayer.AesopPlayerApplication.getAppContext;

// Serves as a cache for inter-fragment communication
// Since it's also needed for RemoteAuto, a ViewModel doesn't work, but a singleton is fine.
@Singleton
public class Provisioning implements ServiceConnection {
    @Inject @Named("AUDIOBOOKS_DIRECTORY") public String audioBooksDirectoryName;
    @Inject public AudioBookManager audioBookManager;
    @Inject public GlobalSettings globalSettings;

    // Types used in this cache
    public enum Severity {INFO, MILD, SEVERE}

    @SuppressWarnings("FieldCanBeLocal")
    final private int justALittleRead = 60; // seconds
    public final File defaultCandidateDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    final List<File>audioBooksDirs = FilesystemUtil.audioBooksDirs(getAppContext());

    private static final String TAG="Provisioning";
    final MediaStoreUpdateObserver mediaStoreUpdateObserver;
    PlaybackService playbackService;
    final AwaitResume pendingPlayback = new AwaitResume();

    public Provisioning() {
        AesopPlayerApplication.getComponent().inject(this);
        EventBus.getDefault().register(this);

        mediaStoreUpdateObserver
                = new MediaStoreUpdateObserver(new Handler(getMainLooper()));
        getAppContext().getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaStoreUpdateObserver);
    }

    static class Candidate {
        String newDirName = null;
        String oldDirPath = null;
        File audioPath = null;
        String audioFile = null;
        String metadataTitle = null;
        String metadataAuthor = null;
        String bookTitle = null;
        boolean isSelected = false;
        boolean collides = false; // ... with existing directory name (not book name)

        void fill (@NonNull String dirName, @NonNull String dirPath, @NonNull File audioPath) {
            this.newDirName = dirName;
            this.oldDirPath = dirPath;
            this.audioPath = audioPath;

            this.audioFile = audioPath.getName();
        }

        @WorkerThread
        void computeDisplayTitle() {
            // candidate.audioPath must be filled in, or this wouldn't be a candidate.
            AudioBook.TitleAndAuthor title = AudioBook.extractTitle(new File(oldDirPath), audioPath);

            metadataTitle = title.title;
            metadataAuthor = title.author;

            if (newDirName.contains(" ")) {
                bookTitle = AudioBook.filenameCleanup(newDirName);
            } else {
                bookTitle = AudioBook.computeTitle(title);
            }

            // If the audio file is in the cache dir, it's extracted from a zip, and should
            // be tossed now that we've processed it. Otherwise, it's a real file and should
            // be left alone.
            FileUtilities.removeIfTemp(audioPath);
        }
    }

    static class BookInfo {
        final AudioBook book;
        boolean selected;
        final boolean unWritable;
        final boolean current;
        BookInfo(AudioBook book, boolean unWritable, boolean current) {
            this.book = book;
            this.selected = false;
            this.unWritable = unWritable;
            this.current = current;
        }
    }

    // Common data for UI side
    // The fragment id to return to upon rotation (etc.)
    int currentFragment = 0;

    // Action bar titles
    String windowTitle;
    String windowSubTitle;
    String errorTitle;

    // Used for parameter passing to "edit" fragments
    Object fragmentParameter;

    // The cached data we're operating on:

    ArrayList<String> downloadDirs = null;
    // ... current directory containing new books and the books
    File candidateDirectory;
    long candidatesTimestamp;
    final List<Candidate> candidates = new ArrayList<>();

    // ... associated with existing books
    BookInfo[] bookList;
    long totalTime;
    boolean partiallyUnknown;

    String getTotalTimeSubtitle() {
        return String.format(
            getAppContext().getString(R.string.fragment_subtitle_total_length),
            partiallyUnknown ?
                    getAppContext().getString(R.string.fragment_subtitle_total_length_greater_than) : "",
            UiUtil.formatDuration(totalTime));
    }

    // ... error reporting
    static class ErrorInfo {
        final String text;
        final Severity severity;

        ErrorInfo(Severity severity, String text) {
            this.severity = severity;
            this.text = text;
        }

        @NonNull
        @Override
        public String toString() {
            String prefix = "";

            switch (severity){
            case INFO:
                prefix = getAppContext().getString(R.string.status_name_info);
                break;
            case MILD:
                prefix = getAppContext().getString(R.string.status_name_check);
                break;
            case SEVERE:
                prefix = getAppContext().getString(R.string.status_name_error);
                break;
            }
            return prefix + text;
        }
    }

    final List<ErrorInfo>errorLogs = new ArrayList<>();

    void clearErrors() {
        errorLogs.clear();
    }

    private void logResult(Provisioning.Severity severity, String text) {
        errorLogs.add(new Provisioning.ErrorInfo(severity, text));
    }

    // Data functions
    void buildBookList() {
        List<AudioBook> audioBooks = audioBookManager.getAudioBooks();

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (audioBooks) {
            bookList = new BookInfo[audioBooks.size()];

            totalTime = 0;
            partiallyUnknown = false;
            for (int i = 0; i < audioBooks.size(); i++) {
                AudioBook book = audioBooks.get(i);

                bookList[i] = new BookInfo(book, !book.getPath().canWrite(),
                        book == audioBookManager.getCurrentBook());
                long t = book.getTotalDurationMs();
                if (t != AudioBook.UNKNOWN_POSITION) {
                    totalTime += book.getTotalDurationMs();
                } else {
                    computeBookDuration(book);
                    partiallyUnknown = true;
                }
            }
        }

        refreshCollisionState();
    }

    void selectCompletedBooks() {
        // A completed book that isn't in its first 10 seconds is likely being reread.
        // Don't suggest deletion.
        for (Provisioning.BookInfo book : bookList) {
            book.selected = book.book.getCompleted()
                && TimeUnit.MILLISECONDS.toSeconds(book.book.toMs(book.book.getLastPosition()))
                    < justALittleRead;
        }
    }

    // All the tasks we spun off, so we can collect them later to be sure the task
    // is complete before proceeding.
    final List<Thread> candidatesSubTasks = new ArrayList<>();

    @WorkerThread
    void buildCandidateList_Task(@NonNull File candidateDirectory, Progress progress) {
        // Must run inside a task; will be slow particularly for zips
        // We call notifier several times so that there's indication of progress to
        // the user while the long operations of digging into a zip file happen.
        candidatesSubTasks.clear();
        String[] dirList = candidateDirectory.list();
        if (dirList == null) {
            return;
        }
        List<String> files = Arrays.asList(dirList);
        Collections.sort(files, String::compareToIgnoreCase);

        for (String fileName : files) {
            File pathToTarget = new File(candidateDirectory, fileName);

            // This might return a file in the cache dir, which should later be deleted.
            // This can be expensive depending on the content
            File audioPath = FileUtilities.findFileMatching(pathToTarget, FilesystemUtil::isAudioPath);

            if (audioPath != null) {
                Candidate candidate = new Candidate();
                candidate.newDirName = AudioBook.filenameCleanup(fileName);
                progress.progress(ProgressKind.SEND_TOAST, candidate.newDirName);
                candidate.fill(
                        AudioBook.filenameCleanup(fileName),
                        pathToTarget.getPath(),
                        audioPath);

                synchronized (candidates) {
                    candidates.add(candidate);
                }

                if (scanForDuplicateAudioBook(candidate.newDirName) != null) {
                    candidate.collides = true;
                }
                progress.progress(ProgressKind.SEND_TOAST, candidate.audioFile);

                final Candidate c = candidate;
                // Theoretically, we should throttle the number of threads, but since the number
                // of books is small and these terminate soon enough...
                Thread t = new Thread(()-> {
                    // This can be very expensive
                    c.computeDisplayTitle();
                    progress.progress(ProgressKind.BOOK_DONE, c.bookTitle);
                });
                t.setPriority(Thread.MIN_PRIORITY);
                t.start();
                candidatesSubTasks.add(t);
            }
        }

        // Just to be sure
        progress.progress(ProgressKind.ALL_DONE, "");
        candidatesTimestamp = candidateDirectory.lastModified();
    }

    void joinCandidatesSubTasks() {
        for (Thread t : candidatesSubTasks) {
            try {
                t.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    @WorkerThread
    private void refreshCollisionState() {
        // Something changed in the books directories to get here... possibly a
        // new or removed collision.
        for (Candidate c: candidates) {
            if (c.newDirName != null) {
                c.collides = scanForDuplicateAudioBook(c.newDirName) != null;
            }
        }
    }

    @WorkerThread
    String scanForDuplicateAudioBook(String dirname) {
        for (File currentAudioBooks : audioBooksDirs) {
            File possibleBook = new File(currentAudioBooks, dirname);
            if (possibleBook.exists()) {
                return possibleBook.getPath();
            }
        }
        return null;
    }

    @WorkerThread
    void moveOneFile_Task(Provisioning.Candidate candidate,
                          Progress progress, boolean retainBooks, boolean renameFiles) {
        clearErrors();

        Provisioning.Candidate[] currentCandidates = new Provisioning.Candidate[1];
        currentCandidates[0] = candidate;

        moveAllSelected_pass1(currentCandidates, progress, retainBooks, renameFiles);
        moveAllSelected_pass2(currentCandidates, progress, retainBooks, renameFiles);

        progress.progress(ProgressKind.ALL_DONE, null);
    }

    @WorkerThread
    void moveAllSelected_Task(Progress progress, boolean retainBooks, boolean renameFiles) {
        clearErrors();

        final Provisioning.Candidate[] currentCandidates
                = candidates.toArray(new Provisioning.Candidate[0]);

        moveAllSelected_pass1(currentCandidates, progress, retainBooks, renameFiles);
        moveAllSelected_pass2(currentCandidates, progress, retainBooks, renameFiles);

        progress.progress(ProgressKind.ALL_DONE, null);
    }

    @SuppressLint("UsableSpace")
    @WorkerThread
    private void moveAllSelected_pass1(Provisioning.Candidate[] currentCandidates,
                                       Progress progress, boolean retainBooks, boolean renameFiles) {
        // Pass one: simple renames on the same file system (operations that don't change
        // the space used even temporarily). Thus avoiding interaction with the available space
        // checks.

        if (retainBooks) {
            // No-op, since it will take space
            return;
        }


        // Iterate through all the files. If we need space to copy to, advance the pointer to
        // the several AudioBooks directories when needed.
        for (int candidateIndex = 0; candidateIndex < currentCandidates.length; /* void */) {
            Candidate candidate = currentCandidates[candidateIndex];
            if (!candidate.isSelected || candidate.collides) {
                // So we do nothing when no items selected
                candidateIndex++;
                continue;
            }

            candidateIndex++;

            File fromDir = new File(candidate.oldDirPath);
            File newTree;

            if (FileUtilities.isZip(candidate.oldDirPath)) {
                // Ignore zips since they take space at least while expanding
                continue;
            }

            progress.progress(ProgressKind.SEND_TOAST, fromDir.getName());
            if (fromDir.isDirectory()) {
                newTree = moveToSameFs(fromDir, candidate.newDirName, null);
            } else {
                // Ordinary file.
                // In this case, "fromDir" is really an audio file, not a directory.
                // This must be an audio file, or it wouldn't be a candidate.
                newTree = moveToSameFs(fromDir, candidate.newDirName, candidate.audioFile);
            }

            if (newTree != null) {
                logResult(Severity.INFO,
                    String.format(getAppContext().getString(R.string.info_book_installed),
                            candidate.bookTitle, newTree.getPath()));

                candidate.isSelected = false;
                candidates.remove(candidate);
                if (renameFiles) {
                    FileUtilities.treeNameFix(newTree, this::logResult);
                }
            }

            progress.progress(ProgressKind.BOOK_DONE, fromDir.getName());
        }
    }

    @SuppressLint("UsableSpace")
    @WorkerThread
    private void moveAllSelected_pass2(@NonNull final Provisioning.Candidate[] currentCandidates,
                                       Progress progress, boolean retainBooks, boolean renameFiles) {
        // Pass 2: These operations use space (copying or expanding archives)
        // Note: "moveToSameFs" shouldn't normally be called from here (everything got done in pass one)
        // It's a fail-soft in the case of is a nearly full file system than fails a directory move
        // because of the space needed for a name change. (The error will get reported here if
        // the retry fails.)

        // Find the (possibly several) target directories.
        List<File> dirsToUse = audioBooksDirs;

        int nextDir = 0;
        File activeStorage = null;

        // the several AudioBooks directories when needed.
        for (int candidateIndex = 0; candidateIndex < currentCandidates.length; /* void */) {
            Candidate candidate = currentCandidates[candidateIndex];
            if (!candidate.isSelected || candidate.collides) {
                // So we do nothing when no items selected
                candidateIndex++;
                continue;
            }

            if (activeStorage == null) {
                if (nextDir >= dirsToUse.size()) {
                    logResult(Severity.SEVERE, getAppContext().getString(R.string.error_all_file_systems_full));
                    // Force the user to notice this.
                    progress.progress(ProgressKind.FILESYSTEMS_FULL, null);
                    break;
                }
                activeStorage = dirsToUse.get(nextDir++);
                if (!activeStorage.exists()) {
                    logResult(Severity.INFO, String.format(getAppContext().getString(R.string.no_such_directory), activeStorage.getPath()));
                    activeStorage = null;
                    continue;
                }
                if (!activeStorage.canWrite()) {
                    logResult(Severity.INFO, String.format(getAppContext().getString(R.string.directory_not_writable), activeStorage.getPath()));
                    // Try the next one
                    activeStorage = null;
                    continue;
                }
            }

            if ((float) activeStorage.getUsableSpace() / (float) activeStorage.getTotalSpace() < 0.1f) {
                logResult(Severity.MILD, String.format(getAppContext().getString(R.string.error_specific_file_system_full), activeStorage.getPath()));
                // Try the next one
                activeStorage = null;
                continue;
            }

            candidateIndex++;

            File fromDir = new File(candidate.oldDirPath);
            File toDir = new File(activeStorage, candidate.newDirName);
            if (toDir.exists()) {
                candidate.isSelected = false;
                logResult(Severity.SEVERE, String.format(getAppContext().getString(R.string.error_duplicate_book_name), toDir.getPath()));
                continue;
            }

            File newTree = null;

            if (FileUtilities.isZip(candidate.oldDirPath)) {
                // Zip files always get unpacked into their target location, where (presumably)
                // there's enough space. Error and remove the partial unpack on failure.
                if (!FileUtilities.unzipAll(fromDir, toDir,
                        (fn) -> progress.progress(ProgressKind.SEND_TOAST, fn),
                        this::logResult)) {
                    FileUtilities.deleteTree(toDir, this::logResult);
                    break;
                }
                candidate.isSelected = false;

                // Just in case there are any inner zips that we unpacked.
                if (!FileUtilities.expandInnerZips(toDir,
                        (fn) -> progress.progress(ProgressKind.SEND_TOAST, fn),
                        this::logResult)) {
                    break;
                }

                if (!retainBooks) {
                    if (!fromDir.delete()) {
                        logResult(Severity.SEVERE, String.format(getAppContext().getString(R.string.error_could_not_delete_book), fromDir.getPath()));
                    }
                }
                logResult(Severity.INFO,
                        String.format(getAppContext().getString(R.string.info_book_installed),
                                candidate.bookTitle, toDir.getPath()));

                newTree = toDir;
            } else if (fromDir.isDirectory()) {
                if (!retainBooks) {
                    progress.progress(ProgressKind.SEND_TOAST, fromDir.getName());
                    newTree = moveToSameFs(fromDir, candidate.newDirName, null);
                }

                if (newTree != null) {
                    candidate.isSelected = false;
                    candidates.remove(candidate);
                }
                else {
                    // Move failed (or we're just copying), copy it.
                    if (!FileUtilities.atomicTreeCopy(fromDir, toDir,
                            (fn) -> progress.progress(ProgressKind.SEND_TOAST, fn),
                            this::logResult)) {
                        FileUtilities.deleteTree(toDir, this::logResult);
                        break;
                    }

                    candidate.isSelected = false;
                    if (!retainBooks) {
                        FileUtilities.deleteTree(fromDir, this::logResult);
                    }
                    logResult(Severity.INFO,
                        String.format(getAppContext().getString(R.string.info_book_installed),
                            candidate.bookTitle, toDir.getPath()));

                    newTree = toDir;
                }

                if (!FileUtilities.expandInnerZips(toDir,
                        (fn) -> progress.progress(ProgressKind.SEND_TOAST, fn),
                        this::logResult)) {
                    break;
                }
            } else {
                // Ordinary file.
                // In this case, "fromDir" is really an audio file, not a directory.
                // This must be an audio file, or it wouldn't be a candidate.
                if (!retainBooks) {
                    progress.progress(ProgressKind.SEND_TOAST, fromDir.getName());
                    newTree = moveToSameFs(fromDir, candidate.newDirName, candidate.audioFile);
                }

                if (newTree != null) {
                    candidate.isSelected = false;
                    candidates.remove(candidate);
                } else {
                    // Move failed (or we're just copying), copy it.
                    if (!FileUtilities.mkdirs(toDir, this::logResult)) {
                        break;
                    }
                    File toFile = new File(toDir, candidate.audioFile);

                    progress.progress(ProgressKind.SEND_TOAST, fromDir.getName());
                    try (InputStream fs = new FileInputStream(fromDir);
                         OutputStream ts = new FileOutputStream(toFile)) {
                        IOUtils.copy(fs, ts);
                    } catch (IOException e) {
                        logResult(Severity.SEVERE, String.format(getAppContext().getString(R.string.error_could_not_copy_book_with_exception), fromDir.getPath(), e.getLocalizedMessage()));
                        FileUtilities.deleteTree(toDir, this::logResult);
                        break;
                    }
                    candidate.isSelected = false;
                    if (!retainBooks) {
                        FileUtilities.deleteTree(fromDir, this::logResult);
                    }
                    logResult(Severity.INFO,
                        String.format(getAppContext().getString(R.string.info_book_installed),
                            candidate.bookTitle, toDir.getPath()));
                    newTree = toDir;
                }
            }

            if (renameFiles) {
                FileUtilities.treeNameFix(newTree, this::logResult);
            }

            if (retainBooks) {
                candidate.collides = true;
            }
            progress.progress(ProgressKind.BOOK_DONE, fromDir.getName());
        }
    }

    @WorkerThread
    private File moveToSameFs(@NonNull File fromDir, @NonNull String toDirName, @Nullable String toName) {
        // Try to move it - I didn't find an a-priori way to check that it would succeed.
        // Return the File of the final result (or null)
        try {
            File toFile = null;
            for (File f: audioBooksDirs) {
                if (FilesystemUtil.sameFilesystemAs(f,fromDir) && f.canWrite()) {
                    toFile = new File(f, toDirName);
                    break;
                }
            }
            if (toFile == null) {
                return null;
            }
            if (toName != null) {
                if (!FileUtilities.mkdirs(toFile, this::logResult)) {
                    return null;
                }
                toFile = new File(toFile, toName);
            }
            if (!FileUtilities.renameTo(fromDir, toFile, this::logResult)) {
                return null;
            }
            return toFile;
        } catch (SecurityException e) {
            return null;
        }
    }

    @WorkerThread
    void deleteAllSelected_Task(Progress progress, boolean archiveBooks) {
        clearErrors();

        DeleteLoop:
        for (Provisioning.BookInfo book : bookList) {
            if (book.selected && !book.unWritable) {
                File bookDir = book.book.getPath();

                if (archiveBooks) {
                    // Get the path every time in case there are several AudioBook directories:
                    // We'll always leave them on the same physical filesystem.
                    File toDir = new File(Objects.requireNonNull(bookDir.getParentFile()).getParentFile(),
                            audioBooksDirectoryName + ".old");
                    if (toDir.exists()) {
                        if (!toDir.canWrite()) {
                            logResult(Severity.SEVERE, String.format(getAppContext().getString(
                                    R.string.error_archive_target_not_writable),
                                    toDir.getPath(), book.book.getDisplayTitle()));
                            //noinspection UnnecessaryLabelOnContinueStatement
                            continue DeleteLoop;
                        }
                    }
                    else if (!FileUtilities.mkdirs(toDir, this::logResult)) {
                        //noinspection UnnecessaryLabelOnContinueStatement
                        continue DeleteLoop;
                    }
                    File toBook = new File(toDir, book.book.getDirectoryName());
                    if (!FileUtilities.renameTo(bookDir, toBook, this::logResult)) {
                        //noinspection UnnecessaryLabelOnContinueStatement
                        continue DeleteLoop;
                    }
                }
                else {
                    if (!FileUtilities.deleteTree(bookDir, this::logResult)) {
                        //noinspection UnnecessaryLabelOnContinueStatement
                        continue DeleteLoop;
                    }
                }
                book.selected = false;
                progress.progress(ProgressKind.BOOK_DONE, null);
                logResult(Severity.INFO,
                        String.format(getAppContext().getString(R.string.info_book_removed),
                            book.book.getDisplayTitle(),
                            bookDir.getPath()));
            }
        }

        // This has the side-effect of a mediaStoreUpdate, which re-syncs with the UI.
        // (RemoteAuto does the same, slightly differently.)
        progress.progress(ProgressKind.ALL_DONE, null);
    }

    void groupAllSelected_execute(File newDir) {
        CrashWrapper.log("PV: Group books selected");
        clearErrors();

        for (Provisioning.Candidate c: candidates) {
            if (c.isSelected) {
                File bookPath = new File(c.oldDirPath);

                String renamedTo = c.newDirName;
                if (!bookPath.isDirectory()) {
                    // This is a zip or audio file, retain the extension
                    // (We couldn't get here if it wasn't one of those because it's not a candidate)

                    String bookDirName = bookPath.getName();
                    String oldExtension;
                    int dotLoc = bookDirName.lastIndexOf('.');
                    oldExtension = bookDirName.substring(dotLoc);
                    renamedTo += oldExtension;

                    // de-blank in case it gets ungrouped - the result would be messy otherwise
                    renamedTo = AudioBook.deBlank(renamedTo);
                }

                File toBook = new File(newDir, renamedTo);
                if (!bookPath.renameTo(toBook)) {
                    logResult(Severity.SEVERE, String.format(getAppContext().getString(
                        R.string.cannot_rename_group),
                        bookPath.getPath(), toBook.getPath()));
                }
            }
        }
    }

    void unGroupSelected_execute() {
        CrashWrapper.log("PV: Ungroup books selected");
        clearErrors();

        for (Provisioning.Candidate c: candidates) {
            if (c.isSelected) {
                File ungroupDir = new File(c.oldDirPath);
                File parent = ungroupDir.getParentFile();
                assert parent != null;
                if (ungroupDir.list() != null) {
                    for (String fn : Objects.requireNonNull(ungroupDir.list())) {
                        File oldLoc = new File(ungroupDir, fn);
                        if (oldLoc.isDirectory()) {
                            logResult(Severity.MILD,
                                String.format(getAppContext().getString(R.string.group_file_not_directory),
                                    parent.getPath()));
                        }

                        File newLoc = FilesystemUtil.createUniqueFilename(parent, fn);
                        if (newLoc == null) {
                            logResult(Severity.SEVERE, String.format(getAppContext().getString(
                                    R.string.group_could_not_name_new),
                                    oldLoc.getPath()));
                        }
                        else if (!oldLoc.renameTo(newLoc)) {
                            logResult(Severity.SEVERE, String.format(getAppContext().getString(
                                R.string.cannot_rename_group),
                                oldLoc.getPath(), newLoc.getPath()));
                        }
                    }
                    // Unless we couldn't empty the directory, this wouldn't fail. If we couldn't
                    // empty it, we know that from failures above. Nothing useful to do here.
                    //noinspection ResultOfMethodCallIgnored
                    ungroupDir.delete();
                }
                else {
                    logResult(Severity.SEVERE,
                            String.format(getAppContext().getString(R.string.could_not_ungroup_file),
                                c.newDirName));
                }
                break;
            }
        }
    }

    public void computeBookDuration(AudioBook book) {
        // Do it if we can, but there will be other calls if it isn't ready
        if (playbackService == null) {
            return;
        }
        playbackService.computeDuration(book);
    }

    public void assurePlaybackService() {
        if (playbackService != null) {
            return;
        }
        pendingPlayback.prepare();
        Intent serviceIntent = new Intent(getAppContext(), PlaybackService.class);
        getAppContext().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);
        pendingPlayback.await();
    }

    public void releasePlaybackService() {
        if (playbackService == null) {
            return;
        }
        getAppContext().unbindService(this);
        playbackService = null;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        CrashWrapper.log(TAG, "onServiceConnected");
        Preconditions.checkState(playbackService == null);
        playbackService = ((PlaybackService.ServiceBinder) service).getService();
        pendingPlayback.resume();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        CrashWrapper.log(TAG, "onServiceDisconnected");
        playbackService = null;
    }

    // Listen for external changes...
    // ...remember a book changed event that occurred while we weren't looking.
    private boolean booksChanged;
    private BooksChangedListener listener;

    void setListener(BooksChangedListener listener) {
        this.listener = listener;
        if (listener != null && booksChanged) {
            // Remember if books changed while we were inactive and make the callback if so
            booksChanged = false;
            listener.booksChanged();
        }
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    @Subscribe
    public void onEvent(@NonNull AudioBooksChangedEvent event) {
        if (event.contentType == null) {
            // nothing interesting happened
            return;
        }
        booksEvent();
    }

    void booksEvent() {
        if (listener != null) {
            listener.booksChanged();
        }
        else {
            booksChanged = true;
        }
    }

    interface BooksChangedListener {
        void booksChanged();
    }

    public enum ProgressKind{SEND_TOAST, FILESYSTEMS_FULL, BOOK_DONE, ALL_DONE}

    public interface Progress {
        void progress(ProgressKind kind, String string);
    }
}
