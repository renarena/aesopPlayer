/**
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
package com.donnKey.aesopPlayer.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.system.StructStat;

import com.donnKey.aesopPlayer.AudioBookManagerModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FilesystemUtil {

    private static final String[] SUPPORTED_SUFFIXES = {".mp3", ".m4a", ".ogg"};

    private static List<File> listRootDirs(Context context) {
        List<File> rootDirs = listStorageMounts();
        for (File rootDir : listSemiPermanentRootDirs(context)) {
            if (!rootDirs.contains(rootDir))
                rootDirs.add(rootDir);
        }

        return rootDirs;
    }

    private static File getFSRootForPath(File path) {
        while (path != null && path.isDirectory()) {
            File parent = path.getParentFile();
            if (parent == null || !sameFilesystemAs(path, parent))
                return path;
            path = parent;
        }
        return path;
    }

    // Returns all system mount points that start with /storage.
    // This is likely to list attached SD cards, including those that are hidden from
    // Context.getExternalFilesDir()..
    // Some of the returned files may not be accessible due to permissions.
    private static List<File> listStorageMounts() {
        List<File> mounts = new ArrayList<>();
        File mountsFile = new File("/proc/mounts");
        try {
            BufferedReader reader = new BufferedReader(new FileReader(mountsFile));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(" +");
                if (fields.length >= 3 && !fields[2].equals("tmpfs") && fields[1].startsWith("/storage")) {
                    mounts.add(new File(fields[1]));
                }
            }
        } catch (IOException e) {
            // Ignore, just return as much as is accumulated in mounts.
        }
        return mounts;
    }

    // Returns a list of file system roots on all semi-permanent storage mounts.
    // Semi-permanent storage is removable medium that is part of the device (e.g. an SD slot
    // inside the battery compartment) and therefore unlikely to be removed often.
    // Storage medium that is easily accessible by the user (e.g. an external SD card slot) is
    // treated as portable storage.
    // The Context.getExternalFilesDir() method only lists semi-permanent storage devices.
    //
    // See http://source.android.com/devices/storage/traditional.html#multiple-external-storage-devices
    private static List<File> listSemiPermanentRootDirs(Context context) {
        File[] filesDirs;
        if (Build.VERSION.SDK_INT < 19)
            filesDirs = new File[]{ context.getExternalFilesDir(null) };
        else
            filesDirs = API19.getExternalFilesDirs(context);

        List<File> rootDirs = new ArrayList<>(filesDirs.length);
        for (File path : filesDirs) {
            File root = getFSRootForPath(path);
            if (root != null)
                rootDirs.add(root);
        }
        return rootDirs;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean sameFilesystemAs(File file1, File file2) {
        if (Build.VERSION.SDK_INT < 21) {
            // This will work 99.9% of the time, but...
            return file1.getTotalSpace() == file2.getTotalSpace()  &&
                    file1.getUsableSpace() == file2.getUsableSpace();
        }
        else {
            // This is right.
            long fsDev1 = API21.stat(file1.getAbsolutePath());
            long fsDev2 = API21.stat(file2.getAbsolutePath());
            return fsDev1 == fsDev2 && fsDev1 != -1;
        }
    }

    public static List<File> fileSystemRoots(Context applicationContext) {
        List<File> dirsToScan = FilesystemUtil.listRootDirs(applicationContext);
        File defaultStorage = Environment.getExternalStorageDirectory();

        // If defaultStorage's file system is already in the list, be sure to replace it
        // with the actual defaultStorage. (We can't use names - the root directory we
        // currently have may not be the right path to where AudioBooks is/should be.)
        // (Unchecked assumption: the entries are each on a distinct filesystem. The most
        // likely duplicates are of root, which WILL get deleted.)
        //
        // The provisioning stuff assumes that defaultStorage will be first
        List<File> result = new ArrayList<>();
        result.add(defaultStorage);
        for (File item : dirsToScan) {
            if (!sameFilesystemAs(item, defaultStorage)) {
                result.add(item);
            }
        }

        return result;
    }

    public static List<File> audioBooksDirs(Context context) {
        List<File> dirsToScan = FilesystemUtil.fileSystemRoots(context);
        List<File> result = new ArrayList<>();

        for (File item : dirsToScan) {
            File possibleBooks = new File(item, AudioBookManagerModule.audioBooksDirectoryName);
            if (possibleBooks.exists() && possibleBooks.isDirectory() && possibleBooks.canRead()) {
                result.add(possibleBooks);
            }
            possibleBooks = new File(item,"Android");
            possibleBooks = new File(possibleBooks,"media");
            possibleBooks = new File(possibleBooks, context.getPackageName());
            possibleBooks = new File(possibleBooks, AudioBookManagerModule.audioBooksDirectoryName);

            if (possibleBooks.exists() && possibleBooks.isDirectory() && possibleBooks.canRead()) {
                result.add(possibleBooks);
            }
        }
        return result;
    }

    public static boolean isAudioPath(String filename) {
        String lowerCaseFileName = filename.toLowerCase();
        for (String suffix : SUPPORTED_SUFFIXES)
            if (lowerCaseFileName.endsWith(suffix))
                return true;

        return false;
    }

    public static boolean isAudioFile(File file) {
        return isAudioPath(file.getName());
    }

    @TargetApi(19)
    private static class API19 {
        static File[] getExternalFilesDirs(Context context) {
            return context.getExternalFilesDirs(null);
        }
    }

    @TargetApi(21)
    private static class API21 {
        static long stat(String s) {
            try {
                StructStat stat = android.system.Os.stat(s);
                return stat.st_dev;
            } catch (android.system.ErrnoException e) {
                return -1;
            }
        }
    }
}
