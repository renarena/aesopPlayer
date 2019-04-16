package com.studio4plus.homerplayer.ui.settings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.appcompat.widget.Toolbar;

import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.HomerPlayerApplication;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.events.SettingsEnteredEvent;
import com.studio4plus.homerplayer.service.DeviceMotionDetector;
import com.studio4plus.homerplayer.ui.ActivityComponent;
import com.studio4plus.homerplayer.ui.ActivityModule;
import com.studio4plus.homerplayer.ui.DaggerActivityComponent;
import com.studio4plus.homerplayer.ui.KioskModeHandler;
import com.studio4plus.homerplayer.ui.OrientationActivityDelegate;
import com.studio4plus.homerplayer.ui.provisioning.ProvisioningActivity;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

public class SettingsActivity
        extends AppCompatActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback {

    private static final int BLOCK_TIME_MS = 500;

    private Handler mainThreadHandler;
    private Runnable unblockEventsTask;
    private OrientationActivityDelegate orientationDelegate;
    private BottomNavigationView navigation;

    @Inject public EventBus eventBus;
    @Inject public GlobalSettings globalSettings;
    @Inject public KioskModeHandler kioskModeHandler;
    private static boolean enteringSettings;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.settings_activity);
        super.onCreate(savedInstanceState);
        ActivityComponent activityComponent = DaggerActivityComponent.builder()
                .applicationComponent(HomerPlayerApplication.getComponent(this))
                .activityModule(new ActivityModule(this))
                .build();
        activityComponent.inject(this);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        Preconditions.checkNotNull(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(true);

        navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(this::onNavigationItemSelectedListener);
        navigation.setItemIconTintList(null); // enable my control of icon color
        navigation.setSelectedItemId(R.id.navigation_settings); // for side-effect of item size change

        kioskModeHandler.setKeepNavigation(true);
        orientationDelegate = new OrientationActivityDelegate(this, globalSettings);

        // Display the fragment as the main content.
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_container, new MainSettingsFragment())
                .commit();

        mainThreadHandler = new Handler(getMainLooper());

        // So we can get out to fix things without too much pain. A very long press.
        BottomNavigationItemView quickExit = navigation.findViewById(R.id.navigation_quick_exit);
        quickExit.setClickable(true);
        quickExit.setOnTouchListener(this::quickExitTouchListenerThunk);
    }

    private final Handler h = new Handler();

    private boolean onNavigationItemSelectedListener (MenuItem item) {
        switch (item.getItemId()) {
        case R.id.navigation_inventory:
        case R.id.navigation_candidates:
            Intent intent = new Intent(getApplicationContext(), ProvisioningActivity.class);
            intent.putExtra(ProvisioningActivity.EXTRA_TARGET_FRAGMENT,item.getItemId());
            setResult(RESULT_OK,intent);
            finish();
            return true;

        case R.id.navigation_settings:
            // No-op
            return true;

        case R.id.navigation_quick_exit:
            // Delayed so "currently active" icon/text size is restored. Support caches seem to interfere;
            // 200ms works only intermittently.
            h.postDelayed(()->navigation.setSelectedItemId(R.id.navigation_settings), 500);
            ProvisioningActivity.quickExitDialog(this);
            return true;
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        orientationDelegate.onStart();
        blockEventsOnStart();
        eventBus.post(new SettingsEnteredEvent());
        kioskModeHandler.onActivityStart();
        enteringSettings = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        DeviceMotionDetector.suspend();
    }

    static public boolean getInSettings() {
        return enteringSettings;
    }


    @Override
    protected void onStop() {
        enteringSettings = false;
        super.onStop();
        orientationDelegate.onStop();
        cancelBlockEventOnStart();
        kioskModeHandler.onActivityStop();
        DeviceMotionDetector.resume();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            kioskModeHandler.onFocusGained();
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        // Instantiate the new Fragment
        final Bundle args = pref.getExtras();
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(), pref.getFragment(), args);
        Preconditions.checkState(fragment instanceof BaseSettingsFragment);

        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);
        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_container, fragment)
                .addToBackStack(null)
                .commit();
        return true;
    }

    @Override
    public boolean onPreferenceDisplayDialog(
            @NonNull PreferenceFragmentCompat preferenceFragmentCompat, Preference preference) {
        if (preference instanceof ConfirmDialogPreference) {
            DialogFragment dialogFragment =
                    ConfirmDialogFragmentCompat.newInstance(preference.getKey());
            dialogFragment.setTargetFragment(preferenceFragmentCompat, 0);
            dialogFragment.show(getSupportFragmentManager(), "CONFIRM_DIALOG");
            return true;
        }
        if (preference instanceof DurationDialogPreference) {
            DialogFragment dialogFragment =
                    DurationDialogFragmentCompat.newInstance(preference.getKey());
            dialogFragment.setTargetFragment(preferenceFragmentCompat, 0);
            dialogFragment.show(getSupportFragmentManager(), "DURATION_DIALOG");
            return true;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void blockEventsOnStart() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        unblockEventsTask = () -> {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            unblockEventsTask = null;
        };
        mainThreadHandler.postDelayed(unblockEventsTask, BLOCK_TIME_MS);
    }

    private void cancelBlockEventOnStart() {
        if (unblockEventsTask != null)
            mainThreadHandler.removeCallbacks(unblockEventsTask);
    }

    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data) {
        kioskModeHandler.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        kioskModeHandler.onRequestPermissionResult(requestCode, permissions, grantResults);
    }

    private boolean quickExitTouchListenerThunk(View v, MotionEvent e) {
        return ProvisioningActivity.quickExitTouchListener(v, e);
    }
}
