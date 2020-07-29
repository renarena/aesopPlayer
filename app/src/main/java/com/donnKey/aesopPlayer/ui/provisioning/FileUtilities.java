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

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.util.FilesystemUtil;

import org.apache.commons.compress.utils.IOUtils;

import static com.donnKey.aesopPlayer.AesopPlayerApplication.getAppContext;
import static com.donnKey.aesopPlayer.ui.provisioning.Provisioning.Severity.SEVERE;

public class FileUtilities {

    public interface StringCallback {
        void Callback(String s);
    }

    public interface ErrorCallback {
        void Callback(Provisioning.Severity severity, String text);
    }

    static public final String UnzipTmpName = ".TmpDir";

    static boolean isZip(@NonNull String path) {
        return path.endsWith(".zip") || path.endsWith(".ZIP");
    }

    // Do a substitution in the body, but not extension, of a filename.
    // Used to convert n-1 to n digits (with 0)
    private static String fixDigits(@NonNull String name, String regex, String subst) {
        int dot = name.lastIndexOf('.');
        String extension = "";
        if (dot > 0) {
            extension = name.substring(dot+1);
            name = name.substring(0,dot);
        }

        // Do it twice just in case we get a1b2c (or 1.2), which doesn't fix the 2 in just one pass.
        name = name.replaceAll(regex, subst);
        name = name.replaceAll(regex, subst);

        if (dot > 0) {
            name = name + "." + extension;
        }

        return name;
    }

    // Look for zip files in targetDir and unzip them recursively, in place.
    // Return true if all inner zips (if any) expanded, false on error.
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean expandInnerZips(@NonNull File targetDir,
                                   StringCallback progress, ErrorCallback logError) {
        String[] filenames = targetDir.list();
        if (filenames == null) {
            return true;
        }

        for (String fn: filenames) {
            if (isZip(fn)) {
                File oldZip = new File(targetDir, fn);
                String newDirName = fn.substring(0,fn.length()-4);
                File newDir = new File(targetDir, newDirName);
                if (newDir.exists()) {
                    // Arguably we could fail soft here, but this likely indicates that
                    // we're adding to an existing mess.
                    logError.Callback(SEVERE, String.format(getAppContext()
                                    .getString(R.string.error_unzip_into_existing_directory),
                            newDir.getName()));
                    return false;
                }
                if (!unzipAll(oldZip, newDir, progress, logError)) {
                    return false;
                }
                deleteTree(oldZip, logError);
                // Just in case there's a nested zip (unlikely in the extreme).
                if (!expandInnerZips(newDir, progress, logError)) {
                    return false;
                }
                continue;
            }
            File file = new File(targetDir, fn);
            if (file.isDirectory()) {
                if (!expandInnerZips(file, progress, logError)) {
                    return false;
                }
            }
        }
        return true;
    }

    // Recursively unzip a whole file to a directory; return true on success
    // Can log errors.
    static public boolean unzipAll(File zipName, @NonNull File targetDir,
                                   StringCallback progress, ErrorCallback logError) {
        // Get a temp directory and make sure it doesn't exist. (That's "free" housekeeping
        // if previously something had gone wrong.)
        File targetParent = targetDir.getParentFile();
        File targetTmp = new File(targetParent, targetDir.getName() + UnzipTmpName);
        if (targetTmp.exists()) {
            deleteTree(targetTmp,logError);
        }

        try ( ZipFile zipData = new ZipFile(zipName) )
        {
            if (innerUnzipAll(zipData, targetTmp, progress, logError)) {
                // All went well, rename
                return renameTo(targetTmp, targetDir, logError);
            }
            return false;
        }
        catch (IOException e) {
            logError.Callback(SEVERE, String.format(getAppContext()
                            .getString(R.string.error_unzip_all_files_with_exception),
                    zipName, e.getLocalizedMessage()));
            return false;
        }
    }

    private static boolean innerUnzipAll(@NonNull ZipFile zipData, File targetTmp,
                                         StringCallback progress, ErrorCallback logError) {
        /*
           Nested zip files are possible, but we haven't seen one yet for audiobooks.
           Big performance concern here: we'd either have to extract the inner zip
           as a new file or use a zip stream to unpack it. Zip streams are VERY slow
           compared to files, but if the inner zip is big, we hit space issues
           However, when unpacking a book, we call expandInnerZips() after this runs,
           so any book will get cleaned up. (And probably we don't want to do that for
           non-books anyway.)
         */
        Enumeration<? extends ZipEntry> entryList = zipData.entries();
        while (entryList.hasMoreElements()) {
            ZipEntry subFile = entryList.nextElement();
            String newFile_name = subFile.getName();
            File newFile = new File(targetTmp, newFile_name);
            progress.Callback(newFile_name);
            if (subFile.isDirectory()) {
                if (!mkdirs(newFile, logError)) {
                    return false;
                }
            }
            else {
                // There isn't necessarily a zip Directory Entry prior to a file that goes into
                // a new sub-directory. Sort that out.
                File targetTmpDir = targetTmp;
                int pathEnd = newFile_name.lastIndexOf('/');
                if (pathEnd >= 0) {
                    String dirPath = newFile_name.substring(0,pathEnd);
                    targetTmpDir = new File(targetTmp,dirPath);
                }
                if (!mkdirs(targetTmpDir, logError)) {
                    return false;
                }
                try (BufferedInputStream from = new BufferedInputStream(zipData.getInputStream(subFile));
                     OutputStream to = new FileOutputStream(newFile))
                {
                    IOUtils.copy(from, to);
                }
                catch (IOException e) {
                    logError.Callback(SEVERE, String.format(getAppContext()
                                    .getString(R.string.error_unzip_single_file_with_exception),
                            newFile_name, e.getLocalizedMessage()));
                    return false;
                }
                catch (SecurityException e) {
                    logError.Callback(SEVERE, String.format(getAppContext()
                                    .getString(R.string.error_unzip_single_file_with_exception),
                            newFile_name, e.getLocalizedMessage()));
                    return false;
                }
            }
        }
        return true;
    }

    public static File findZipFileMatching(@NonNull File zipFile, FileFilter filter) {
        String baseName = zipFile.getName();
        int extensionPos = baseName.lastIndexOf('.');
        if (extensionPos > 0) {
            baseName = baseName.substring(0, extensionPos);
        }

        try (ZipFile fileData = new ZipFile(zipFile))
        {
            return innerFindZipFileMatching(baseName, fileData, filter);
        }
        catch (IOException e) {
            // ignore
        }
        return null;
    }

    private static File innerFindZipFileMatching(String tmpdirName, @NonNull ZipFile inputData, FileFilter filter) {
        try {
            Enumeration<? extends ZipEntry> entryList = inputData.entries();
            while (entryList.hasMoreElements()) {
                ZipEntry entry = entryList.nextElement();
                String name = entry.getName();

                if (filter.filterFunc(name)) {
                    // Some zippers put in a partial path name as the filename; for this,
                    // just strip that out.
                    File longName = new File("", name);
                    name = longName.getName();

                    // Make a copy of the data file in <cacheDir>/basename(zipFile)/name
                    // We use the source name to make it unique (so we don't collide when 2 books both
                    // have a "Book.opf", e.g.)
                    File cache = getAppContext().getCacheDir();
                    File tmpDir = new File(cache, tmpdirName);
                    if (tmpDir.exists()) {
                        // Just in case
                        deleteTree(tmpDir, (a,b)->{});
                    }
                    if (!tmpDir.mkdirs()) {
                        return null;
                    }
                    File targetFile = new File(tmpDir, name);
                    try (BufferedInputStream from = new BufferedInputStream(inputData.getInputStream(entry));
                         OutputStream to = new FileOutputStream(targetFile))
                    {
                        IOUtils.copy(from, to);
                    }
                    // files closed by try-with-resources
                    return targetFile;
                }
                /*
                   Nested zip files are possible, but we haven't seen one yet for audiobooks.
                   Big performance concern here: we'd either have to extract the inner zip
                   as a new file or use a zip stream to do this. Zip streams are VERY slow compared
                   to files, but if the inner zip is big, we hit space issues.
                   For picking individual files, we'll assume we won't need to deal with that.
                 */
            }
        }
        catch (IOException e) {
            // ignore
        }

        return null;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean mkdirs(@NonNull File f, ErrorCallback logError)
    {
        if (f.exists() && f.isDirectory()) {
            // (Trivially) succeeded
            return true;
        }
        try {
            if (f.mkdirs()) {
                return true;
            }
            logError.Callback(SEVERE, String.format(getAppContext()
                .getString(R.string.error_could_not_create_directory), f.getPath()));
        } catch(SecurityException e) {
            logError.Callback(SEVERE, String.format(getAppContext()
                .getString(R.string.error_could_not_create_directory_with_exception),
                    f.getPath(), e.getLocalizedMessage()));
        }
        return false;
    }

    public static boolean renameTo(@NonNull File oldFile, File newFile, ErrorCallback logError)
    {
        try {
            if (oldFile.renameTo(newFile)) {
                return true;
            }
            logError.Callback(SEVERE, String.format(getAppContext()
                .getString(R.string.error_could_not_rename), oldFile.getPath(), newFile.getPath()));
        } catch(SecurityException e) {
            logError.Callback(SEVERE, String.format(getAppContext()
                .getString(R.string.error_could_not_rename_with_exception),
                oldFile.getPath(), newFile.getPath(), e.getLocalizedMessage()));
        }
        return false;
    }

    static boolean atomicTreeCopy(File from, @NonNull File to, StringCallback progress, ErrorCallback logError) {
        // Get a temp directory and make sure it doesn't exist. (That's "free" housekeeping
        // if previously something had dong wrong.)
        File targetParent = to.getParentFile();
        File targetTmp = new File(targetParent, UnzipTmpName);
        if (targetTmp.exists()) {
            deleteTree(targetTmp, logError);
        }

        boolean result = treeCopy(from, targetTmp, progress, logError);

        if (result) {
            renameTo(targetTmp, to, logError);
        }
        return result;
    }

    // Copy a tree; return true on success, error description is posted
    private static boolean treeCopy(@NonNull File from, File to, StringCallback progress, ErrorCallback logError) {
        if (!from.exists()) {
            throw new RuntimeException("Attempt to copy nonexistent directory");
        }
        if (!mkdirs(to, logError)) {
            logError.Callback(SEVERE, String.format(getAppContext()
                    .getString(R.string.error_could_not_create_books), to.getPath()));
            return false;
        }

        String[] files = from.list();
        if (files == null) {
            return true;
        }

        for (String file: files) {
            File f = new File(from, file);
            File t = new File(to, file);
            if (f.isDirectory()) {
                if (!mkdirs(t, logError)) {
                    logError.Callback(SEVERE, String.format(getAppContext()
                            .getString(R.string.error_could_not_create_books), t.getPath()));
                    return false;
                }
                if (!treeCopy(f,t,progress, logError)) {
                    return false;
                }
            }
            else {
                progress.Callback(file);
                try(InputStream fs = new FileInputStream(f);
                    OutputStream ts = new FileOutputStream(t)) {
                    IOUtils.copy(fs, ts);
                }
                catch (IOException e) {
                    logError.Callback(SEVERE, String.format(getAppContext()
                        .getString(R.string.error_copy_single_failed), from.getPath(),
                        e.getLocalizedMessage()));
                    return false;
                }
                catch (SecurityException e) {
                    logError.Callback(SEVERE, String.format(getAppContext()
                                    .getString(R.string.error_copy_single_failed), from.getPath(),
                            e.getLocalizedMessage()));
                    return false;
                }
            }
        }
        return true;
    }

    // Fix filenames containing single digits to add leading zeros so it will sort correctly
    // when file names contain some sort of sequence number that isn't already with leading zeros
    // Tne number of leading zeros is determined by the number of files to be renamed.
    static boolean treeNameFix(@NonNull File tree, ErrorCallback logError) {
        if (!tree.exists()) {
            throw new RuntimeException("Attempt to fix-up names in nonexistent directory");
        }

        String[] files = tree.list();

        if (files == null) {
            return true;
        }

        ArrayList<String> dirs = new ArrayList<>();
        ArrayList<String> audio = new ArrayList<>();
        for (String file : files) {
            File f = new File(tree, file);
            if (f.isDirectory()) {
                if (!treeNameFix(f, logError)) {
                    return false;
                }
                dirs.add(file);
            } else if (FilesystemUtil.isAudioPath(file)) {
                audio.add(file);
            }
        }
        // We rename audio files and directories separately.
        // Primarily this is for the case where there are both random files
        // and directories in a single directory, so we don't rename
        // if there aren't at least 10 directories (or audio files, but
        // mixed audio and other files seems pretty weird).
        if (!fixNames(dirs, tree, logError)) {
            return false;
        }
        return fixNames(audio, tree, logError);
    }

    static private boolean fixNames(@NonNull ArrayList<String> files, File tree, ErrorCallback logError) {
        // If there are enough files...
        if (files.size() < 10) {
            return true;
        }
        // Convert all single digits to two
        if (!fileNameFix(files, tree, "(^|\\D)(\\d)(\\D|$)", "$10$2$3", logError)) {
            return false;
        }

        // Note: fileNameFix changed the entry in 'files' as well as on the disk.

        // All 2 digits to 3 if there are more than 100 files (yes, double rename of 10 files)
        if (files.size() < 100) {
            return true;
        }
        if (!fileNameFix(files, tree, "(^|\\D)(\\d\\d)(\\D|$)", "$10$2$3", logError)) {
            return false;
        }

        // Similarly, 3 to 4. Hopefully no-one is foolish enough to actually need this, because
        // it becomes a performance issue system wide.
        if (files.size() < 1000) {
            return true;
        }
        return fileNameFix(files, tree, "(^|\\D)(\\d\\d\\d)(\\D|$)", "$10$2$3", logError);
    }

    @SuppressWarnings("SameParameterValue")
    static private boolean fileNameFix(@NonNull ArrayList<String> files, File tree, String p1, String p2, ErrorCallback logError)
    {
        for (int i=0; i<files.size(); i++) {
            File f = new File(tree, files.get(i));
            File t = new File(tree, fixDigits(files.get(i), p1, p2));
            if (!f.getName().equals(t.getName())) {
                if (!renameTo(f, t, logError)) {
                    return false;
                }
                files.set(i,t.getName());
            }
        }
        return true;
    }

    // rm -rf might be a bit slower, but it's known thorough!
    public static boolean deleteTree(@NonNull File dir, ErrorCallback logError) {
        if (dir.exists()) {
            try {
                String[] commands = new String[3];
                commands[0] = "rm";
                commands[1] = "-r";
                commands[2] = dir.getCanonicalPath();
                Runtime runtime = Runtime.getRuntime();
                Process p = runtime.exec(commands);
                try {
                    p.waitFor();
                } catch (Exception e) {
                    logError.Callback(SEVERE, String.format(getAppContext()
                        .getString(R.string.error_delete_failed_with_exception),
                        dir.getPath(), e.getLocalizedMessage()));
                    return false;
                }
            }
            catch (IOException e) {
                logError.Callback(SEVERE, String.format(getAppContext()
                    .getString(R.string.error_delete_failed_with_exception),
                    dir.getPath(), e.getLocalizedMessage()));
                return false;
            }
        }
        return true;
    }

    // Find the (first) file in parentPath with the desired pattern and return the File
    // The pattern is whatever filter looks for
    public static File findFileMatching(@NonNull File parentPath, @NonNull FileFilter filter) {
        if (filter.filterFunc(parentPath.getName())) {
            return parentPath;
        }

        if (isZip(parentPath.getName())) {
            // The result will be in CacheDir
            return findZipFileMatching(parentPath, filter);
        }

        if (parentPath.isDirectory()) {
            String[] files = parentPath.list();
            if (files == null) {
                return null;
            }
            // Sorted for predictability
            Arrays.sort(files, String::compareTo);
            for (String fileName:files) {
                File t = findFileMatching(new File(parentPath,fileName), filter);
                if (t != null) {
                    return t;
                }
            }
        }

        return null;
    }

    public static void removeIfTemp(@NonNull File file) {
        File cache = getAppContext().getCacheDir();
        String cacheName = cache.getPath();
        if (file.getName().regionMatches(0, cacheName, 0, cacheName.length())) {
            // It's in a directory named by the original dir name in which it was found;
            // delete both directory and file. This should never fail, but if it does
            // Android will clean up later, and there's nothing sensible to do about it
            // since it has no consequence for the user.
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            //noinspection ResultOfMethodCallIgnored
            Objects.requireNonNull(file.getParentFile()).delete();
        }
    }

    public interface FileFilter {
        boolean filterFunc(String name);
    }
}
