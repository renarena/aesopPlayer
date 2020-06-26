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
package com.donnKey.aesopPlayer.filescanner;

import android.content.Context;
import android.os.Environment;

import com.donnKey.aesopPlayer.ApplicationScope;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.concurrency.BackgroundExecutor;
import com.donnKey.aesopPlayer.concurrency.SimpleFuture;
import com.donnKey.aesopPlayer.util.MediaScannerUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import javax.inject.Inject;
import javax.inject.Named;

@ApplicationScope
public class FileScanner {
    public static final String SAMPLE_BOOK_FILE_NAME = ".sample";
    public static final String REFERENCE_BOOK_FILE_NAME = ".reference";

    private final String audioBooksDirectoryName;
    private final BackgroundExecutor ioExecutor;
    private final Context applicationContext;

    @Inject
    public FileScanner(
            @Named("AUDIOBOOKS_DIRECTORY") String audioBooksDirectoryName,
            @Named("IO_EXECUTOR") BackgroundExecutor ioExecutor,
            Context applicationContext) {
        this.audioBooksDirectoryName = audioBooksDirectoryName;
        this.ioExecutor = ioExecutor;
        this.applicationContext = applicationContext;
    }

    public SimpleFuture<List<FileSet>> scanAudioBooksDirectories() {
        ensureDefaultAudioBooksDirectory();
        ScanFilesTask task = new ScanFilesTask(applicationContext);
        return ioExecutor.postTask(task);
    }

    /**
     * Provide the default directory for audio books.
     *
     * The directory is in the devices external storage. Other than that there is nothing
     * special about it (e.g. it may be on an removable storage).
     */
    public File getDefaultAudioBooksDirectory() {
        File externalStorage = Environment.getExternalStorageDirectory();
        return new File(externalStorage, audioBooksDirectoryName);
    }

    @SuppressWarnings("SameReturnValue")
    private void ensureDefaultAudioBooksDirectory() {
        final File defaultAudiobooksPath = getDefaultAudioBooksDirectory();
        ioExecutor.postTask((Callable<Void>) () -> {
            ensureAudioBooksDirectory(applicationContext, defaultAudiobooksPath);
            return null;
        });
    }

    private static void ensureAudioBooksDirectory(Context applicationContext, File path) {
        if (!path.exists()) {
            if (Objects.requireNonNull(path.getParentFile()).canWrite()) {
                if (path.mkdirs()) {
                    // The MediaScanner doesn't work so well with directories (registers them as regular
                    // files) so make it scan a dummy.
                    final File dummyFile = new File(path, ".ignore");
                    try {
                        if (dummyFile.createNewFile()) {
                            MediaScannerUtil.scanAndDeleteFile(applicationContext, dummyFile);
                        }
                    } catch (IOException e) {
                        CrashWrapper.recordException(e);
                    }
                }
            } else {
                // This should not happen because permissions are granted by this point.
                // But it does, at least on some unofficial CyanogenMod systems.
                CrashWrapper.recordException(new Exception(
                        "Unable to write to: " + path.getParentFile().getAbsolutePath()));
            }
        }
    }
}
