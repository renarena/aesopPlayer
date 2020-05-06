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
import android.content.SharedPreferences;

import com.donnKey.aesopPlayer.events.CurrentBookChangedEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.greenrobot.event.EventBus;

public class Storage implements AudioBook.UpdateObserver {

    private static final String PREFERENCES_NAME = Storage.class.getSimpleName();
    private static final String AUDIOBOOK_KEY_PREFIX = "audiobook_";
    private static final String LAST_AUDIOBOOK_KEY = "lastPlayedId";

    private static final String FIELD_POSITION = "position";
    private static final String FIELD_COLOUR_SCHEME = "colourScheme";
    private static final String FIELD_POSITION_FILEPATH_DEPRECATED = "filePath";
    private static final String FIELD_POSITION_FILE_INDEX = "fileIndex";
    private static final String FIELD_POSITION_SEEK = "seek";
    private static final String FIELD_POSITION_COMPLETED = "completed";
    private static final String FIELD_FILE_DURATIONS = "fileDurations";
    private static final String FIELD_BOOK_STOPS = "bookStops";
    private static final String FIELD_MAX_POSITION = "maxPosition";


    private final SharedPreferences preferences;

    public Storage(Context context) {
        this.preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        EventBus.getDefault().register(this);
    }

    void readAudioBookState(AudioBook audioBook) {
        String bookData = preferences.getString(getAudioBookPreferenceKey(audioBook.getId()), null);
        if (bookData != null) {
            try {
                ColourScheme colourScheme = null;
                List<Long> durations = null;
                List<Long> bookStops = null;

                JSONObject jsonObject = (JSONObject) new JSONTokener(bookData).nextValue();
                JSONObject jsonPosition = jsonObject.getJSONObject(FIELD_POSITION);
                String fileName = jsonPosition.optString(FIELD_POSITION_FILEPATH_DEPRECATED, null);
                int fileIndex = jsonPosition.optInt(FIELD_POSITION_FILE_INDEX, -1);
                long seek = jsonPosition.getLong(FIELD_POSITION_SEEK);

                String colourSchemeName = jsonObject.optString(FIELD_COLOUR_SCHEME, null);
                if (colourSchemeName != null) {
                    colourScheme = ColourScheme.valueOf(colourSchemeName);
                }

                JSONArray jsonDurations = jsonObject.optJSONArray(FIELD_FILE_DURATIONS);
                if (jsonDurations != null) {
                    final int count = jsonDurations.length();
                    durations = new ArrayList<>(count);
                    for (int i = 0; i < count; ++i)
                        durations.add(jsonDurations.getLong(i));
                }

                boolean completed = jsonObject.optBoolean(FIELD_POSITION_COMPLETED, false);

                JSONArray jsonBookStops = jsonObject.optJSONArray(FIELD_BOOK_STOPS);
                if (jsonBookStops != null) {
                    final int count = jsonBookStops.length();
                    bookStops = new ArrayList<>(count);
                    for (int i = 0; i < count; ++i)
                        bookStops.add(jsonBookStops.getLong(i));
                }

                int maxPosition = jsonPosition.optInt(FIELD_MAX_POSITION, 0);

                if (fileIndex >= 0)
                    audioBook.restore(colourScheme, fileIndex, seek, durations, completed, bookStops, maxPosition);
                else
                    audioBook.restoreOldFormat(colourScheme, fileName, seek, durations);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    void writeAudioBookState(AudioBook audioBook) {
        JSONObject jsonAudioBook = new JSONObject();
        JSONObject jsonPosition = new JSONObject();
        BookPosition position = audioBook.getLastPosition();
        try {
            jsonPosition.put(FIELD_POSITION_FILE_INDEX, position.fileIndex);
            jsonPosition.put(FIELD_POSITION_SEEK, position.seekPosition);
            List<Long> durations = audioBook.getFileDurations();
            if (durations != null) {
                JSONArray jsonDurations = new JSONArray(audioBook.getFileDurations());
                jsonAudioBook.put(FIELD_FILE_DURATIONS, jsonDurations);
            }
            jsonAudioBook.put(FIELD_POSITION, jsonPosition);
            jsonAudioBook.putOpt(FIELD_COLOUR_SCHEME, audioBook.getColourScheme());
            jsonAudioBook.put(FIELD_POSITION_COMPLETED, audioBook.getCompleted());
            List<Long> stops = audioBook.getBookStops();
            if (stops != null) {
                JSONArray jsonStops = new JSONArray(stops);
                jsonAudioBook.put(FIELD_BOOK_STOPS, jsonStops);
            }
            jsonAudioBook.put(FIELD_MAX_POSITION, audioBook.getMaxPosition());

            SharedPreferences.Editor editor = preferences.edit();
            String key = getAudioBookPreferenceKey(audioBook.getId());
            editor.putString(key, jsonAudioBook.toString());
            editor.apply();
        } catch (JSONException e) {
            // Should never happen, none of the values is null, NaN nor Infinity.
            e.printStackTrace();
        }
    }

    String getCurrentAudioBook() {
        return preferences.getString(LAST_AUDIOBOOK_KEY, null);
    }

    private void writeCurrentAudioBook(String id) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(LAST_AUDIOBOOK_KEY, id);
        editor.apply();
    }

    @Override
    public void onAudioBookStateUpdated(AudioBook audioBook) {
        writeAudioBookState(audioBook);
    }

    private String getAudioBookPreferenceKey(String id) {
        return AUDIOBOOK_KEY_PREFIX + id;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void onEvent(CurrentBookChangedEvent event) {
        writeCurrentAudioBook(event.audioBook.getId());
    }

    // Remove old (deleted) books from the list, so they don't build up over time.
    // (Yes, VERY occasionally remembering the position would be nice, but it's not worth the leak.)
    void cleanOldEntries(AudioBookManager audioBooks) {
        SharedPreferences.Editor editor = preferences.edit();

        Map<String,?> oldBooks = preferences.getAll();
        for (Map.Entry<String, ?> oldBook : oldBooks.entrySet()) {
            String prefId = oldBook.getKey();
            if (prefId.startsWith(AUDIOBOOK_KEY_PREFIX)) {
                String bookId = prefId.substring(AUDIOBOOK_KEY_PREFIX.length());
                AudioBook book = audioBooks.getById(bookId);
                if (book == null) {
                    editor.remove(prefId);
                }
            }
        }

        editor.apply();
    }
}
