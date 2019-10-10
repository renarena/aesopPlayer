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
package com.donnKey.aesopPlayer.model;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.donnKey.aesopPlayer.ApplicationScope;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.concurrency.SimpleFuture;
import com.donnKey.aesopPlayer.events.AudioBooksChangedEvent;
import com.donnKey.aesopPlayer.events.CurrentBookChangedEvent;
import com.donnKey.aesopPlayer.events.MediaStoreUpdateEvent;
import com.donnKey.aesopPlayer.filescanner.FileScanner;
import com.donnKey.aesopPlayer.filescanner.FileSet;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

@ApplicationScope
public class AudioBookManager {

    private final List<AudioBook> audioBooks = new ArrayList<>();
    private final FileScanner fileScanner;
    private final Storage storage;
    private AudioBook currentBook;
    private boolean isInitialized = false;
    private int isFirstScan = 2;

    @Inject
    @MainThread
    public AudioBookManager(EventBus eventBus, FileScanner fileScanner, Storage storage) {
        this.fileScanner = fileScanner;
        this.storage = storage;
        eventBus.register(this);
    }

    @SuppressWarnings("UnusedDeclaration")
    @MainThread
    public void onEvent(MediaStoreUpdateEvent ignored) {
        scanFiles();
    }

    @MainThread
    public List<AudioBook> getAudioBooks() {
        return audioBooks;
    }

    @MainThread
    public void setCurrentBook(String bookId) {
        AudioBook newBook = getById(bookId);
        if (newBook != currentBook) {
            currentBook = getById(bookId);
            EventBus.getDefault().post(new CurrentBookChangedEvent(currentBook));
        }
    }

    @MainThread
    public AudioBook getCurrentBook() {
        return currentBook;
    }

    public void removeBook(AudioBook book) {
        audioBooks.remove(book);
        if (book == currentBook) {
            currentBook = null;
        }
    }

    @MainThread
    public int getCurrentBookIndex() {
        return audioBooks.indexOf(currentBook);
    }

    @MainThread
    public AudioBook getById(String id) {
        for (AudioBook book : audioBooks)
            if (book.getId().equals(id))
                return book;
        return null;
    }

    @MainThread
    public File getDefaultAudioBooksDirectory() {
        return fileScanner.getDefaultAudioBooksDirectory();
    }

    @MainThread
    public boolean isInitialized() {
        return isInitialized;
    }

    @MainThread
    public void scanFiles() {
        SimpleFuture<List<FileSet>> future = fileScanner.scanAudioBooksDirectories();
        future.addListener(new SimpleFuture.Listener<List<FileSet>>() {
            @Override
            public void onResult(@NonNull List<FileSet> result) {
                isInitialized = true;
                processScanResult(result);
            }

            @Override
            public void onException(@NonNull Throwable t) {
                isInitialized = true;
                // TODO: clear the list of books?
                CrashWrapper.logException(t);
            }
        });
    }

    @MainThread
    private void processScanResult(@NonNull List<FileSet> fileSets) {
        if (isFirstScan > 1 && fileSets.isEmpty()) {
            // The first scan may fail if it is just after booting and the SD card is not yet
            // mounted. Retry in a while. If it's still empty, then it really is empty and
            // post that.
            Handler handler = new Handler(Looper.myLooper());
            handler.postDelayed(this::scanFiles, TimeUnit.SECONDS.toMillis(10));
            isFirstScan = 1;
            return;
        }

        // This isn't very efficient but there shouldn't be more than a dozen audio books on the
        // device.
        List<AudioBook> booksToRemove = new ArrayList<>();
        for (AudioBook audioBook : audioBooks) {
            String id = audioBook.getId();
            boolean isInFileSet = false;
            for (FileSet fileSet : fileSets) {
                if (id.equals(fileSet.id)) {
                    isInFileSet = true;
                    break;
                }
            }
            if (!isInFileSet) {
                booksToRemove.add(audioBook);
            }
        }
        if (booksToRemove.contains(currentBook)) {
            currentBook = null;
        }
        boolean audioBooksChanged = audioBooks.removeAll(booksToRemove);
        LibraryContentType contentType = LibraryContentType.EMPTY;

        for (FileSet fileSet : fileSets) {
            AudioBook book = getById(fileSet.id);
            if (book == null) {
                AudioBook audioBook = new AudioBook(fileSet);
                storage.readAudioBookState(audioBook);
                audioBook.setUpdateObserver(storage);
                audioBooks.add(audioBook);
                audioBooksChanged = true;
            }
            else {
                if (!book.getDirectoryName().equals(fileSet.directoryName)) {
                    // A rename outside of Aesop
                    book.replaceFileSet(fileSet);
                    audioBooksChanged = true;
                }
            }
            LibraryContentType newContentType = fileSet.isDemoSample
                    ? LibraryContentType.SAMPLES_ONLY : LibraryContentType.USER_CONTENT;
            if (newContentType.supersedes(contentType)) {
                contentType = newContentType;
            }
        }

        if (audioBooks.size() > 0) {
            Collections.sort(audioBooks, (lhs, rhs) -> lhs.getTitle().compareToIgnoreCase(rhs.getTitle()));

            assignColoursToNewBooks();
        }

        storage.cleanOldEntries(this);

        if (currentBook == null) {
            String id = storage.getCurrentAudioBook();
            if (getById(id) == null && audioBooks.size() > 0)
                id = audioBooks.get(0).getId();

            if (id != null)
                setCurrentBook(id);
        }

        if (audioBooksChanged || isFirstScan > 0) {
            EventBus.getDefault().post(new AudioBooksChangedEvent(contentType));
        }

        isFirstScan = 0;
    }

    @MainThread
    private void assignColoursToNewBooks() {
        final int MAX_NEIGHBOUR_DISTANCE = 2;

        int count = audioBooks.size();
        int lastIndex = count - 1;
        for (int i = 0; i < count; ++i) {
            AudioBook book = audioBooks.get(i);
            if (book.getColourScheme() == null) {
                int startNeighbourIndex = i - MAX_NEIGHBOUR_DISTANCE;
                int endNeighbourIndex = i + MAX_NEIGHBOUR_DISTANCE;
                List<ColourScheme> coloursToAvoid = getColoursInRange(
                        Math.max(0, startNeighbourIndex),
                        Math.min(lastIndex, endNeighbourIndex));
                book.setColourScheme(ColourScheme.getRandom(coloursToAvoid));
                storage.writeAudioBookState(book);
            }
        }
    }

    @MainThread
    private List<ColourScheme> getColoursInRange(int startIndex, int endIndex) {
        List<ColourScheme> colours = new ArrayList<>();
        for (int i = startIndex; i <= endIndex; ++i) {
            ColourScheme colourScheme = audioBooks.get(i).getColourScheme();
            if (colourScheme != null)
                colours.add(colourScheme);
        }
        return colours;
    }
}
