package com.studio4plus.aesopPlayer.util;

import com.studio4plus.aesopPlayer.BuildConfig;

public class VersionUtil {

    private static final String OFFICIAL_VERSION_PATTERN = "^\\d+\\.\\d+\\.\\d+$";

    public static boolean isOfficialVersion() {
        return !BuildConfig.DEBUG && BuildConfig.VERSION_NAME.matches(OFFICIAL_VERSION_PATTERN);
    }
}