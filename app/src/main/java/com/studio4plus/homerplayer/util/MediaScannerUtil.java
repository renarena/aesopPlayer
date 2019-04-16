package com.studio4plus.homerplayer.util;

import android.content.Context;
import android.media.MediaScannerConnection;

import java.io.File;

public class MediaScannerUtil {

    public static void scanAndDeleteFile(final Context context, final File file) {
        // The effect here is to register the directory with the MediaManager as a directory;
        // a dummy file is created and deleted (and unregistered); apparently that creates a
        // side-effect in the MediaManager.
        MediaScannerConnection.OnScanCompletedListener listener =
                (path, uri) -> {
                    if (path == null)
                        return;

                    File scannedFile = new File(path);
                    if (scannedFile.equals(file)) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                        MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);
                    }
                };

        MediaScannerConnection.scanFile(context, new String[] { file.getAbsolutePath() }, null, listener);
    }
}
