/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Donn S. Terry
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
package com.donnKey.aesopPlayer.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.model.AudioBookManager;
import com.donnKey.aesopPlayer.ui.UiUtil;
import com.donnKey.aesopPlayer.ui.provisioning.Mail;
import com.donnKey.aesopPlayer.ui.provisioning.RemoteAuto;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Objects;

import javax.inject.Inject;

import org.greenrobot.eventbus.EventBus;

public class RemoteSettingsFragment extends BaseSettingsFragment {

    @Inject public GlobalSettings globalSettings;
    @Inject public AudioBookManager audioBookManager;
    @Inject public EventBus eventBus;

    int mailValidated = Mail.UNRESOLVED;
    boolean remoteMailValidated = false;
    boolean remoteFileValidated = false;

    // A way RemoteAuto's loop to stop doing things while we're changing them.
    static public boolean inRemoteSettings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AesopPlayerApplication.getComponent().inject(this);
        inRemoteSettings = true;
        super.onCreate(savedInstanceState);

        mailValidated = Mail.UNRESOLVED;
        requireActivity().getOnBackPressedDispatcher().addCallback(this, onBackPressed);

        EditTextPreference deviceName = getPreferenceScreen().findPreference(GlobalSettings.KEY_REMOTE_DEVICE_NAME);
        assert deviceName!=null;

        deviceName.setOnPreferenceChangeListener((preference, n)-> {
            String newValue = (String)n;
            if (!newValue.matches("[\\p{Alnum}_-]*")) {
                new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.remote_device_name_title)
                    .setIcon(R.drawable.ic_launcher)
                    .setMessage(R.string.remote_device_name_illegal)
                    .setPositiveButton(android.R.string.yes, null)
                    .show();
                return false;
            }
            return true;
        });
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_remote_auto, rootKey);
    }

    @Override
    public void onResume() {
        super.onResume();

        doRemoteFileValidation();

        boolean mailGroupVisible = globalSettings.getMailPollEnabled();
        boolean fileGroupVisible = globalSettings.getFilePollEnabled();

        switchField(GlobalSettings.KEY_REMOTE_MAIL_POLL, this::remoteMailSummaryProvider);
        requiredField(GlobalSettings.KEY_REMOTE_LOGIN, mailGroupVisible,
                globalSettings::getMailLogin, false, this::loginBindListener);
        requiredField(GlobalSettings.KEY_REMOTE_PASSWORD, mailGroupVisible,
                globalSettings::getMailPassword, true, this::passwordBindListener);
        requiredField(GlobalSettings.KEY_REMOTE_HOST, mailGroupVisible,
                globalSettings::getMailHostname, false, this::otherBindListener);
        optionalField(GlobalSettings.KEY_REMOTE_DEVICE_NAME, mailGroupVisible,
                globalSettings::getMailDeviceName, false, this::otherBindListener);

        switchField(GlobalSettings.KEY_REMOTE_FILE_POLL, this::remoteFileSummaryProvider);
        requiredField(GlobalSettings.KEY_REMOTE_CONTROL_DIR, fileGroupVisible,
                globalSettings::getRemoteControlDir, false, this::directoryNameBindListener);
    }

    @Override
    public void onDestroy() {
        inRemoteSettings = false;
        super.onDestroy();
    }

    final OnBackPressedCallback onBackPressed = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            String message = null;
            if (globalSettings.getMailPollEnabled() && !remoteMailValidated) {
                message = getString(R.string.remote_must_have_credentials);
            }
            else if (globalSettings.getFilePollEnabled() && !remoteFileValidated) {
                message = getString(R.string.remote_must_have_valid_directory);
            }
            if (message != null) {
                // Complain (and don't do back)
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.remote_must_have_required)
                        .setIcon(R.drawable.ic_launcher)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.yes, null)
                        .show();
            }
            else {
                // Do the back operation
                RemoteAuto.activate(globalSettings.getMailPollEnabled() || globalSettings.getFilePollEnabled());
                this.setEnabled(false);
                requireActivity().onBackPressed();
            }
        }
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch(key) {
            case GlobalSettings.KEY_REMOTE_LOGIN:
            case GlobalSettings.KEY_REMOTE_PASSWORD:
            case GlobalSettings.KEY_REMOTE_HOST:
            case GlobalSettings.KEY_REMOTE_MAIL_POLL:
                mailValidated = Mail.UNRESOLVED;
                checkMailAddress();
                // drop thru
            case GlobalSettings.KEY_REMOTE_FILE_POLL:
            case GlobalSettings.KEY_REMOTE_CONTROL_DIR:
            case GlobalSettings.KEY_REMOTE_DEVICE_NAME:
                // So it redraws
                requireActivity().getSupportFragmentManager().beginTransaction().detach(this).attach(this).commit();
                break;
        }
    }

    private void requiredField(String key, boolean visible, getResource getResource, boolean secure,
                               EditTextPreference.OnBindEditTextListener listener) {
        final EditTextPreference preference = findPreference(key);
        assert(preference != null);

        // Flag values as needing to be provided
        preference.setSummaryProvider((pref)-> {
            HookEditTextPreference p = (HookEditTextPreference) pref;
            String resValue = getResource.getter();
            TextView summary = p.getItemView().findViewById(android.R.id.summary);

            if (resValue.isEmpty()) {
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.settingsProblemColor));
                return getString(R.string.remote_required_word);
            } else {
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.provisioningTextColor3));
                if (secure) {
                    return (StringUtils.repeat('*', resValue.length()));
                }
                else {
                    return resValue;
                }
            }
        });

        preference.setVisible(visible);

        // Set input type, change provider to always asterisks since it's now set.
        if (listener != null) {
            preference.setOnBindEditTextListener(listener);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void optionalField(String key, boolean visible, getResource getResource, boolean secure,
                               EditTextPreference.OnBindEditTextListener listener) {
        final EditTextPreference preference = findPreference(key);
        assert(preference != null);

        // Flag an optional parameter
        preference.setSummaryProvider((pref)-> {
            HookEditTextPreference p = (HookEditTextPreference) pref;
            String resValue = getResource.getter();
            TextView summary = p.getItemView().findViewById(android.R.id.summary);

            if (resValue.isEmpty()) {
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.settingsConcernColor));
                return getString(R.string.remote_optional_word);
            } else {
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.provisioningTextColor3));
                if (secure) {
                    return (StringUtils.repeat('*', resValue.length()));
                }
                else {
                    return resValue;
                }
            }
        });

        preference.setVisible(visible);

        // Set input type, change provider to always asterisks since it's now set.
        if (listener != null) {
            preference.setOnBindEditTextListener(listener);
        }
    }

    void switchField (String key, Preference.SummaryProvider<SwitchPreference> summaryProvider) {
        final SwitchPreference preference = findPreference(key);
        assert(preference != null);

        preference.setSummaryProvider(summaryProvider);
    }

    void loginBindListener (@NonNull EditText editText) {
        // Haven't figured out how to get rid of the emoji line and also have the @ convenient...
        // this is as good as it gets
        editText.setInputType(InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    }

    void passwordBindListener (@NonNull EditText editText) {
        editText.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    }

    void otherBindListener (@NonNull EditText editText) {
        editText.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    }

    String remoteMailSummaryProvider(Preference pref) {
        // Compute what (and what color) to show depending on the data that the user entered.
        // This for mail: there are a lot of potential failure states to describe
        HookSwitchPreference p = (HookSwitchPreference) pref;
        TextView summary = p.getItemView().findViewById(android.R.id.summary);

        remoteMailValidated = false;
        if (!globalSettings.getMailPollEnabled()) {
            summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.provisioningTextColor3));
            return getString(R.string.remote_disabled_word);
        }
        if (globalSettings.getMailLogin().isEmpty()
                || globalSettings.getMailPassword().isEmpty()
                || globalSettings.getMailHostname().isEmpty()) {
            summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.settingsProblemColor));
            return getString(R.string.remote_incomplete_state);
        }
        switch (this.getMailValidated()) {
            case Mail.SUCCESS: {
                remoteMailValidated = true;
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.provisioningTextColor3));
                return getString(R.string.remote_validated_state);
            }
            case Mail.UNRESOLVED:
                // Going through Back can get us here. Just fix it.
                checkMailAddress();
                // drop thru
            case Mail.PENDING: {
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.settingsConcernColor));
                return getString(R.string.remote_pending_state);
            }
            case Mail.OTHER_ERROR: {
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.settingsProblemColor));
                return getString(R.string.remote_other_state);
            }
            case Mail.UNRECOGNIZED_HOST: {
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.settingsProblemColor));
                return getString(R.string.remote_unrecognized_host_state);
            }
            case Mail.UNRECOGNIZED_USER_PASSWORD: {
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.settingsProblemColor));
                return getString(R.string.remote_unrecognized_user_password_state);
            }
            default: {
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.settingsProblemColor));
                return "Internal State Error"; // should never occur, not translated
            }
        }
    }

    private int doRemoteFileValidation()
    {
        // we need to compute remoteFileValidated before the preference is displayed;
        // remoteFileSummaryProvider doesn't get run until it's on screen!
        // (Theoretically that applies to remoteMailValidated too, but its
        // preference always starts on screen.)
        remoteFileValidated = false;
        String rd = globalSettings.getRemoteControlDir();
        if (!globalSettings.getFilePollEnabled()) {
            return 0;
        }
        else if (rd.isEmpty()) {
            // Pragmatically, this can't happen because an empty directory is forced to
            // "Download", but just in case it somehow happens.
            return 1;
        }
        else if (!new File(rd).canWrite()) {
            return 2;
        }
        else {
            remoteFileValidated = true;
            return 3;
        }
    }

    String remoteFileSummaryProvider(Preference pref) {
        // Compute what (and what color) to show depending on the data that the user entered.
        // This for the log file directory
        HookSwitchPreference p = (HookSwitchPreference) pref;
        TextView summary = p.getItemView().findViewById(android.R.id.summary);

        remoteFileValidated = false;
        switch (doRemoteFileValidation()) {
            case 0:
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.provisioningTextColor3));
                return getString(R.string.remote_disabled_word);
            case 1:
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.settingsProblemColor));
                return getString(R.string.remote_incomplete_state);
            case 2:
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.settingsProblemColor));
                return getString(R.string.remote_unwriteable_state);
            case 3:
            default:
                summary.setTextColor(UiUtil.colorFromAttribute(requireContext(), R.attr.provisioningTextColor3));
                return getString(R.string.remote_validated_state);
        }
    }

    void directoryNameBindListener (@NonNull EditText editText) {
        // Rather than make the user remember the path to 'Download', substitute it
        // for an empty entry, since empty would always be illegal.
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        editText.setOnEditorActionListener((v, actionId, e) -> {
                switch (actionId) {
                    case EditorInfo.IME_ACTION_DONE:
                    case EditorInfo.IME_ACTION_NEXT:
                    case EditorInfo.IME_ACTION_PREVIOUS:
                        String s = Objects.requireNonNull(editText.getText()).toString().trim();
                        if (s.isEmpty()) {
                            s = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
                        }
                        editText.setText(s);
                }
                return false; // We tweaked the result, but didn't consume the action
            });
    }

    void checkMailAddress() {
        // Use the mailer to determine if the host/user/password is valid.
        // We can't do networking on the main thread, so a little thread magic
        if (mailValidated != Mail.UNRESOLVED) {
            return;
        }
        mailValidated = Mail.PENDING;
        Thread t = new Thread( () ->
        {
            AppCompatActivity activity = (AppCompatActivity) requireActivity();//.getParent());
            Mail mail = new Mail();
            mailValidated = mail.testConnection();
            activity.runOnUiThread(() ->
                activity.getSupportFragmentManager().beginTransaction().detach(this).attach(this).commit()
            );
        });
        t.start();
    }

    int getMailValidated() {
        // We need the dynamic value of this, not a captured one. Thus, this.
        return mailValidated;
    }

    private interface getResource {
        String getter();
    }

    static public boolean getInRemoteSettings() {
        return inRemoteSettings;
    }

    @Override
    protected int getTitle() {
        return R.string.pref_remote_options_screen_title;
    }
}
