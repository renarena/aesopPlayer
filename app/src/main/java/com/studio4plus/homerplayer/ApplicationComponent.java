package com.studio4plus.homerplayer;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;

import com.studio4plus.homerplayer.analytics.AnalyticsTracker;
import com.studio4plus.homerplayer.content.ConfigurationContentProvider;
import com.studio4plus.homerplayer.model.AudioBookManager;
import com.studio4plus.homerplayer.model.DemoSamplesInstaller;
import com.studio4plus.homerplayer.player.Player;
import com.studio4plus.homerplayer.service.AudioBookPlayerModule;
import com.studio4plus.homerplayer.service.DemoSamplesInstallerService;
import com.studio4plus.homerplayer.service.DeviceMotionDetector;
import com.studio4plus.homerplayer.service.PlaybackService;
import com.studio4plus.homerplayer.ui.provisioning.CandidateFragment;
import com.studio4plus.homerplayer.ui.provisioning.InventoryItemFragment;
import com.studio4plus.homerplayer.ui.classic.ClassicPlaybackUi;
import com.studio4plus.homerplayer.ui.classic.FragmentBookItem;
import com.studio4plus.homerplayer.ui.classic.ClassicBookList;
import com.studio4plus.homerplayer.ui.classic.ClassicNoBooksUi;
import com.studio4plus.homerplayer.ui.classic.ClassicInitUi;
import com.studio4plus.homerplayer.ui.provisioning.Provisioning;
import com.studio4plus.homerplayer.ui.provisioning.ProvisioningActivity;
import com.studio4plus.homerplayer.ui.settings.KioskSelectionPreference;
import com.studio4plus.homerplayer.ui.settings.KioskSettingsFragment;
import com.studio4plus.homerplayer.ui.settings.MainSettingsFragment;
import com.studio4plus.homerplayer.ui.settings.PlaybackSettingsFragment;
import com.studio4plus.homerplayer.ui.classic.FragmentPlayback;

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
    void inject(HomerPlayerApplication application);
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
