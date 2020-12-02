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
package com.donnKey.aesopPlayer;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;

import com.donnKey.aesopPlayer.analytics.AnalyticsTracker;
import com.donnKey.aesopPlayer.content.ConfigurationContentProvider;
import com.donnKey.aesopPlayer.model.AudioBookManager;
import com.donnKey.aesopPlayer.model.DemoSamplesInstaller;
import com.donnKey.aesopPlayer.player.Player;
import com.donnKey.aesopPlayer.service.AudioBookPlayerModule;
import com.donnKey.aesopPlayer.service.DemoSamplesInstallerService;
import com.donnKey.aesopPlayer.service.DeviceMotionDetector;
import com.donnKey.aesopPlayer.service.PlaybackService;
import com.donnKey.aesopPlayer.ui.BootReceive;
import com.donnKey.aesopPlayer.ui.RewindSound;
import com.donnKey.aesopPlayer.ui.UiControllerBookList;
import com.donnKey.aesopPlayer.ui.provisioning.CandidateFragment;
import com.donnKey.aesopPlayer.ui.provisioning.ErrorTextFragment;
import com.donnKey.aesopPlayer.ui.provisioning.InventoryItemFragment;
import com.donnKey.aesopPlayer.ui.classic.ClassicPlaybackUi;
import com.donnKey.aesopPlayer.ui.classic.FragmentBookItem;
import com.donnKey.aesopPlayer.ui.classic.ClassicBookList;
import com.donnKey.aesopPlayer.ui.classic.ClassicNoBooksUi;
import com.donnKey.aesopPlayer.ui.classic.ClassicInitUi;
import com.donnKey.aesopPlayer.ui.provisioning.Mail;
import com.donnKey.aesopPlayer.ui.provisioning.PositionEditFragment;
import com.donnKey.aesopPlayer.ui.provisioning.Provisioning;
import com.donnKey.aesopPlayer.ui.provisioning.ProvisioningActivity;
import com.donnKey.aesopPlayer.ui.provisioning.RemoteAuto;
import com.donnKey.aesopPlayer.ui.provisioning.RemoteAutoWorker;
import com.donnKey.aesopPlayer.ui.provisioning.TitleEditFragment;
import com.donnKey.aesopPlayer.ui.settings.KioskSelectionPreference;
import com.donnKey.aesopPlayer.ui.settings.KioskSettingsFragment;
import com.donnKey.aesopPlayer.ui.settings.MainSettingsFragment;
import com.donnKey.aesopPlayer.ui.settings.NewVersionSettingsFragment;
import com.donnKey.aesopPlayer.ui.settings.PlaybackSettingsFragment;
import com.donnKey.aesopPlayer.ui.classic.FragmentPlayback;
import com.donnKey.aesopPlayer.ui.settings.RemoteSettingsFragment;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Component;
import org.greenrobot.eventbus.EventBus;

@Singleton
@ApplicationScope
@Component(modules = { ApplicationModule.class, AudioBookManagerModule.class, AudioBookPlayerModule.class })
public interface ApplicationComponent {
    // --Commented out by Inspection (1/19/2019 11:40 AM):void inject(BatteryStatusProvider batteryStatusProvider);
    // --Commented out by Inspection (1/19/2019 9:55 AM):void inject(BatteryStatusIndicator batteryStatusIndicator);
    void inject(DemoSamplesInstallerService demoSamplesInstallerService);
    void inject(ClassicBookList fragment);
    void inject(ClassicNoBooksUi fragment);
    void inject(ClassicInitUi fragment);
    void inject(ClassicPlaybackUi playbackUi);
    void inject(ConfigurationContentProvider provider);
    void inject(FragmentBookItem fragment);
    void inject(FragmentPlayback fragment);
    void inject(AesopPlayerApplication application);
    void inject(KioskSettingsFragment fragment);
    void inject(MainSettingsFragment fragment);
    void inject(PlaybackService playbackService);
    void inject(DeviceMotionDetector deviceMotionDetector);
    void inject(PlaybackSettingsFragment fragment);
    void inject(RemoteSettingsFragment fragment);
    void inject(InventoryItemFragment fragment);
    void inject(CandidateFragment fragment);
    // --Commented out by Inspection (5/10/2019 8:35 PM):void inject(CandidateRecyclerViewAdapter fragment);
    // --Commented out by Inspection (5/16/2019 2:30 PM):void inject(InventoryItemRecyclerViewAdapter fragment);
    void inject(ProvisioningActivity fragment);
    void inject(Provisioning fragment);
    void inject(KioskSelectionPreference fragment);
    void inject(AesopPlayerDeviceAdmin fragment);
    void inject(UiControllerBookList fragment);
    void inject(RewindSound rewindSound);
    void inject(NewVersionSettingsFragment newVersionSettingsFragment);
    void inject(RemoteAuto remoteAuto);
    void inject(RemoteAutoWorker remoteAutoWorker);
    void inject(Mail mail);
    void inject(ErrorTextFragment errorTextFragment);
    void inject(TitleEditFragment titleEditFragment);
    void inject(PositionEditFragment positionEditFragment);
    void inject(BootReceive bootReceive);

    Player createAudioBookPlayer();
    DemoSamplesInstaller createDemoSamplesInstaller();

    AnalyticsTracker getAnalyticsTracker();
    AudioBookManager getAudioBookManager();
    Context getContext();
    EventBus getEventBus();
    GlobalSettings getGlobalSettings();
    KioskModeSwitcher getKioskModeSwitcher();
    Resources getResources();
    @Named("SAMPLES_DOWNLOAD_URL") Uri getSamplesUrl();
}
