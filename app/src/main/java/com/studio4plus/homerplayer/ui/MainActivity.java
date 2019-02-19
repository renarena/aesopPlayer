package com.studio4plus.homerplayer.ui;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.HomerPlayerApplication;
import com.studio4plus.homerplayer.KioskModeSwitcher;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.battery.BatteryStatusProvider;
import com.studio4plus.homerplayer.concurrency.SimpleDeferred;
import com.studio4plus.homerplayer.ui.classic.ClassicMainUiModule;
import com.studio4plus.homerplayer.ui.classic.DaggerClassicMainUiComponent;
import com.studio4plus.homerplayer.concurrency.SimpleFuture;
import com.studio4plus.homerplayer.ui.settings.SettingsActivity;

import java.lang.reflect.Method;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

public class MainActivity extends AppCompatActivity implements SpeakerProvider {

    private static final int TTS_CHECK_CODE = 1;
    private static final String KIOSK_MODE_ENABLE_ACTION = "KioskModeEnable";
    private static final String ENABLE_EXTRA = "Enable";

    @SuppressWarnings("FieldCanBeLocal")
    private MainUiComponent mainUiComponent;

    private BatteryStatusIndicator batteryStatusIndicator;
    private static @Nullable
    SimpleDeferred<Object> ttsDeferred;
    private OrientationActivityDelegate orientationDelegate;

    @SuppressWarnings("WeakerAccess")
    @Inject
    public UiControllerMain controller;
    @SuppressWarnings("WeakerAccess")
    @Inject
    public BatteryStatusProvider batteryStatusProvider;
    @SuppressWarnings("WeakerAccess")
    @Inject
    public GlobalSettings globalSettings;
    @SuppressWarnings("WeakerAccess")
    @Inject
    public KioskModeHandler kioskModeHandler;
    @SuppressWarnings("WeakerAccess")
    @Inject
    public KioskModeSwitcher kioskModeSwitcher;

    private PowerManager powerManager;
    private ActivityManager activityManager;
    private StatusBarCollapser statusBarCollapser;

    // Used for Oreo and up suppression of status bar.
    private boolean isPaused = false;

    /* General comments:
       This section contains a lot of heuristically arrived at ad-hoc code to make the
       "simple" kiosk mode work reasonably well. Most things are easy enough, but handling of
       the Home (a.k.a Start) key (effectively "application pause") is messy. There are a few
       timeouts, but their values are not particularly critical to the design,
       although they may affect apparent responsiveness.

       Expected results do include strange partial animations, temporary revisions to portrait
       mode, and black screens. I know of no way to make any of these "stick" - they'll always
       go away after several seconds. (But I don't claim it's impossible.)

       It also posts toasts telling the user that exit was blocked. They're not always accurate,
       with both false positives and false negatives, but neither actually affects anything, and
       are associated with fast, repeated key presses.

       See restoreApp() for details on how it and cancelRestore() interact as the last line
       of defense from excessive button pushing.

       This works pretty well on 4.4.2 devices: on newer devices it's not quite as solid and more
       prone to odd things happening. Application Pinning and or full Kiosk may be better there.

       Note that Samsung has the S-Voice application that's started with a double-tap of the Home
       key on some devices. That has to be explicitly disabled manually for this to be reliable.
       See http://www.androidbeat.com/2014/05/disable-s-voice-galaxy-s5/ .
     */

    // The "exit blocked" toast looks much better after the resume.
    private boolean postToastOnResume;

    // If we did an onStart recently, we (probably) aren't seeing bad button pushing, don't toast.
    private boolean recentlyDidStart;

    // For pause overrides force some restarts that would otherwise hang.
    private boolean recentPauseOverride;

    // These need to be on separate handler queues so they can be cleared.
    // (I can't make removing specific run-ables from the common queue work!)
    private final Handler recentPauseOverrideHandler = new Handler();
    private final Handler recentStartHandler = new Handler();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        cancelRestore();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        activityManager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);

        mainUiComponent = DaggerClassicMainUiComponent.builder()
                .applicationComponent(HomerPlayerApplication.getComponent(this))
                .activityModule(new ActivityModule(this))
                .classicMainUiModule(new ClassicMainUiModule(this))
                .build();
        mainUiComponent.inject(this);

        controller.onActivityCreated();

        batteryStatusIndicator = new BatteryStatusIndicator(
                findViewById(R.id.batteryStatusIndicator), EventBus.getDefault());

        orientationDelegate = new OrientationActivityDelegate(this, globalSettings);

        statusBarCollapser = new StatusBarCollapser();

        View touchEventEater = findViewById(R.id.touchEventEater);
        touchEventEater.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Tell the other views that the event has been handled.
                return true;
            }
        });
    }

    @Override
    protected void onStart() {
        isPaused = false;
        // If app is started with a black screen (thus, from the debugger) various bad things
        // appear to happen not under our control. At a minimum it will loop between start
        // and stop states (with all the intermediate stuff as expected) at a fairly high
        // rate (about 1.2 sec interval on one machine: not even seconds). This does not
        // occur if the screen is on.
        cancelRestore();

        // Indicate that onStart just ran ...
        recentlyDidStart = true;
        recentStartHandler.removeCallbacksAndMessages(null);
        recentStartHandler.postDelayed(()-> recentlyDidStart = false , 1000); //... but only for a second

        super.onStart();
        // onStart must be called before the UI controller can manipulate fragments.
        controller.onActivityStart();
        orientationDelegate.onStart();
        batteryStatusProvider.start();
        kioskModeHandler.onActivityStart();
        kioskModeHandler.controlStatusBarExpansion(getApplicationContext(),
                globalSettings.isSimpleKioskModeEnabled());
        handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        isPaused = false;
        cancelRestore();
        postToastOnResume &= !recentlyDidStart;
        postToastOnResume &= !controller.justDidPauseActionAndReset();

        if (postToastOnResume) {
            // onPause decided to restart the activity; tell the user here because it
            // looks better when it does a full redraw.
            toastKioskActive();
            postToastOnResume = false;
        }
        kioskModeHandler.controlStatusBarExpansion(getApplicationContext(),
                globalSettings.isSimpleKioskModeEnabled());
        super.onResume();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        // Do nothing, this activity takes state from the PlayerService and the AudioBookManager.
        // There's no fault here, but a runtime error would point you here.
        // See ClassicMainUi:showPage for details.
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        // Do nothing, this activity takes state from the PlayerService and the AudioBookManager.
    }

    private boolean isInteractive() {
        return Build.VERSION.SDK_INT >= 20
                ? powerManager.isInteractive()
                : powerManager.isScreenOn();
    }

    @Override
    protected void onPause() {
        // When onPause is called, it might either be a "real" pause, or a synthetic one from
        // the system that will be immediately resumed.

        if (globalSettings.isSimpleKioskModeEnabled() && isInteractive() && !SettingsActivity.getInSettings()  ) {
            // Work with onStop to ignore user presses of the home key when in kiosk mode.

            // The real work of ignoring the button.
            activityManager.moveTaskToFront(getTaskId(), 0);

            // This may later be suppressed if we're doing "real" starts where we get a
            // system-generated pause "just because".
            postToastOnResume = !recentlyDidStart;

            // Tell onStop that we were just here, making sure it clears after a moment.
            // According to debug log, 1/2 second is enough, but let's assume
            // slower hardware and be generous since it's a human pressing the buttons.
            // Cancel any prior clear so a prior one doesn't clear recentPauseOverride too soon.
            recentPauseOverride = true;
            recentPauseOverrideHandler.removeCallbacksAndMessages(null);
            recentPauseOverrideHandler.postDelayed(()-> recentPauseOverride = false , 2000);

            restoreApp(); // In case something goes awry
        }

        // Call super.onPause() first. It may, among other things, call onResumeFragments(), so
        // calling super.onPause() before controller.onActivityPause() is necessary to ensure that
        // controller.onActivityResumeFragments() is called in the right order.
        isPaused = true;
        super.onPause();
        controller.onActivityPause();
        kioskModeHandler.controlStatusBarExpansion(getApplicationContext(), false);
    }

    @Override
    protected void onStop() {
        if (globalSettings.isSimpleKioskModeEnabled()) {
            postToastOnResume = !recentlyDidStart;

            // The user pressed buttons we're trying to ignore
            boolean kioskUndoStop = isInteractive() && hasWindowFocus();

            // If home (pause) is pressed twice with an interval around 1/2 second, there
            // is a narrow interval (1/10(?) second wide) where it will go directly to Stop state
            // and one or the other of isInteractive and hasWindowFocus (or both) is not set and
            // it gets into a limbo of stopped but not restarting. Thus...
            // If home (pause) was just recently pressed (and we're in kiosk mode),
            // undo stop forcibly.
            kioskUndoStop |= recentPauseOverride;

            // But if we're entering settings, just let it stop.
            kioskUndoStop &= !SettingsActivity.getInSettings();

            if (kioskUndoStop) {
                // We have to let the stop proceed, but then force a restart
                // This works better here (fewer chances of failing) than after all
                // the other stop activity.
                recentlyDidStart = false;
                restoreApp();
            }
        }

        controller.onActivityStop();
        orientationDelegate.onStop();
        kioskModeHandler.onActivityStop();
        super.onStop();
        batteryStatusProvider.stop();

    }

    private Toast lastToast = null;
    private void toastKioskActive() {

        if (lastToast != null) {
            // Cancel prior one (that may prove a no-op) so the times don't accumulate.
            // It'll clear LENGTH_SHORT after the last toast is posted.
            lastToast.cancel();
            lastToast = null;
        }

        lastToast = Toast.makeText(this, R.string.back_suppressed_by_kiosk, Toast.LENGTH_SHORT);
        lastToast.show();
    }

    @Override
    public void onBackPressed() {
        if (globalSettings.isAnyKioskModeEnabled()) {
            toastKioskActive();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Start animations.
            batteryStatusIndicator.startAnimations();

            kioskModeHandler.onFocusGained();
        }
        else {
            // Close every kind of system dialog
            Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            sendBroadcast(closeDialog);
        }

        if (globalSettings.isSimpleKioskModeEnabled()) {
            statusBarCollapser.closeStatusBar();
        }
    }

    @Override
    protected void onDestroy() {
        batteryStatusIndicator.shutdown();
        controller.onActivityDestroy();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // A call with no grantResults means the dialog has been closed without any user decision.
        if (grantResults.length > 0) {
            // Book scan results:
            controller.onRequestPermissionResult(requestCode, permissions, grantResults);
        }
    }

    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TTS_CHECK_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
                if (ttsDeferred != null) {
                    ttsDeferred.setResult(this); // Result value not needed, ignored.
                    ttsDeferred = null;
                }
            } else {
                // missing data, install it
                Intent installIntent = new Intent();
                installIntent.setAction(
                        TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                try {
                    startActivity(installIntent);
                } catch (ActivityNotFoundException e) {
                    Log.w("MainActivity", "No activity to handle Text-to-Speech data installation.");
                    if (ttsDeferred != null) {
                        ttsDeferred.setException(e);
                        ttsDeferred = null;
                    }
                }
            }
        }
    }

    @Override
    @NonNull
    public SimpleFuture<Object> obtainTts() {
        SimpleDeferred<Object> result = ttsDeferred;
        if (ttsDeferred == null) {
            result = new SimpleDeferred<>();
            Intent checkIntent = new Intent();
            checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            try {
                startActivityForResult(checkIntent, TTS_CHECK_CODE);
                ttsDeferred = result;
            } catch (ActivityNotFoundException e) {
                Log.w("MainActivity", "Text-to-Speech not available");
                result.setException(e);
                // ttsDeferred stays unset because the exception is delivered.
            }
        }
        return result;
    }

    // Triggered from an external (PC) app that's not finished yet. Not reachable from
    // within this app.
    private void handleIntent(Intent intent) {
        if (intent != null && KIOSK_MODE_ENABLE_ACTION.equals(intent.getAction())) {
            if (kioskModeSwitcher.isLockTaskPermitted()) {
                boolean enable = intent.getBooleanExtra(ENABLE_EXTRA, false);
                if (globalSettings.isFullKioskModeEnabled() != enable) {
                    globalSettings.setFullKioskModeEnabledNow(enable);
                    kioskModeSwitcher.onFullKioskModeEnabled(enable, this);

                    // For some reason clearing the preferred Home activity only takes effect if the
                    // application exits (finishing the activity doesn't help).
                    // This issue doesn't happen when disabling the kiosk mode from the settings
                    // screen and I'm out of ideas.
                    if (!enable) {
                        new Handler(getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                System.exit(0);
                            }
                        }, 500);
                    }
                }
            }
        }
    }

    // The functions below serve as the last line of defense from too many button
    // pushes. restoreApp is called whenever there's a chance that that's happened.
    // cancelRestore is called when something "good" happens to indicate that normal
    // processing has occurred, and this isn't needed. If the cancellation doesn't
    // occur, a call to forcibly restore the app will occur after a delay. If a
    // cancellation doesn't then occur, it will try one more time to force the issue
    // after a longer delay. Note that the "counting" is done in the program state
    // so there's no issue of local variables being reset in the process.

    private static final int restoreDelay = 2000;
    private static final Handler restoreHandler = new Handler();

    // If the app hasn't started after the delay, force the issue.
    private void restoreApp() {
        cancelRestore(); // Only one restart should be active, so clean up.
        restoreHandler.postDelayed(this::doRestoreApp, restoreDelay);
    }

    // All went well, cancel the forced restart
    private void cancelRestore() {
        restoreHandler.removeCallbacksAndMessages(null);
    }

    // Post another try at cleaning up that won't itself try again
    // and then force the restore.
    private void doRestoreApp() {
        restoreHandler.postDelayed(this::restoreAppWorker, 2*restoreDelay);
        restoreAppWorker();
    }

    // Restart this activity after stop shut it down.
    // (After Andreas Schrade's article on Kiosks)
    private void restoreAppWorker() {
        if (SettingsActivity.getInSettings()) {
            // A switch to settings mode looks like an unexpected pause, so
            // don't actually do anything. (This would be very hard to get
            // right in the main line, and is easy here.)
            cancelRestore(); // The backup call is already posted.
            return;
        }
        Context context = getApplicationContext();
        Intent i = new Intent(context, this.getClass());
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    class StatusBarCollapser {
        // On Oreo and above, it's not possible to disable the status bar in the ways that
        // work on earlier releases. There's an arms-race between folks who want a solid
        // Kiosk mode and the Android team's concerns about security. This is as good as
        // can be done on Oreo. This might not always work.
        // We'll just ignore any failure, and if the user doesn't like it, there are other
        // Kiosk modes available.
        StatusBarCollapser() {
            getStatusBarCollapser();
        }

        private Object statusBarService;
        private Method collapseStatusBar = null;

        @SuppressLint({"WrongConstant", "PrivateApi"})
        private void getStatusBarCollapser() {
            // This bit of code is accessing a function not in the SDK, and thus isn't guaranteed
            // to be supported forever.  However it's been this way since Lollipop.
            //
            // If collapseStatusBar doesn't get set, then we can't suppress the status bar this way,
            // and we'll just ignore it.
            //
            // This requires EXPAND_STATUS_BAR permissions, which is granted from the manifest.

            if (KioskModeHandler.canDrawOverlays(getApplicationContext())) {
                // Don't bother - something else better is doing the job (if the user wants).
                return;
            }

            // The string "statusbar" below is reporting an severe warning (but not an error); we
            // will just ignore that.
            statusBarService = getSystemService("statusbar"); // wrong constant warning
            Class<?> statusBarManager;

            try {
                statusBarManager = Class.forName("android.app.StatusBarManager");
                // Prior to API 17, the method to call is 'collapse()'
                // API 17 onwards, the method to call is `collapsePanels()`
                collapseStatusBar = statusBarManager.getMethod("collapsePanels"); // private api
            }
            catch (Exception e) { // possible ClassNotFound
                // collapseStatusBar remains null
                return;
            }

            collapseStatusBar.setAccessible(true);
        }

        private final Handler collapseNotificationHandler = new Handler();

        private void forceCollapse() {
            // Do the real work.
            try {
                collapseStatusBar.invoke(statusBarService);
            }
            catch (Exception e) {
                collapseStatusBar = null;
            }
        }
        private void redoCollapse() {
            forceCollapse();
            if (hasWindowFocus() || isPaused) return;
            collapseNotificationHandler.postDelayed(this::redoCollapse, 1000);
        }

        private void closeStatusBar() {
            if (collapseStatusBar == null) {
                // Just ignore the situation
                return;
            }

            // Not focused, but not Paused == status bar is active
            if (!hasWindowFocus() && !isPaused) {
                // Get it closed as quickly as possible, but on some releases (seen on API25)
                // the sweet spot is out one second or so, but by that time the focus and paused
                // test claims it's gone, and it really isn't. Yes, ugly. (On later releases
                // it does work to keep checking focus and paused until that's done.)
                // (It appears that focus is changed when the bar is only half removed, and
                // it takes a second try to finish the job.)
                collapseNotificationHandler.postDelayed(this::forceCollapse,  250);
                collapseNotificationHandler.postDelayed(this::forceCollapse,  500);

                collapseNotificationHandler.postDelayed(this::redoCollapse, 1000);
            }
        }
    }
}