package com.studio4plus.aesopPlayer.ui.provisioning;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.studio4plus.aesopPlayer.R;
import com.studio4plus.aesopPlayer.util.FilesystemUtil;

import org.apache.commons.compress.utils.IOUtils;

import static com.studio4plus.aesopPlayer.AesopPlayerApplication.getAppContext;
import static com.studio4plus.aesopPlayer.ui.provisioning.Provisioning.Severity.SEVERE;

public class FileUtilities {

    interface StringCallback {
        void Callback(String s);
    }

    interface ErrorCallback {
        void Callback(Provisioning.Severity severity, String text);
    }

    static public final String UnzipTmpName = ".TmpDir";

    // Unzip a whole file to a directory; return true on success
    // Can log errors.
    static boolean unzipAll(File zipName, File targetDir,
                           StringCallback progress, ErrorCallback logError) {
        // Get a temp directory and make sure it doesn't exist. (That's "free" housekeeping
        // if previously something had dong wrong.)
        File targetParent = targetDir.getParentFile();
        File targetTmp = new File(targetParent, UnzipTmpName);
        if (targetTmp.exists()) {
            deleteTree(targetTmp,logError);
        }

        try (FileInputStream fileData = new FileInputStream(zipName);
             ZipInputStream zipData = new ZipInputStream(fileData)
        ) {
            ZipEntry subFile;
            while ((subFile = zipData.getNextEntry()) != null) {
                File newFile = new File(targetTmp, subFile.getName());
                progress.Callback(subFile.getName());
                if (subFile.isDirectory()) {
                    if (!mkdirs(newFile, logError)) {
                        return false;
                    }
                }
                else {
                    if (!mkdirs(newFile.getParentFile(), logError)) {
                        return false;
                    }
                    try (
                        OutputStream to = new FileOutputStream(newFile)) {
                        IOUtils.copy(zipData, to);
                    }
                    catch (IOException e) {
                        logError.Callback(SEVERE, String.format(getAppContext()
                                .getString(R.string.error_unzip_single_file_with_exception),
                                newFile.getName(), e.getLocalizedMessage()));
                        return false;
                    }
                    catch (SecurityException e) {
                        logError.Callback(SEVERE, String.format(getAppContext()
                                .getString(R.string.error_unzip_single_file_with_exception),
                                newFile.getName(), e.getLocalizedMessage()));
                        return false;
                    }
                    zipData.closeEntry();
                }
            }

            // If all went well, rename it so a half-done job isn't a problem later
            renameTo(targetTmp, targetDir, logError);
        }
        catch (IOException e) {
            logError.Callback(SEVERE, String.format(getAppContext()
                    .getString(R.string.error_unzip_all_files_with_exception),
                    zipName, e.getLocalizedMessage()));
            return false;
        }
        return true;
    }

    static void unzipNamed(String zipName, String desired, File targetFile)
            throws IOException {
        try (FileInputStream fileData = new FileInputStream(zipName);
             ZipInputStream zipData = new ZipInputStream(fileData)
        ) {
            ZipEntry fileName;
            while ((fileName = zipData.getNextEntry()) != null) {
                if (fileName.getName().equals(desired)) {
                    try (OutputStream to = new FileOutputStream(targetFile)) {
                        IOUtils.copy(zipData, to);
                    }
                    break;
                }
            }
        }
    }

    static String getZipAudioFile(File zipFile) {
        try (FileInputStream fileData = new FileInputStream(zipFile);
            ZipInputStream zipData = new ZipInputStream(fileData)
        ) {
            ZipEntry fileName;
            while ((fileName = zipData.getNextEntry()) != null) {
                if (FilesystemUtil.isAudioPath(fileName.getName())) {
                    return fileName.getName();
                }
            }
        }
        catch (IOException e) {
            // ignore
        }
        return null;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean mkdirs(File f, ErrorCallback logError)
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean renameTo(File oldFile, File newFile, ErrorCallback logError)
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

    static boolean atomicTreeCopy(File from, File to, StringCallback progress, ErrorCallback logError) {
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
    private static boolean treeCopy(File from, File to, StringCallback progress, ErrorCallback logError) {
        if (!from.exists()) {
            throw new RuntimeException("Attempt to copy nonexistent directory");
        }
        if (!mkdirs(to, logError)) {
            logError.Callback(SEVERE, String.format(getAppContext()
                    .getString(R.string.error_could_not_create_books), to.getPath()));
            return false;
        }

        String[] files = from.list();

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

    // rm -rf might be a bit slower, but it's known thorough!
    static boolean deleteTree(File dir, ErrorCallback logError) {
        if (dir.exists()) {
            try {
                String commands[] = new String[3];
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

    // Find the first audio file in the tree
    static String getAudioFile(File parent) {
        String[] files = parent.list();

        for (String file: files) {
            if (FilesystemUtil.isAudioPath(file)) {
                return parent.getPath() + "/" + file;
            }
            File f = new File(parent, file);
            if (f.isDirectory()) {
                String t;
                if ((t = getAudioFile(f)) != null) {
                    return t;
                }
            }
        }
        return null;
    }
}
