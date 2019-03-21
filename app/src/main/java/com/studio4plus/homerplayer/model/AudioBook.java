package com.studio4plus.homerplayer.model;

import com.google.common.base.Preconditions;
import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import com.studio4plus.homerplayer.filescanner.FileSet;
import com.studio4plus.homerplayer.util.DebugUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AudioBook {

    public final static long UNKNOWN_POSITION = -1;

    public interface UpdateObserver {
        void onAudioBookStateUpdated(AudioBook audioBook);
    }

    public class Position {
        public final int fileIndex;
        public final long seekPosition;
        public final File file;

        Position(int fileIndex, long seekPosition) {
            this.fileIndex = fileIndex;
            this.seekPosition = seekPosition;
            this.file = fileSet.files[fileIndex];
        }
    }

    private final FileSet fileSet;
    private List<Long> fileDurations;
    private ColourScheme colourScheme;
    private Position lastPosition;
    private long totalDuration = UNKNOWN_POSITION;
    private boolean completed = false;

    private UpdateObserver updateObserver;

    public AudioBook(FileSet fileSet) {
        this.fileSet = fileSet;
        this.lastPosition = new Position(0, 0);
        this.fileDurations = new ArrayList<>(fileSet.files.length);
    }

    public void setUpdateObserver(UpdateObserver updateObserver) {
        this.updateObserver = updateObserver;
    }

    private String albumTitle;

    public String getTitle() {
        if (albumTitle != null) {
            return albumTitle;
        }

        String fileName = fileSet.files[lastPosition.fileIndex].getPath();

        String author = null;
        if (fileSet.directoryName.indexOf(' ') >= 0) {
            // Spaces in the name -> it's supposed to be human-readable
            albumTitle = directoryToTitle(fileSet.directoryName);
            return albumTitle;
        }
        else {
            // Get it from the metadata

            // MediaMetadataRetriever (the obvious choice) simply doesn't work,
            // not returning metadata that's clearly there.
            // (StackOverflow rumor has it that it's a Samsung issue in part.)
            // This is (apparently inherently) slow. Cache.
            try {
                Mp3File mp3file = new Mp3File(fileName);
                if (mp3file.hasId3v2Tag()) {
                    ID3v2 id3v2Tag = mp3file.getId3v2Tag();
                    albumTitle = id3v2Tag.getAlbum();
                    author = id3v2Tag.getArtist();
                }
                if (albumTitle == null || author == null) {
                    if (mp3file.hasId3v1Tag()) {
                        ID3v1 id3v1Tag = mp3file.getId3v1Tag();
                        if (albumTitle == null) {
                            albumTitle = id3v1Tag.getAlbum();
                        }
                        if (author == null) {
                            author = id3v1Tag.getArtist();
                        }
                    }
                }
                if (author != null) {
                    albumTitle += " - " + author;
                }
                // If any underscores get to here, get rid of them... they look
                // (and worse, sound) awful.
                albumTitle = directoryToTitle(albumTitle);
            }
            catch (Exception e) {
                // Ignore any errors
            }

            if (albumTitle == null || albumTitle.length() <= 0) {
                albumTitle = directoryToTitle(fileSet.directoryName);
            }
        }
        return albumTitle;
    }

    public String getId() {
        return fileSet.id;
    }

    private String chapterTitle;
    private int lastFileIndex = -1;

    // Do the best we can for this format.
    public String getChapter() {
        // Do this only if we haven't done it before
        if (lastPosition.fileIndex != lastFileIndex) {
            String fileName = fileSet.files[lastPosition.fileIndex].getPath();
            // Get it from the metadata
            // See above about MediaMetadataRetriever.
            try {
                Mp3File mp3file = new Mp3File(fileName);
                if (mp3file.hasId3v2Tag()) {
                    ID3v2 id3v2Tag = mp3file.getId3v2Tag();
                    chapterTitle = id3v2Tag.getTitle();
                }
                if (chapterTitle == null) {
                    if (mp3file.hasId3v1Tag()) {
                        ID3v1 id3v1Tag = mp3file.getId3v1Tag();
                        chapterTitle = id3v1Tag.getTitle();
                    }
                }
            }
            catch (Exception e) {
                // Ignore any errors
            }

            if (chapterTitle == null || chapterTitle.length() <= 0) {

                // No metadata chapter title... fake it.
                // Get rid of ".mp3" (etc.)
                chapterTitle = fileName.substring(0, fileName.lastIndexOf("."));
                // clean up _s
                chapterTitle = directoryToTitle(chapterTitle);

                // Guess if the title is repeated in the chapter name and remove that
                String title = getTitle();
                int titleLoc = chapterTitle.indexOf(title);
                if (titleLoc >= 0) {
                    chapterTitle = chapterTitle.substring(0, titleLoc)
                                 + chapterTitle.substring(titleLoc + title.length() + 1);
                }
            }
            lastFileIndex = lastPosition.fileIndex;
        }

        return chapterTitle;
    }

    public Position getLastPosition() {
        return lastPosition;
    }

    public long getLastPositionTime(long lastFileSeekPosition) {
        int fullFileCount = lastPosition.fileIndex;

        if (fullFileCount <= fileDurations.size()) {
            return fileDurationSum(fullFileCount) + lastFileSeekPosition;
        } else {
            return UNKNOWN_POSITION;
        }
    }

    public long getTotalDurationMs() {
        return totalDuration;
    }

    public void offerFileDuration(File file, long durationMs) {
        int index = Arrays.asList(fileSet.files).indexOf(file);
        Preconditions.checkState(index >= 0);
        Preconditions.checkState(index <= fileDurations.size(), "Duration set out of order.");

        // Only set the duration if unknown.
        if (index == fileDurations.size()) {
            fileDurations.add(durationMs);
            if (fileDurations.size() == fileSet.files.length)
                totalDuration = fileDurationSum(fileSet.files.length);
            notifyUpdateObserver();
        }
    }

    public List<File> getFilesWithNoDuration() {
        int count = fileSet.files.length;
        int firstIndex = fileDurations.size();
        List<File> files = new ArrayList<>(count - firstIndex);
        files.addAll(Arrays.asList(fileSet.files).subList(firstIndex, count));
        return files;
    }

    public boolean isDemoSample() {
        return fileSet.isDemoSample;
    }

    public void updatePosition(long seekPosition) {
        DebugUtil.verifyIsOnMainThread();
        lastPosition = new Position(lastPosition.fileIndex, seekPosition);
        notifyUpdateObserver();
    }

    public void updateTotalPosition(long totalPositionMs) {
        Preconditions.checkArgument(totalPositionMs <= totalDuration);

        long fullFileDurationSum = 0;
        int fileIndex = 0;
        for (; fileIndex < fileDurations.size(); ++fileIndex) {
            long total = fullFileDurationSum + fileDurations.get(fileIndex);
            if (totalPositionMs <= total)
                break;
            fullFileDurationSum = total;
        }
        long seekPosition = totalPositionMs - fullFileDurationSum;
        lastPosition = new Position(fileIndex, seekPosition);
        notifyUpdateObserver();
    }

    public void resetPosition() {
        DebugUtil.verifyIsOnMainThread();
        lastPosition = new Position(0, 0);
        notifyUpdateObserver();
    }

    public ColourScheme getColourScheme() {
        return colourScheme;
    }

    public void setColourScheme(ColourScheme colourScheme) {
        this.colourScheme = colourScheme;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean getCompleted() {
        return this.completed;
    }

    public boolean advanceFile() {
        DebugUtil.verifyIsOnMainThread();
        int newIndex = lastPosition.fileIndex + 1;
        boolean hasMoreFiles = newIndex < fileSet.files.length;
        if (hasMoreFiles) {
            lastPosition = new Position(newIndex, 0);
            notifyUpdateObserver();
        }

        return hasMoreFiles;
    }

    List<Long> getFileDurations() {
        return fileDurations;
    }

    void restore(
            ColourScheme colourScheme, int fileIndex, long seekPosition, List<Long> fileDurations,
            boolean completed) {
        this.lastPosition = new Position(fileIndex, seekPosition);
        if (colourScheme != null)
            this.colourScheme = colourScheme;
        if (fileDurations != null) {
            this.fileDurations = fileDurations;
            if (fileDurations.size() == fileSet.files.length)
                this.totalDuration = fileDurationSum(fileDurations.size());
        }
        this.completed = completed;
    }

    void restoreOldFormat(
            ColourScheme colourScheme, String fileName, long seekPosition, List<Long> fileDurations) {
        if (colourScheme != null)
            this.colourScheme = colourScheme;
        if (fileDurations != null) {
            this.fileDurations = fileDurations;
            if (fileDurations.size() == fileSet.files.length)
                this.totalDuration = fileDurationSum(fileDurations.size());
        }

        int fileIndex = -1;
        for (int i = 0; i < fileSet.files.length; ++i) {
            String path = fileSet.files[i].getAbsolutePath();
            if (path.endsWith(fileName)) {
                fileIndex = i;
                break;
            }
        }
        if (fileIndex >= 0) {
            lastPosition = new Position(fileIndex, seekPosition);
        }
        this.completed = false; // Old won't have this
    }

    private long fileDurationSum(int fileCount) {
        long totalPosition = 0;
        for (int i = 0; i < fileCount; ++i) {
            totalPosition += fileDurations.get(i);
        }
        return totalPosition;
    }

    private static String directoryToTitle(String directory) {
        return directory.replace('_', ' ');
    }

    private void notifyUpdateObserver() {
        if (updateObserver != null)
            updateObserver.onAudioBookStateUpdated(this);
    }
}
