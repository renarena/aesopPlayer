package com.studio4plus.homerplayer.ui.provisioning;

import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.HomerPlayerApplication;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.events.AudioBooksChangedEvent;
import com.studio4plus.homerplayer.model.AudioBook;
import com.studio4plus.homerplayer.model.AudioBookManager;
import com.studio4plus.homerplayer.service.PlaybackService;
import com.studio4plus.homerplayer.ui.UiControllerMain;
import com.studio4plus.homerplayer.util.FilesystemUtil;

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

import javax.inject.Inject;
import javax.inject.Named;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.ViewModel;
import de.greenrobot.event.EventBus;

import static com.studio4plus.homerplayer.HomerPlayerApplication.getAppContext;

// Serves as a cache for inter-fragment communication
public class Provisioning extends ViewModel {
    @Inject @Named("AUDIOBOOKS_DIRECTORY") public String audioBooksDirectoryName;
    @SuppressWarnings("WeakerAccess")
    @Inject public AudioBookManager audioBookManager;
    @SuppressWarnings("WeakerAccess")
    @Inject public GlobalSettings globalSettings;

    // Types used in this cache
    enum FileKind {ZIP_FILE, AUDIO_FILE, DIRECTORY, NONE}
    enum Severity {INFO, MILD, SEVERE}

    static class Candidate {
        FileKind kind;
        String newDirName;
        String oldDirPath;
        String audioPath;
        String audioFile;
        String metadataTitle;
        String metadataAuthor;
        String bookTitle;
        boolean isSelected;
        boolean collides; // ... with existing directory name (not book name)

        Candidate() {
            this.kind = FileKind.NONE;
            this.newDirName = null;
            this.oldDirPath = null;
            this.audioFile = null;
            this.audioPath = null;

            this.metadataTitle = null;
            this.metadataAuthor = null;
            this.bookTitle = null;
            this.isSelected = false;
            this.collides = false;
        }

        void fill (FileKind kind, String dirName, String dirPath, String audioFile, String audioPath) {
            this.newDirName = dirName;
            this.oldDirPath = dirPath;
            this.audioFile = audioFile;
            this.audioPath = audioPath;
            this.kind = kind;
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

    // ... current directory containing new books and the books
    File candidateDirectory;
    long candidatesTimestamp;
    final List<Candidate> candidates = new ArrayList<>();

    // ... associated with existing books
    BookInfo bookList[];
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
        HomerPlayerApplication.getComponent(getAppContext()).inject(this);
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

            bookList[i] = new BookInfo(book,
                    book.getCompleted(),!book.getPath().canWrite());
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
        List<String> files = Arrays.asList(candidateDirectory.list());
        Collections.sort(files, String::compareToIgnoreCase);

        Candidate candidate = null;
        for (String fileName : files) {
            if (candidate == null) {
                candidate = new Candidate();
                synchronized (candidates) {
                    candidates.add(candidate);
                }
                notifier.notifier();
            }
            else {
                candidate.newDirName = fileName;
                notifier.notifier();
            }

            File file = new File(candidateDirectory, fileName);
            String pathToTarget = file.getPath();

            boolean newItem = false;
            if (fileName.toLowerCase().endsWith(".zip")) {
                // This is an expensive operation
                String audioFile = FileUtilities.getZipAudioFile(file);
                if (audioFile != null) {
                    candidate.fill(
                            FileKind.ZIP_FILE,
                            AudioBook.filenameCleanup(fileName),
                            pathToTarget,
                            audioFile, null);
                    newItem = true;
                }
            }
            else if (FilesystemUtil.isAudioPath(fileName)) {
                candidate.fill(
                        FileKind.AUDIO_FILE,
                        AudioBook.filenameCleanup(fileName),
                        pathToTarget,
                        fileName,
                        pathToTarget);
                newItem = true;
            }
            else if (file.isDirectory()) {
                String audioFile = FileUtilities.getAudioFile(file);
                if (audioFile != null) {
                    // No audio file -> not a book; ignore
                    candidate.fill(
                            FileKind.DIRECTORY,
                            fileName,
                            pathToTarget,
                            AudioBook.basename(audioFile),
                            audioFile);
                    newItem = true;
                }
            }

            if (newItem) {
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
            if (candidate.kind == FileKind.NONE) {
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
    private void computeBookTitle(Candidate candidate) {
        if (candidate.newDirName.contains(" ")) {
            candidate.bookTitle = AudioBook.filenameCleanup(candidate.newDirName);
            return;
        }

        if (candidate.audioFile != null) {
            AudioBook.TitleAndAuthor title;
            if (candidate.audioPath == null) {
                Preconditions.checkState(candidate.kind == FileKind.ZIP_FILE);
                int pos = candidate.audioFile.lastIndexOf(".");
                String extension = "";
                if (pos >= 0) {
                    // (Retain the dot in the extension.)
                    extension = candidate.audioFile.substring(pos);
                }
                File tmp = null;
                try {
                    tmp = File.createTempFile("AudioTmp", extension);
                    FileUtilities.unzipNamed(candidate.oldDirPath, candidate.audioFile, tmp);
                    title = AudioBook.metadataTitle(tmp);
                } catch (IOException e) {
                    title = new AudioBook.TitleAndAuthor(null,null);
                } finally {
                    if (tmp != null) {
                        //noinspection ResultOfMethodCallIgnored
                        tmp.delete();
                    }
                }
            }
            else {
                title = AudioBook.metadataTitle(candidate.audioPath);
            }

            candidate.metadataTitle = title.title;
            candidate.metadataAuthor = title.author;
            candidate.bookTitle = AudioBook.computeTitle(title);
        }

        if (candidate.bookTitle == null || candidate.bookTitle.length() <= 0){
            candidate.bookTitle = AudioBook.filenameCleanup(candidate.newDirName);
            candidate.bookTitle = AudioBook.titleCase(candidate.bookTitle);
        }
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
    void moveAllSelected_Task(Progress progress, boolean retainBooks) {
        clearErrors();

        // Find the (possibly several) directories.
        List<File> dirsToUse = audioBooksDirs;

        int nextDir = 0;
        File activeStorage = null;

        Provisioning.Candidate[] currentCandidates
                = candidates.toArray(new Provisioning.Candidate[0]);

        // Iterate through all the files. If we need space to copy to, advance the pointer to
        // the several AudioBooks directories when needed.
        CopyLoop:
        for (int candidateIndex = 0; candidateIndex < currentCandidates.length; /* void */ ) {
            Provisioning.Candidate candidate = currentCandidates[candidateIndex];
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
                    logResult(Severity.INFO,String.format(getAppContext().getString(R.string.no_such_directory), activeStorage.getPath()));
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

            if ((float)activeStorage.getUsableSpace() / (float)activeStorage.getTotalSpace() < 0.1f) {
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

            switch (candidate.kind) {
            case ZIP_FILE:
            {
                // Zip files always get unpacked into their target location, where (presumably)
                // there's enough space. Error and remove the partial unpack on failure.
                if (!FileUtilities.unzipAll(fromDir, toDir,
                        (fn)-> progress.progress(ProgressKind.SEND_TOAST, fn),
                        this::logResult)) {
                    FileUtilities.deleteTree(toDir, this::logResult);
                    break CopyLoop;
                }
                candidate.isSelected = false;

                if (!retainBooks) {
                    if (!fromDir.delete()) {
                        logResult(Severity.SEVERE,String.format(getAppContext().getString(R.string.error_could_not_delete_book), fromDir.getPath()));
                    }
                }
                logResult(Severity.INFO,String.format(getAppContext().getString(R.string.info_book_installed), fromDir.getPath()));
                break;
            }
            case DIRECTORY:
            {
                boolean successful = false;
                if (!retainBooks) {
                    progress.progress(ProgressKind.SEND_TOAST, fromDir.getName());
                    successful = moveToSameFs(fromDir, candidate.newDirName, null);
                }

                if (successful) {
                    candidate.isSelected = false;
                    candidates.remove(candidate);
                    break;
                }

                // Move failed (or we're just copying), copy it.
                if (!FileUtilities.atomicTreeCopy(fromDir, toDir,
                        (fn)-> progress.progress(ProgressKind.SEND_TOAST, fn),
                        this::logResult)) {
                    FileUtilities.deleteTree(toDir, this::logResult);
                    break CopyLoop;
                }
                candidate.isSelected = false;
                if (!retainBooks) {
                    FileUtilities.deleteTree(fromDir, this::logResult);
                }
                logResult(Severity.INFO,String.format(getAppContext().getString(R.string.info_book_installed), fromDir.getPath()));
                break;
            }
            case AUDIO_FILE:
            {
                // In this case, "fromDir" is really an audio file, not a directory.
                boolean successful = false;
                if (!retainBooks) {
                    progress.progress(ProgressKind.SEND_TOAST, fromDir.getName());
                    successful = moveToSameFs(fromDir, candidate.newDirName, candidate.audioFile);
                }

                if (successful) {
                    candidate.isSelected = false;
                    candidates.remove(candidate);
                    break;
                }

                // Move failed (or we're just copying), copy it.
                if (!FileUtilities.mkdirs(toDir, this::logResult)) {
                    break CopyLoop;
                }
                File toFile = new File(toDir,candidate.audioFile);

                progress.progress(ProgressKind.SEND_TOAST, fromDir.getName());
                try (InputStream fs = new FileInputStream(fromDir);
                     OutputStream ts = new FileOutputStream(toFile)) {
                    IOUtils.copy(fs, ts);
                }
                catch (IOException e) {
                    logResult(Severity.SEVERE, String.format(getAppContext().getString(R.string.error_could_not_copy_book_with_exception), fromDir.getPath(), e.getLocalizedMessage()));
                    FileUtilities.deleteTree(toDir, this::logResult);
                    break CopyLoop;
                }
                candidate.isSelected = false;
                if (!retainBooks) {
                    FileUtilities.deleteTree(fromDir, this::logResult);
                }
                logResult(Severity.INFO,String.format(getAppContext().getString(R.string.info_book_installed), fromDir.getPath()));
                break;
            }
            }
            if (retainBooks) {
                candidate.collides = true;
            }
            progress.progress(ProgressKind.BOOK_DONE, fromDir.getName());
        }

        progress.progress(ProgressKind.ALL_DONE, null);
    }

    @WorkerThread
    private boolean moveToSameFs(@NonNull File fromDir, @NonNull String toDirName, @Nullable String toName) {
        // Try to move it - I didn't find an a-priori way to check that it would succeed.
        try {
            File toFile = null;
            for (File f: audioBooksDirs) {
                if (FilesystemUtil.sameFilesystemAs(f,fromDir) && f.canWrite()) {
                    toFile = new File(f, toDirName);
                    break;
                }
            }
            if (toFile == null) {
                return false;
            }
            if (toName != null) {
                if (!FileUtilities.mkdirs(toFile, this::logResult)) {
                    return false;
                }
                toFile = new File(toFile, toName);
            }
            return FileUtilities.renameTo(fromDir, toFile, this::logResult);
        } catch (SecurityException e) {
            return false;
        }
    }

    @WorkerThread
    void deleteAllSelected_Task(Progress progress) {
        clearErrors();
        List<AudioBook> audioBooks = audioBookManager.getAudioBooks();
        boolean archiveBooks = globalSettings.getArchiveBooks();

        DeleteLoop:
        for (Provisioning.BookInfo book : bookList) {
            if (book.selected && !book.unWritable) {
                File bookDir = book.book.getPath();

                if (archiveBooks) {
                    // Get the path every time in case there are several AudioBook directories:
                    // We'll always leave them on the same physical filesystem.
                    File toDir = new File(bookDir.getParentFile().getParentFile(),
                            audioBooksDirectoryName + ".old");
                    if (toDir.exists()) {
                        if (!toDir.canWrite()) {
                            logResult(Severity.SEVERE, String.format(getAppContext().getString(
                                    R.string.error_archive_target_not_writable),
                                    toDir.getPath(), book.book.getTitle()));
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
                audioBooks.remove(book.book);
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