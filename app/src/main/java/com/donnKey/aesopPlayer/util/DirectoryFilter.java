package com.donnKey.aesopPlayer.util;

import java.io.File;
import java.io.FileFilter;

import static com.donnKey.aesopPlayer.ui.provisioning.FileUtilities.UnzipTmpName;

public class DirectoryFilter implements FileFilter {

    @Override
    public boolean accept(File pathname) {
        // Ignore non-directories and ".TmpDir"
        // N.B. You have to use ls -a from a terminal window to reliably see .TmpDir .
        // Not even Android Studio DeviceFileExplorer will reliably show it.
        if (pathname.getName().equals(UnzipTmpName)) {
            return false;
        }
        return pathname.isDirectory();
    }
}
