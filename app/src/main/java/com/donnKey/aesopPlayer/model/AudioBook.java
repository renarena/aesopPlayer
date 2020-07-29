/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2020 Donn S. Terry
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

import androidx.annotation.NonNull;

import com.donnKey.aesopPlayer.events.AnAudioBookChangedEvent;
import com.donnKey.aesopPlayer.service.PlaybackService;
import com.donnKey.aesopPlayer.ui.provisioning.FileUtilities;
import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.filescanner.FileSet;
import com.donnKey.aesopPlayer.filescanner.ScanFilesTask;
import com.donnKey.aesopPlayer.ui.UiUtil;
import com.donnKey.aesopPlayer.util.DebugUtil;
import com.google.common.primitives.UnsignedLong;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import de.greenrobot.event.EventBus;

public class AudioBook {

    static public class TitleAndAuthor {
        // These fields should be case-correct and free of _ and / characters.
        public final String title;
        public final String author;
        TitleAndAuthor(String title, String author) {
            this.title = title;
            this.author = author;
        }
    }

    public final static long UNKNOWN_POSITION = -1;

    public interface UpdateObserver {
        void onAudioBookStateUpdated(AudioBook audioBook);
    }

    private FileSet fileSet;
    private List<Long> fileDurations;
    private ColourScheme colourScheme;
    private BookPosition lastPosition;
    private long totalDuration = UNKNOWN_POSITION;
    private boolean completed = false;
    private TitleAndAuthor titleInfo;
    private String displayTitle;
    private PlaybackService.DurationQuery bookDurationQuery;

    private UpdateObserver updateObserver;

    public AudioBook(@NonNull FileSet fileSet) {
        this.fileSet = fileSet;
        this.lastPosition = new BookPosition(0, 0);
        this.fileDurations = new ArrayList<>(fileSet.files.length);
    }

    public File getFile(@NonNull BookPosition position) {
        return fileSet.files[position.fileIndex];
    }

    public PlaybackService.DurationQuery getBookDurationQuery() {
        return bookDurationQuery;
    }

    public void setBookDurationQuery(PlaybackService.DurationQuery bookDurationQuery) {
        this.bookDurationQuery = bookDurationQuery;
    }

    void setUpdateObserver(UpdateObserver updateObserver) {
        this.updateObserver = updateObserver;
    }

    void replaceFileSet(FileSet fileSet) {
        this.fileSet = fileSet;
        this.displayTitle = null;
    }

    static public TitleAndAuthor metadataTitle(File file) {
        // MediaMetadataRetriever (the obvious choice) simply doesn't work,
        // not returning metadata that's clearly there.
        // (StackOverflow rumor has it that it's a Samsung issue in part.)
        // This is (apparently inherently) slow. Cache.

        String author = null;
        String newTitle = null;

        if (file != null) {
            try {
                AudioFile audioFile = AudioFileIO.read(file);
                Tag tag = audioFile.getTag();
                if (tag != null) {
                    newTitle = titleClean(tag.getFirst(FieldKey.ALBUM));
                    author = titleClean(tag.getFirst(FieldKey.ARTIST));
                }
            } catch (Exception e) {
                // Ignore any errors
            }
        }

        if (newTitle == null || newTitle.isEmpty()) {
            return null;
        }

        if (author == null) {
            author = "";
        }

        return new TitleAndAuthor(newTitle, author);
    }

    static public String computeTitle (@NonNull TitleAndAuthor title) {
        Preconditions.checkState(title.title != null && !title.title.isEmpty());

        String newTitle = title.title;
        if (title.author != null && !title.author.isEmpty()) {
            newTitle += " - " + title.author;
        }

        return newTitle;
    }

    public void setTitle(String title) {
        displayTitle = title;
        EventBus.getDefault().post(new AnAudioBookChangedEvent(this));
    }

    private String getRawTitle() {
        if (titleInfo == null) {
            titleInfo = extractTitle(fileSet.path, fileSet.files[0]);
        }
        return titleInfo.title;
    }

    public TitleAndAuthor getTitleInfo() {
        if (titleInfo == null) {
            titleInfo = extractTitle(fileSet.path, fileSet.files[0]);
        }
        return titleInfo;
    }

    public String getDisplayTitle() {
        if (titleInfo == null) {
            titleInfo = extractTitle(fileSet.path, fileSet.files[0]);
        }

        if (displayTitle != null) {
            return displayTitle;
        }

        if (fileSet.directoryName.indexOf(' ') >= 0) {
            // Spaces in the name -> it's supposed to be human-readable
            displayTitle = fileSet.directoryName;
            return displayTitle;
        }

        displayTitle = computeTitle(titleInfo);
        return displayTitle;
    }

    @NonNull
    public static TitleAndAuthor extractTitle(File bookPath, File audioPath)
    {
        TitleAndAuthor title = null;

        // First, get it from an associated information (.opf) file
        File opf = FileUtilities.findFileMatching(bookPath, (name)->name.endsWith(".opf"));
        if (opf != null) {
            title = new OpfParser().getTitle(opf);
            FileUtilities.removeIfTemp(opf);
        }

        // Then get it from the metadata
        if (title == null) {
            title = metadataTitle(audioPath);
        }

        if (title == null) {
            // filenameGuessTitle is the last gasp... it guarantees SOME title.
            title = new TitleAndAuthor(filenameGuessTitle(bookPath.getName()), "");
        }

        return title;
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
            chapterTitle = null;
            String fileName = fileSet.files[lastPosition.fileIndex].getPath();
            // Get it from the metadata
            // See above about MediaMetadataRetriever.
            try {
                File file = new File(fileName);
                AudioFile audioFile = AudioFileIO.read(file);
                Tag tag = audioFile.getTag();
                if (tag != null) {
                    chapterTitle = tag.getFirst(FieldKey.TITLE);
                }
            }
            catch (Exception e) {
                // Ignore any errors
            }

            if (chapterTitle == null || chapterTitle.isEmpty()) {
                // No metadata chapter title... fake it.
                fileName = fileSet.files[lastPosition.fileIndex].getName(); // name, not path
                chapterTitle = filenameGuessTitle(fileName);
            }

            // Guess if the book title is repeated in the chapter name and remove that
            String title = getRawTitle();
            int titleLoc = StringUtils.indexOfIgnoreCase(chapterTitle,title);

            if (titleLoc >= 0 && chapterTitle.length() > title.length()) {
                // If the chapter title is the book title do nothing
                char ch;
                int start = titleLoc;
                while (start > 0 &&
                    ((ch = chapterTitle.charAt(start-1)) == ' ' || ch == ':' || ch == '-')) {
                    start--;
                }
                int end = titleLoc + title.length();
                int len = chapterTitle.length();
                while (end < len-1 &&
                    ((ch = chapterTitle.charAt(end+1)) == ' ' || ch == ':' || ch == '-')) {
                    end++;
                }
                String pad = "";
                if (start > 0 && end < len-1) {
                    pad = " ";
                }
                String newTitle = chapterTitle.substring(0, start) + pad + (end < len ? chapterTitle.substring(end+1) : "");
                if (!newTitle.isEmpty()) {
                    chapterTitle = newTitle;
                }
            }

            lastFileIndex = lastPosition.fileIndex;
        }

        return chapterTitle;
    }

    public String getDirectoryName()
    {
        return fileSet.directoryName;
    }

    public File getPath()
    {
        return fileSet.path;
    }

    public BookPosition getLastPosition() {
        return lastPosition;
    }

    public BookPosition getBegin() {
        return new BookPosition(0, 0);
    }

    public long toMs(@NonNull BookPosition position) {
        int fullFileCount = position.fileIndex;

        if (fullFileCount <= fileDurations.size()) {
            return fileDurationSum(fullFileCount) + position.seekPosition;
        } else {
            return UNKNOWN_POSITION;
        }
    }

    public long getLastTotalPositionTime(long instantaneousSegmentPosition) {
        int fullFileCount = lastPosition.fileIndex;

        if (fullFileCount <= fileDurations.size()) {
            return fileDurationSum(fullFileCount) + instantaneousSegmentPosition;
        } else {
            return UNKNOWN_POSITION;
        }
    }

    public long getTotalDurationMs() {
        return totalDuration;
    }

    public void offerFileDuration(File file, long durationMs) {
        int index = Arrays.asList(fileSet.files).indexOf(file);
        Preconditions.checkState(index >= 0, "Attempt to size file failed: " + file.getName());
        Preconditions.checkState(index <= fileDurations.size(), "Duration set out of order: " + file.getPath());

        // Only set the duration if unknown.
        if (index == fileDurations.size()) {
            fileDurations.add(durationMs);
            if (fileDurations.size() == fileSet.files.length) {
                totalDuration = fileDurationSum(fileSet.files.length);
                // Notify the display the book info changed
                EventBus.getDefault().post(new AnAudioBookChangedEvent(this));
            }
            // Update the stored state (N.B.: that can contain deleted books.)
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

    public void updatePosition(long segmentPositionMs) {
        DebugUtil.verifyIsOnMainThread();
        lastPosition = new BookPosition(lastPosition.fileIndex, segmentPositionMs);
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
        lastPosition = new BookPosition(fileIndex, seekPosition);
        notifyUpdateObserver();
    }

    public void resetPosition() {
        DebugUtil.verifyIsOnMainThread();
        lastPosition = new BookPosition(0, 0);
        notifyUpdateObserver();
    }

    public ColourScheme getColourScheme() {
        return colourScheme;
    }

    void setColourScheme(ColourScheme colourScheme) {
        this.colourScheme = colourScheme;
    }

    public void setCompleted(boolean completed) {
        if (completed && this.fileSet.isReference) {
            // Just in case, allow it to be set false
            return;
        }
        this.completed = completed;
        if (completed) {
            maxPosition = 0;
        }
    }

    public boolean getCompleted() {
        return this.completed;
    }

    public boolean advanceFile() {
        DebugUtil.verifyIsOnMainThread();
        int newIndex = lastPosition.fileIndex + 1;
        boolean hasMoreFiles = newIndex < fileSet.files.length;
        if (hasMoreFiles) {
            lastPosition = new BookPosition(newIndex, 0);
            // below affects settings state, not playback
            notifyUpdateObserver();
        }

        return hasMoreFiles;
    }

    List<Long> getFileDurations() {
        return fileDurations;
    }

    void restore(
            ColourScheme colourScheme, int fileIndex, long seekPosition, List<Long> fileDurations,
            boolean completed, List<Long>bookStops, long maxPosition) {
        this.lastPosition = new BookPosition(fileIndex, seekPosition);
        if (colourScheme != null)
            this.colourScheme = colourScheme;
        if (fileDurations != null) {
            this.fileDurations = fileDurations;
            if (fileDurations.size() == fileSet.files.length)
                this.totalDuration = fileDurationSum(fileDurations.size());
        }
        this.completed = completed;
        this.lastStops = bookStops;
        this.maxPosition = maxPosition;
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
            lastPosition = new BookPosition(fileIndex, seekPosition);
        }
        this.completed = false; // Old won't have this
        this.lastStops = null;
        this.maxPosition = 0;
    }

    private long fileDurationSum(int fileCount) {
        long totalPosition = 0;
        for (int i = 0; i < fileCount; ++i) {
            totalPosition += fileDurations.get(i);
        }
        return totalPosition;
    }

    // Where we are in the current book
    public String thisBookProgress(Context context) {
        long currentTotalMs = this.toMs(lastPosition);
        String duration = UiUtil.formatDuration(currentTotalMs);

        long totalMs = getTotalDurationMs();
        String progress = "?";
        if (totalMs == UNKNOWN_POSITION) {
            progress = "??";
        }
        else if (currentTotalMs == 0) {
            progress = "0";
        }
        else if (totalMs != 0) {
            progress = (Long.valueOf((currentTotalMs * 100) / totalMs)).toString();
        }

        return context.getString(R.string.playback_elapsed_time, duration, progress);
    }

    /* Remove /s and extensions */
    public static String filenameCleanup(String name) {
        if (name == null) {
            return null;
        }
        name = name.replace('/', '-');

        int dot = name.lastIndexOf('.');
        if (dot>0 && name.length()-dot <= 4) {
            name = name.substring(0,dot);
        }
        return name;
    }

    @NonNull
    public static String filenameGuessTitle(String n) {
        // If we're installing a book and haven't found a good title, try to infer
        // something from the directory/zip file name. Informed guesswork at best.
        // It will always return some non-null string.
        if (n == null) {
            return "Unknown Title";
        }
        String name = n;

        int dot = name.lastIndexOf('.');
        if (dot>0 && name.length()-dot <= 4) {
            name = name.substring(0,dot);
        }

        name = name.replace('/', '-');

        // delete all pure-digit, underscore-delimited, strings
        name = name.replaceAll("(^|_)\\d+(_|$)", "_");

        // Underscore->space (so it's a word boundary!)
        name = name.replace('_', ' ');

        // delete the words mp3, 64kb, and librivox if present
        // (Not that I dislike Librivox, but it doesn't belong in a spoken title.)
        name = name.replaceAll("(?i)\\bmp3\\b", " ");
        name = name.replaceAll("(?i)\\b64kb\\b", " ");
        name = name.replaceAll("(?i)\\blibrivox\\b", " ");

        // delete things that look like serial numbers (with a leading letter, then all digits)
        name = name.replaceAll("\\b[A-Za-z]\\d+\\b", "");

        // Convert camel case to separate words
        name = name.replaceAll("(\\p{javaLowerCase})(\\p{javaUpperCase})(\\p{javaLowerCase})", "$1 $2$3");

        // clean up any multiple spaces
        name = name.replaceAll(" +", " ");
        name = name.replaceAll("^ ", "");
        name = name.replaceAll(" $", "");

        name = titleCase(name);

        if (name.isEmpty()) {
            return titleClean(n);
        }

        return name;
    }

    @NonNull
    public static String deBlank(@NonNull String name) {
        return name.replace(" ", "");
    }

    public boolean renameTo(String s, FileUtilities.ErrorCallback errorCallback) {
        File newName = new File(fileSet.path.getParent(), s);
        if (FileUtilities.renameTo(fileSet.path, newName, errorCallback)) {
            replaceFileSet(ScanFilesTask.createFileSet(newName));
            setTitle(s);
            return true;
        }
        return false;
    }

    public static String titleClean(String str) {
        if (str == null) {
            return null;
        }

        return str.replace('_', ' ')
                  .replace('/', '-');
    }

    public static String titleCase(String str) {
        if (str == null) {
            return null;
        }

        return WordUtils.capitalizeFully(titleClean(str));
    }

    // Keep a list of a few places the user stopped/paused the player: the NUMBER_OF_STOPS
    // largest time chops are kept in a sorted list, and given another time chop we can
    // return the one closest before or after that actual. Only chops which differ by a minute
    // or more are recorded. There's an implied one at the beginning, and if the querying chop
    // value is later than any recorded one, the actual chop is returned unmodified.
    // This is used to allow backspacing (or forward spacing) to recent stop points, which
    // are the best guess we have of when the user fell asleep.
    private final static long NUMBER_OF_STOPS = 5;
    private List<Long> lastStops = null;
    private long maxPosition = 0;

    public void insertStop(long position) {
        maxPosition = Math.max(position, maxPosition);
        if (lastStops == null) {
            lastStops = new Vector<>();
            lastStops.add((long) 0);
        }
        for (Long stop : lastStops) {
            if (Math.abs(position - stop) < TimeUnit.SECONDS.toMillis(60)) {
                // if there's an existing entry within one minute, treat this as a duplicate
                return;
            }
        }
        while (lastStops.size() >= NUMBER_OF_STOPS) {
            // remove the earliest ones if there are too many
            lastStops.remove(0);
        }
        lastStops.add(position);
        Collections.sort(lastStops, Long::compareTo);
    }

    public long getStopBefore(long position) {
        // For swipe-left
        // Returning 0 (for beginning) if we're before the earliest stop is just what we want
        long prev = 0;
        // Remember how far into the book we've ever been
        maxPosition = Math.max(position, maxPosition);
        if (lastStops == null) {
            return prev;
        }
        for (Long stop : lastStops) {
            if (position <= stop) {
                break;
            }
            prev = stop;
        }
        return prev;
    }

    public long getStopAfter(long position) {
        // For swipe-right
        // If we're past the last stop, just return the maximum visited
        // (A swipe right at the current position should be a no-op if we're
        // as far into the book as we ever have been.)
        if (lastStops == null) {
            return position;
        }
        for (Long stop : lastStops) {
            if (position < stop) {
                return stop;
            }
        }
        return Math.max(maxPosition, position);
    }

    public void setNewTime(long newBookPosition) {
        long lengthMs = getTotalDurationMs();
        if (newBookPosition < 0) {
            return;
        }
        if (newBookPosition > lengthMs) {
            newBookPosition = lengthMs;
        }

        updateTotalPosition(newBookPosition);
        setCompleted(false);
    }

    public static long timeToMillis(@NonNull String durationString) {
        // We can't use the time scanner because there are a few books longer than 24 hours.
        if (durationString.length() == 0) {
            // Error
            return -1;
        }

        // Ask split to retain a trailing empty string ("hh:" in our usage)
        String[] parts = durationString.split(":", -1);

        long duration = 0;
        try {
            switch (parts.length) {
                case 2:
                    // ':' present - it's hh:mm (either could be empty)
                    if (!parts[0].isEmpty()) {
                        duration = UnsignedLong.valueOf(parts[0]).longValue() * 60;
                    }
                    if (!parts[1].isEmpty()) {
                        duration += UnsignedLong.valueOf(parts[1]).longValue();
                    }
                    break;
                case 1:
                    // no ':' - it's just mm (or mmm)
                    if (!parts[0].isEmpty()) {
                        duration = UnsignedLong.valueOf(parts[0]).longValue(); // in minutes
                    }
                    break;
                default:
                    // nothing or nonsense
                    return -1;
            }
        } catch (NumberFormatException e) {
            return -1;
        }

        if (duration > 35000) {
            // A number slightly larger than that will overflow when converted to Millis, so
            // don't allow the overflow. (That's a bit under 600 hours.)
            duration = 35000;
        }
        return TimeUnit.MINUTES.toMillis(duration);
    }

    public List<Long> getBookStops() {
        return lastStops;
    }

    public long getMaxPosition() {
        return this.maxPosition;
    }

    void leave() {
        // We're done with this book for now. Be sure the stop times are saved.
        notifyUpdateObserver();
    }

    private void notifyUpdateObserver() {
        if (updateObserver != null)
            updateObserver.onAudioBookStateUpdated(this);
    }

    @NonNull
    @Override
    public String toString() {
        return getDisplayTitle() + " " + super.toString();
    }
}
