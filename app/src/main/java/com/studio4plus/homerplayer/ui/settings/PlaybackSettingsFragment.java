package com.studio4plus.homerplayer.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.HomerPlayerApplication;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.model.AudioBook;
import com.studio4plus.homerplayer.model.AudioBookManager;
import com.studio4plus.homerplayer.ui.SnippetPlayer;
import com.studio4plus.homerplayer.ui.UiUtil;

import java.util.Objects;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

public class PlaybackSettingsFragment extends BaseSettingsFragment {

    @Nullable
    private SnippetPlayer snippetPlayer = null;

    @Inject public GlobalSettings globalSettings;
    @Inject public AudioBookManager audioBookManager;
    @Inject public EventBus eventBus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        HomerPlayerApplication.getComponent(Objects.requireNonNull(getActivity())).inject(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_playback, rootKey);
        SharedPreferences sharedPreferences = getSharedPreferences();

        findPreference(GlobalSettings.KEY_PLAYBACK_SPEED).setOnPreferenceClickListener(
                preference -> {
                    if (snippetPlayer != null && !snippetPlayer.isPlaying())
                        playSnippet();
                    return false;
                });

        DurationDialogPreference progressPreference =
                (DurationDialogPreference) findPreference(GlobalSettings.KEY_SET_PROGRESS);
        progressPreference.setOnNewValueListener(this::setNewBookPosition);

        updatePlaybackSpeedSummary(sharedPreferences);
        updateJumpBackSummary(sharedPreferences);
        updateStopOnFaceDownSummary(sharedPreferences);
        updateSleepTimerSummary();
        updateSetProgressSummary();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (snippetPlayer != null) {
            snippetPlayer.stop();
            snippetPlayer = null;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // TODO: use summary updaters when updating to androidx.
        switch(key) {
            case GlobalSettings.KEY_JUMP_BACK:
                updateJumpBackSummary(sharedPreferences);
                break;
            case GlobalSettings.KEY_SLEEP_TIMER:
                updateSleepTimerSummary();
                break;
            case GlobalSettings.KEY_PLAYBACK_SPEED:
                updatePlaybackSpeedSummary(sharedPreferences);
                playSnippet();
                break;
            case GlobalSettings.KEY_STOP_ON_FACE_DOWN:
                updateStopOnFaceDownSummary(sharedPreferences);
                break;
            case GlobalSettings.KEY_SET_PROGRESS:
                updateSetProgressSummary();
            break;
        }
    }

    private void updatePlaybackSpeedSummary(@NonNull SharedPreferences sharedPreferences) {
        String stringValue = sharedPreferences.getString(GlobalSettings.KEY_PLAYBACK_SPEED,
                getString(R.string.pref_playback_speed_default_value));
        Preconditions.checkNotNull(stringValue);
        ListPreference preference =
                (ListPreference) findPreference(GlobalSettings.KEY_PLAYBACK_SPEED);

        // We're assuming that the string form of the speeds is always %1.1f
        CharSequence[] standardValues = preference.getEntryValues();
        int index;
        for (index = 0; index<standardValues.length; index++) {
            if (stringValue.compareTo(standardValues[index].toString()) >= 0) {
                break;
            }
        }
        if (index > standardValues.length-1) {
            index = standardValues.length-1;
        }

        preference.setSummary(preference.getEntries()[index] + " (" + stringValue + ")");
    }

    private void updateJumpBackSummary(@NonNull SharedPreferences sharedPreferences) {
        String stringValue = sharedPreferences.getString(
                GlobalSettings.KEY_JUMP_BACK, getString(R.string.pref_jump_back_default_value));
        Preconditions.checkNotNull(stringValue);
        int value = Integer.parseInt(stringValue);
        Preference preference = findPreference(GlobalSettings.KEY_JUMP_BACK);
        if (value == 0) {
            preference.setSummary(R.string.pref_jump_back_entry_disabled);
        } else {
            preference.setSummary(String.format(
                    getString(R.string.pref_jump_back_summary), value));
        }
    }

    private void updateSleepTimerSummary() {
        ListPreference preference = (ListPreference) findPreference(GlobalSettings.KEY_SLEEP_TIMER);
        int index = preference.findIndexOfValue(preference.getValue());
        if (index == 0) {
            preference.setSummary(getString(R.string.pref_sleep_timer_summary_disabled));
        } else {
            CharSequence entry = preference.getEntries()[index];
            preference.setSummary(String.format(
                    getString(R.string.pref_sleep_timer_summary), entry));
        }
    }

    private void updateStopOnFaceDownSummary(SharedPreferences sharedPreferences) {
        updateListPreferenceSummary(
                sharedPreferences,
                GlobalSettings.KEY_STOP_ON_FACE_DOWN,
                R.string.pref_stop_on_face_down_default_value);
    }

    private void playSnippet() {
        if (snippetPlayer != null) {
            snippetPlayer.stop();
            snippetPlayer = null;
        }

        AudioBook book = audioBookManager.getCurrentBook();
        if (book != null) {
            snippetPlayer = new SnippetPlayer(getActivity(), eventBus, globalSettings.getPlaybackSpeed());

            snippetPlayer.play(book);
        }
    }

    private void updateSetProgressSummary() {
        DurationDialogPreference preference
                = (DurationDialogPreference) findPreference(GlobalSettings.KEY_SET_PROGRESS);

        AudioBook book = audioBookManager.getCurrentBook();
        String progress;
        if (book != null) {
            preference.setEnabled(true);
            String progressMessage = getString(R.string.pref_set_progress_message, book.getTitle());

            // The title in the edit box: will be ellipsized at end of second line.
            preference.setDialogTitle(progressMessage);

            // The title in the preferences list.
            preference.setTitle(progressMessage);

            AudioBook.Position position = book.getLastPosition();
            long currentMs = book.getLastPositionTime(position.seekPosition);
            progress = UiUtil.formatDurationShort(currentMs);
            preference.setSummary(progress);
            preference.setValue(currentMs);
        }
        else {
            preference.setEnabled(false);
            preference.setDialogTitle(R.string.noBooksMessage);
            preference.setTitle(R.string.noBooksMessage); // list
            preference.setSummary("");
        }
    }

    private void setNewBookPosition(long newBookPosition) {
        AudioBook book = audioBookManager.getCurrentBook();
        long lengthMs = book.getTotalDurationMs();
        if (newBookPosition > lengthMs) {
            newBookPosition = lengthMs;
        }
        book.updateTotalPosition(newBookPosition);

        updateSetProgressSummary();
    }

    @Override
    protected int getTitle() {
        return R.string.pref_playback_options_screen_title;
    }
}
