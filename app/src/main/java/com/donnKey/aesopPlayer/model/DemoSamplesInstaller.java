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

import android.content.Context;
import android.media.MediaScannerConnection;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;

import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.google.common.io.Files;
import com.donnKey.aesopPlayer.filescanner.FileScanner;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;

import javax.inject.Inject;

import static com.donnKey.aesopPlayer.ui.provisioning.FileUtilities.*;

@SuppressWarnings("UnstableApiUsage")
public class DemoSamplesInstaller {

    private final File audioBooksDirectory;
    private final Locale locale;
    private final Context context;

    @Inject
    @MainThread
    public DemoSamplesInstaller(Context context, Locale locale, AudioBookManager audioBookManager) {
        this.context = context;
        this.audioBooksDirectory = audioBookManager.getDefaultAudioBooksDirectory();
        this.locale = locale;
    }

    @SuppressWarnings("UnusedReturnValue") // future use?
    @WorkerThread
    public boolean installBooksFromZip(File zipPath) {
        File tempFolder = Files.createTempDir();
        unzipAll(zipPath,tempFolder,
            (s)->{}, // don't Toast these filenames
            (severity, text) -> CrashWrapper.log("DemoSamplesInstaller: unzip: " + text));
        boolean anythingInstalled = installBooks(tempFolder);
        deleteTree(tempFolder, (severity,text)->CrashWrapper.log("DemoSamplesInstaller: Unable to clean up"));

        return anythingInstalled;
    }

    @SuppressWarnings("UnusedReturnValue") // future use?
    @WorkerThread
    private boolean installBooks(File sourceDirectory) {
        if (!audioBooksDirectory.exists()) {
            if (!audioBooksDirectory.mkdirs())
                return false;
        }

        boolean anythingInstalled = false;
        File[] books = sourceDirectory.listFiles();
        for (File bookDirectory : books) {
            boolean success = installSingleBook(bookDirectory, audioBooksDirectory);
            if (success)
                anythingInstalled = true;
        }

        return anythingInstalled;
    }

    @WorkerThread
    private boolean installSingleBook(File sourceBookDirectory, File audioBooksDirectory) {
        File titlesFile = new File(sourceBookDirectory, TITLES_FILE_NAME);
        String localizedTitle = readLocalizedTitle(titlesFile, locale);

        if (localizedTitle == null)
            return false; // Malformed package.

        File bookDirectory = new File(audioBooksDirectory, localizedTitle);

        if (bookDirectory.exists())
            return false;

        if (!bookDirectory.mkdirs())
            return false;

        try {
            File sampleIndicator = new File(bookDirectory, FileScanner.SAMPLE_BOOK_FILE_NAME);
            Files.touch(sampleIndicator);

            File[] files = sourceBookDirectory.listFiles((dir, filename) -> !TITLES_FILE_NAME.equals(filename));
            int count = files.length;
            String[] installedFilePaths = new String[count];

            for (int i = 0; i < count; ++i) {
                File srcFile = files[i];
                File dstFile = new File(bookDirectory, srcFile.getName());
                Files.copy(srcFile, dstFile);
                installedFilePaths[i] = dstFile.getAbsolutePath();
            }

            MediaScannerConnection.scanFile(context, installedFilePaths, null, null);

            return true;
        } catch(IOException exception) {
            CrashWrapper.logException(exception);
            deleteTree(bookDirectory,(a,b)->CrashWrapper.log("DemoSamplesInstaller: Unable to clean " + audioBooksDirectory + " after failure."));
            return false;
        }
    }

    @WorkerThread
    private String readLocalizedTitle(File file, Locale locale) {
        try {
            //noinspection deprecation - toString comes from Files in Guava, which isn't deprecated (yet)
            String titlesString = Files.toString(file, Charset.forName(TITLES_FILE_CHARSET));
            JSONObject titles = (JSONObject) new JSONTokener(titlesString).nextValue();
            String languageCode = locale.getLanguage();
            String localizedTitle = titles.optString(languageCode, null);
            if (localizedTitle == null)
                localizedTitle = titles.getString(DEFAULT_TITLE_FIELD);

            return localizedTitle;
        } catch(IOException | JSONException | ClassCastException exception) {
            CrashWrapper.logException(exception);
            return null;
        }
    }

    private static final String TITLES_FILE_NAME = "titles.json";
    private static final String TITLES_FILE_CHARSET = "UTF-8";
    private static final String DEFAULT_TITLE_FIELD = "default";
}
