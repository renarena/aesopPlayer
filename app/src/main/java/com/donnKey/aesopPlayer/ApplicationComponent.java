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
import com.donnKey.aesopPlayer.ui.provisioning.CandidateFragment;
import com.donnKey.aesopPlayer.ui.provisioning.InventoryItemFragment;
import com.donnKey.aesopPlayer.ui.classic.ClassicPlaybackUi;
import com.donnKey.aesopPlayer.ui.classic.FragmentBookItem;
import com.donnKey.aesopPlayer.ui.classic.ClassicBookList;
import com.donnKey.aesopPlayer.ui.classic.ClassicNoBooksUi;
import com.donnKey.aesopPlayer.ui.classic.ClassicInitUi;
import com.donnKey.aesopPlayer.ui.provisioning.Provisioning;
import com.donnKey.aesopPlayer.ui.provisioning.ProvisioningActivity;
import com.donnKey.aesopPlayer.ui.settings.KioskSelectionPreference;
import com.donnKey.aesopPlayer.ui.settings.KioskSettingsFragment;
import com.donnKey.aesopPlayer.ui.settings.MainSettingsFragment;
import com.donnKey.aesopPlayer.ui.settings.PlaybackSettingsFragment;
import com.donnKey.aesopPlayer.ui.classic.FragmentPlayback;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Component;
import de.greenrobot.event.EventBus;

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
    void inject(InventoryItemFragment fragment);
    void inject(CandidateFragment fragment);
    // --Commented out by Inspection (5/10/2019 8:35 PM):void inject(CandidateRecyclerViewAdapter fragment);
    // --Commented out by Inspection (5/16/2019 2:30 PM):void inject(InventoryItemRecyclerViewAdapter fragment);
    void inject(ProvisioningActivity fragment);
    void inject(Provisioning fragment);
    void inject(KioskSelectionPreference fragment);

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
