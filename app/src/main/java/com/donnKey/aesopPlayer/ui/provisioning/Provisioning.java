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

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.events.AudioBooksChangedEvent;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.model.AudioBookManager;
import com.donnKey.aesopPlayer.service.PlaybackService;
import com.donnKey.aesopPlayer.ui.UiControllerMain;
import com.donnKey.aesopPlayer.util.FilesystemUtil;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.ViewModel;
import de.greenrobot.event.EventBus;

import static com.donnKey.aesopPlayer.AesopPlayerApplication.getAppContext;

// Serves as a cache for inter-fragment communication
public class Provisioning extends ViewModel {
    @Inject @Named("AUDIOBOOKS_DIRECTORY") public String audioBooksDirectoryName;
    @Inject public AudioBookManager audioBookManager;
    @Inject public GlobalSettings globalSettings;

    // Types used in this cache
    public enum Severity {INFO, MILD, SEVERE}

    static class Candidate {
        String newDirName;
        String oldDirPath;
        File audioPath;
        String audioFile;
        String metadataTitle;
        String metadataAuthor;
        String bookTitle;
        boolean isSelected;
        boolean collides; // ... with existing directory name (not book name)

        Candidate() {
            this.newDirName = null;
            this.oldDirPath = null;
            this.audioPath = null;

            this.audioFile = null;
            this.metadataTitle = null;
            this.metadataAuthor = null;
            this.bookTitle = null;
            this.isSelected = false;
            this.collides = false;
        }

        void fill (@NonNull String dirName, @NonNull String dirPath, @NonNull File audioPath) {
            this.newDirName = dirName;
            this.oldDirPath = dirPath;
            this.audioPath = audioPath;

            this.audioFile = audioPath.getName();
        }
    }

    static class BookInfo {
        final AudioBook book;
        boolean selected;
        final boolean unWritable;
        BookInfo(AudioBook book, boolean selected, boolean unWritable) {
            this.book = book;
            this.selected = selected;
            this.unWritable = unWritable;
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

    // ... support info
    private List<File>audioBooksDirs = null;

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

    private void clearErrors() {
        errorLogs.clear();
    }

    private void logResult(Provisioning.Severity severity, String text) {
        errorLogs.add(new Provisioning.ErrorInfo(severity, text));
    }

    // Class machinery
    public Provisioning() {
        AesopPlayerApplication.getComponent(getAppContext()).inject(this);
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        EventBus.getDefault().unregister(this);
    }

    // Data functions

    void buildBookList() {
        List<AudioBook> audioBooks = audioBookManager.getAudioBooks();

        bookList = new BookInfo[audioBooks.size()];

        totalTime = 0;
        partiallyUnknown = false;
        for (int i = 0; i< audioBooks.size(); i++) {
            AudioBook book = audioBooks.get(i);

            // A completed book that isn't in its first 10 seconds is likely being reread.
            // Don't suggest deletion.
            boolean preSelected = book.getCompleted()
                    && TimeUnit.MILLISECONDS.toSeconds(book.toMs(book.getLastPosition())) < 10;
            bookList[i] = new BookInfo(book, preSelected, !book.getPath().canWrite());
            long t = book.getTotalDurationMs();
            if (t != AudioBook.UNKNOWN_POSITION) {
                totalTime += book.getTotalDurationMs();
            }
            else {
                PlaybackService playbackService = UiControllerMain.getPlaybackService();
                if (playbackService != null) {
                    // Sooner or later this will happen, but now is really nice
                    playbackService.computeDuration(book);
                }
                partiallyUnknown = true;
            }
        }

        refreshCollisionState();
    }

    @WorkerThread
    void buildCandidateList_Task(NotifierCallback notifier) {
        // Must run inside a task; will be slow particularly for zips
        // We call notifier several times so that there's indication of progress to
        // the user while the long operations of digging into a zip file happen.
        String[] dirList = candidateDirectory.list();
        if (dirList == null) {
            return;
        }
        List<String> files = Arrays.asList(dirList);
        Collections.sort(files, String::compareToIgnoreCase);

        Candidate candidate = null;
        for (String fileName : files) {
            if (candidate == null) {
                candidate = new Candidate();
                synchronized (candidates) {
                    candidates.add(candidate);
                }
            }
            candidate.newDirName = AudioBook.filenameCleanup(fileName);
            notifier.notifier();

            File pathToTarget = new File(candidateDirectory, fileName);

            // This might return a file in the cache dir, which should later be deleted.
            // This can be expensive depending on the content
            File audioPath = FileUtilities.findFileMatching(pathToTarget, FilesystemUtil::isAudioPath);

            if (audioPath != null) {
                candidate.fill(
                        AudioBook.filenameCleanup(fileName),
                        pathToTarget.getPath(),
                        audioPath);

                if (scanForDuplicateAudioBook(candidate.newDirName)) {
                    candidate.collides = true;
                }
                notifier.notifier();

                final Candidate c = candidate;
                // Theoretically, we should throttle the number of threads, but since the number
                // of books is small and these terminate soon enough...
                Thread t = new Thread(()-> {
                    // This can be very expensive
                    computeBookTitle(c);
                    notifier.notifier();
                });
                t.setPriority(Thread.MIN_PRIORITY);
                t.start();
                candidate = null;
            }
        }

        if (candidates.size() > 0) {
            candidate = candidates.get(candidates.size() - 1);
            if (candidate.audioPath == null) {
                // If the last file was a non-book
                synchronized (candidates) {
                    candidates.remove(candidates.size() - 1);
                }
            }
        }

        // Just to be sure
        notifier.notifier();
        candidatesTimestamp = candidateDirectory.lastModified();
    }

    @WorkerThread
    private void computeBookTitle(@NonNull Candidate candidate) {
        // candidate.audioPath must be filled in, or this wouldn't be a candidate.
        AudioBook.TitleAndAuthor title = AudioBook.extractTitle(new File(candidate.oldDirPath), candidate.audioPath);

        candidate.metadataTitle = title.title;
        candidate.metadataAuthor = title.author;

        if (candidate.newDirName.contains(" ")) {
            candidate.bookTitle = AudioBook.filenameCleanup(candidate.newDirName);
        }
        else {
            candidate.bookTitle = AudioBook.computeTitle(title);
        }

        // If the audio file is in the cache dir, it's extracted from a zip, and should
        // be tossed now that we've processed it. Otherwise, it's a real file and should
        // be left alone.
        FileUtilities.removeIfTemp(candidate.audioPath);
    }

    @WorkerThread
    private void refreshCollisionState() {
        // Something changed in the books directories to get here... possibly a
        // new or removed collision.
        for (Candidate c: candidates) {
            if (c.newDirName != null) {
                c.collides = scanForDuplicateAudioBook(c.newDirName);
            }
        }
    }

    @WorkerThread
    boolean scanForDuplicateAudioBook(String dirname) {
        if (audioBooksDirs == null) {
            audioBooksDirs = FilesystemUtil.audioBooksDirs(getAppContext());
        }
        for (File currentAudioBooks : audioBooksDirs) {
            File possibleBook = new File(currentAudioBooks, dirname);
            if (possibleBook.exists()) {
                return true;
            }
        }
        return false;
    }

    @WorkerThread
    void moveAllSelected_Task(Progress progress, boolean retainBooks, boolean renameFiles) {
        clearErrors();

        moveAllSelected_pass1(progress, retainBooks, renameFiles);
        moveAllSelected_pass2(progress, retainBooks, renameFiles);

        progress.progress(ProgressKind.ALL_DONE, null);
    }

    @SuppressLint("UsableSpace")
    @WorkerThread
    private void moveAllSelected_pass1(Progress progress, boolean retainBooks, boolean renameFiles) {
        // Pass one: simple renames on the same file system (operations that don't change
        // the space used even temporarily). Thus avoiding interaction with the available space
        // checks.

        if (retainBooks) {
            // No-op, since it will take space
            return;
        }

        Provisioning.Candidate[] currentCandidates
                = candidates.toArray(new Provisioning.Candidate[0]);

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
    private void moveAllSelected_pass2(Progress progress, boolean retainBooks, boolean renameFiles) {
        // Pass 2: These operations use space (copying or expanding archives)
        // Note: "moveToSameFs" shouldn't normally be called from here (everything got done in pass one)
        // It's a fail-soft in the case of is a nearly full file system than fails a directory move
        // because of the space needed for a name change. (The error will get reported here if
        // the retry fails.)

        // Find the (possibly several) target directories.
        List<File> dirsToUse = audioBooksDirs;

        int nextDir = 0;
        File activeStorage = null;

        Provisioning.Candidate[] currentCandidates
                = candidates.toArray(new Provisioning.Candidate[0]);

        // Iterate through all the files. If we need space to copy to, advance the pointer to
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
                logResult(Severity.INFO, String.format(getAppContext().getString(R.string.info_book_installed), fromDir.getPath()));

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
                    logResult(Severity.INFO, String.format(getAppContext().getString(R.string.info_book_installed), fromDir.getPath()));

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
                    logResult(Severity.INFO, String.format(getAppContext().getString(R.string.info_book_installed), fromDir.getPath()));

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

        progress.progress(ProgressKind.ALL_DONE, null);
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
    void deleteAllSelected_Task(Progress progress) {
        clearErrors();
        boolean archiveBooks = globalSettings.getArchiveBooks();

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
                audioBookManager.removeBook(book.book);
                book.selected = false;
                progress.progress(ProgressKind.BOOK_DONE, null);
                logResult(Severity.INFO,
                        String.format(getAppContext().getString(R.string.info_book_removed),
                            bookDir.getPath()));
            }
        }

        progress.progress(ProgressKind.ALL_DONE, null);
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
    public void onEvent(AudioBooksChangedEvent event) {
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

    // For tasks that send notifications.
    interface NotifierCallback {
        void notifier();
    }

    enum ProgressKind{SEND_TOAST, FILESYSTEMS_FULL, BOOK_DONE, ALL_DONE}

    interface Progress {
        void progress(ProgressKind kind, String string);
    }
}
