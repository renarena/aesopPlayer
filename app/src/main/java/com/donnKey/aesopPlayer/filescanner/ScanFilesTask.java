package com.donnKey.aesopPlayer.filescanner;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Base64;

import com.donnKey.aesopPlayer.util.DirectoryFilter;
import com.donnKey.aesopPlayer.util.FilesystemUtil;
import com.donnKey.aesopPlayer.util.OrFilter;

import java.io.File;
import java.io.FileFilter;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

public class ScanFilesTask implements Callable<List<FileSet>> {

    private final @NonNull Context applicationContext;

    ScanFilesTask(@NonNull Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public List<FileSet> call() {
        return scanAudioBooksDirectories();
    }

    private List<FileSet> scanAudioBooksDirectories() {
        List<FileSet> fileSets = new ArrayList<>();

        List<File> dirsToScan = FilesystemUtil.audioBooksDirs(applicationContext);

        for (File booksDir : dirsToScan) {
            scanAndAppendBooks(booksDir, fileSets);
        }
        return fileSets;
    }


    private void scanAndAppendBooks(File audioBooksDir, List<FileSet> fileSets) {
        File[] audioBookDirs = audioBooksDir.listFiles(new DirectoryFilter());
        if (audioBookDirs != null) {
            for (File directory : audioBookDirs) {
                FileSet fileSet = createFileSet(directory);
                if (fileSet != null && !fileSets.contains(fileSet))
                    fileSets.add(fileSet);
            }
        }
    }

    @Nullable
    static public FileSet createFileSet(File bookDirectory) {
        File[] allFiles = getAllAudioFiles(bookDirectory);
        int bookDirectoryPathLength = bookDirectory.getAbsolutePath().length();

        ByteBuffer bufferLong = ByteBuffer.allocate(Long.SIZE);
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            for (File file : allFiles) {
                String path = file.getAbsolutePath();
                String relativePath = path.substring(bookDirectoryPathLength);

                // TODO: what if the same book is in two directories?
                bufferLong.putLong(0, file.length());
                digest.update(relativePath.getBytes());
                digest.update(bufferLong);
            }
            String id = Base64.encodeToString(digest.digest(), Base64.NO_PADDING | Base64.NO_WRAP);
            if (allFiles.length > 0) {
                File sampleIndicator = new File(bookDirectory, FileScanner.SAMPLE_BOOK_FILE_NAME);
                boolean isDemoSample = sampleIndicator.exists();
                File referenceIndicator = new File(bookDirectory, FileScanner.REFERENCE_BOOK_FILE_NAME);
                boolean isReference = referenceIndicator.exists();
                return new FileSet(id, bookDirectory, allFiles, isDemoSample, isReference);
            } else {
                return null;
            }
        } catch (NoSuchAlgorithmException e) {
            // Never happens.
            e.printStackTrace();
            throw new RuntimeException("MD5 not available");
        }
    }


    @NonNull
    static private File[] getAllAudioFiles(File directory) {
        List<File> files = new ArrayList<>();
        FileFilter audioFiles = FilesystemUtil::isAudioFile;

        FileFilter filesAndDirectoriesFilter = new OrFilter(audioFiles, new DirectoryFilter());
        addFilesRecursive(directory, filesAndDirectoriesFilter, files);
        return files.toArray(new File[0]);
    }


    static private void addFilesRecursive(File directory, FileFilter filter, List<File> allFiles) {
        File[] files = directory.listFiles(filter);
        // listFiles may return null. Skip such directories.
        if (files == null)
            return;

        Arrays.sort(files, (lhs, rhs) -> lhs.getName().compareToIgnoreCase(rhs.getName()));

        for (File file : files) {
            if (file.isDirectory()) {
                addFilesRecursive(file, filter, allFiles);
            } else {
                allFiles.add(file);
            }
        }
    }
}
