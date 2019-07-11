package com.studio4plus.aesopPlayer.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.google.common.base.Preconditions;
import com.studio4plus.aesopPlayer.GlobalSettings;
import com.studio4plus.aesopPlayer.AesopPlayerApplication;
import com.studio4plus.aesopPlayer.R;
import com.studio4plus.aesopPlayer.model.AudioBook;
import com.studio4plus.aesopPlayer.model.AudioBookManager;
import com.studio4plus.aesopPlayer.ui.SnippetPlayer;

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
        AesopPlayerApplication.getComponent(Objects.requireNonNull(getActivity())).inject(this);
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

        updatePlaybackSpeedSummary(sharedPreferences);
        updateJumpBackSummary(sharedPreferences);
        updateStopOnFaceDownSummary(sharedPreferences);
        updateSleepTimerSummary();
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

    @Override
    protected int getTitle() {
        return R.string.pref_playback_options_screen_title;
    }
}
