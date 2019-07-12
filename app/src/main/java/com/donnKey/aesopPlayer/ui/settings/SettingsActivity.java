package com.donnKey.aesopPlayer.ui.settings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.Drawable;
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
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.KioskModeSwitcher;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.events.SettingsEnteredEvent;
import com.donnKey.aesopPlayer.service.DeviceMotionDetector;
import com.donnKey.aesopPlayer.ui.ActivityComponent;
import com.donnKey.aesopPlayer.ui.ActivityModule;
import com.donnKey.aesopPlayer.ui.DaggerActivityComponent;
import com.donnKey.aesopPlayer.ui.KioskModeHandler;
import com.donnKey.aesopPlayer.ui.OrientationActivityDelegate;
import com.donnKey.aesopPlayer.ui.provisioning.ProvisioningActivity;

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
    @Inject public KioskModeSwitcher kioskModeSwitcher;
    private static boolean enteringSettings;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.settings_activity);
        super.onCreate(savedInstanceState);
        ActivityComponent activityComponent = DaggerActivityComponent.builder()
                .applicationComponent(AesopPlayerApplication.getComponent(this))
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
    }

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

        case R.id.navigation_maintenance:
            boolean enabled = !globalSettings.isMaintenanceMode();
            setMenuItemProperties(this, item,
                    enabled ? R.drawable.ic_settings_red_24dp : R.drawable.ic_settings_redish_24dp,
                    enabled ? android.R.color.white : R.color.medium_dark_grey);
            kioskModeSwitcher.setKioskMaintenanceMode(this, enabled);
            globalSettings.setMaintenanceMode(enabled);
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

        boolean enabled = globalSettings.isMaintenanceMode();
        setMenuItemProperties(this, navigation.getMenu().getItem(3),
                enabled ? R.drawable.ic_settings_red_24dp : R.drawable.ic_settings_redish_24dp,
                enabled ? android.R.color.white : R.color.medium_dark_grey);

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
        if (preference instanceof KioskSelectionPreference) {
            DialogFragment dialogFragment =
                    KioskSelectionFragmentCompat.newInstance(preference.getKey());
            dialogFragment.setTargetFragment(preferenceFragmentCompat, 0);
            dialogFragment.show(getSupportFragmentManager(), "LIST_DIALOG");
            return true;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
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

    static public void setMenuItemProperties(AppCompatActivity activity,
                                             MenuItem item,
                                             int resIconDrawable, int resColor) {
        int id = item.getItemId();

        BottomNavigationItemView m = activity.findViewById(id);
        TextView t1 = m.findViewById(R.id.smallLabel);
        TextView t2 = m.findViewById(R.id.largeLabel);
        t1.setTextColor(activity.getResources().getColor(resColor));
        t2.setTextColor(activity.getResources().getColor(resColor));

        Drawable d = VectorDrawableCompat.create(activity.getResources(), resIconDrawable, null);
        //Drawable d = activity.getResources().getDrawable(resIconDrawable);
        item.setIcon(d);
    }
}
