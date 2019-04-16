package com.studio4plus.homerplayer.ui.provisioning;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import de.greenrobot.event.EventBus;

import android.os.Handler;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.HomerPlayerApplication;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.events.MediaStoreUpdateEvent;
import com.studio4plus.homerplayer.model.AudioBook;
import com.studio4plus.homerplayer.ui.KioskModeHandler;
import com.studio4plus.homerplayer.ui.OrientationActivityDelegate;
import com.studio4plus.homerplayer.ui.settings.SettingsActivity;

import java.util.Objects;

import javax.inject.Inject;

import static com.studio4plus.homerplayer.HomerPlayerApplication.getAppContext;

public class ProvisioningActivity extends AppCompatActivity
{
    @SuppressWarnings("WeakerAccess")
    @Inject
    public GlobalSettings globalSettings;
    public final static String EXTRA_TARGET_FRAGMENT = "com.studio4plus.homerplayer.RETURN_MESSAGE";
    private final static int ACTIVITY_REQUEST_PROVISIONING = 1235;

    private OrientationActivityDelegate orientationDelegate;

    private Provisioning provisioning;
    BottomNavigationView navigation;

    public ProvisioningActivity() {
        // required stub constructor
    }

    CandidateFragment activeCandidateFragment;
    InventoryItemFragment activeInventoryFragment;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        HomerPlayerApplication.getComponent(getAppContext()).inject(this);
        super.onCreate(savedInstanceState);

        provisioning = ViewModelProviders.of(this).get(Provisioning.class);

        orientationDelegate = new OrientationActivityDelegate(this, globalSettings);

        setContentView(R.layout.activity_provisioning);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(this::onNavigationItemSelectedListener);
        navigation.setItemIconTintList(null); // enable my control of icon color

        if (provisioning.currentFragment == 0) {
            navigation.setSelectedItemId(R.id.navigation_settings);
        }

        ActionBar actionBar = getSupportActionBar();
        Preconditions.checkNotNull(actionBar);
        actionBar.setDisplayHomeAsUpEnabled(true);

        // So we can get out to fix things without too much pain. A very long press.
        BottomNavigationItemView quickExit = navigation.findViewById(R.id.navigation_quick_exit);
        quickExit.setClickable(true);
        quickExit.setOnTouchListener(this::quickExitTouchListenerThunk);
    }

    // Used to tell MainActivity that we're here on purpose.
    private static boolean doingProvisioning;

    @Override
    protected void onStart() {
        doingProvisioning = true;
        super.onStart();
        orientationDelegate.onStart();
    }

    @Override
    protected void onStop() {
        doingProvisioning = false;
        orientationDelegate.onStop();
        super.onStop();
    }

    static public boolean getInProvisioning() {
        return doingProvisioning;
    }

    private final Handler h = new Handler();

    private boolean onNavigationItemSelectedListener (MenuItem item) {
        switch (item.getItemId()) {
        case R.id.navigation_inventory:
            provisioning.currentFragment = item.getItemId();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.inventory_container, new InventoryItemFragment())
                    .commit();
            return true;

        case R.id.navigation_candidates:
            provisioning.currentFragment = item.getItemId();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.inventory_container, new CandidateFragment())
                    .commit();
            return true;

        case R.id.navigation_settings:
            Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
            startActivityForResult(intent, ACTIVITY_REQUEST_PROVISIONING);
            return true;

        case R.id.navigation_quick_exit:
            quickExitDialog(this);
            // Delayed so "currently active" size is restored. Support caches seem to interfere;
            // 100ms works only intermittently.
            h.postDelayed(()->navigation.setSelectedItemId(provisioning.currentFragment), 500);
            return true;
        }
        return false;
    }

    // Make the back button pop the stack rather than go all the way back to MainActivity.
    // The back button in the sub-fragments is owned by this fragment, so the code below goes here.
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            FragmentManager fragmentManager = getSupportFragmentManager();
            if (fragmentManager.getBackStackEntryCount() > 0 ){
                fragmentManager.popBackStack();
                return true;
            }
            break;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void switchTo(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.inventory_container, fragment)
            .addToBackStack(null)
            .commit();
    }

    public void updateTitle(Provisioning.Candidate item) {
        provisioning.fragmentParameter = item;
        switchTo(new TitleEditFragment());
    }

    public void updateTitle(AudioBook book) {
        provisioning.fragmentParameter = book;
        switchTo(new TitleEditFragment());
    }

    public void updateProgress(AudioBook book) {
        provisioning.fragmentParameter = book;
        switchTo(new PositionEditFragment());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ACTIVITY_REQUEST_PROVISIONING) {
            if (resultCode == RESULT_OK) {
                int newViewId = Objects.requireNonNull(data).getIntExtra(EXTRA_TARGET_FRAGMENT, 0);
                navigation.setSelectedItemId(newViewId);
                return;
            }
            else if (resultCode == RESULT_CANCELED) {
                finish();
                return;
            }
        }

        if ((requestCode&0xFFFF) == CandidateFragment.REQUEST_DIR_LOOKUP) {
            // onActivityResult in the fragment should be being called, but it's not, sometimes.
            // So do it here (callee will have to look for double calls.) (Seen on Oreo, not on others).
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.inventory_container);
            Preconditions.checkNotNull(fragment);
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void postResults(String title) {
        if (provisioning.errorLogs.size() <= 0) {
            return;
        }
        CheckLoop:
        //noinspection ConstantConditions,LoopStatementThatDoesntLoop
        do {
            for (Provisioning.ErrorInfo e : provisioning.errorLogs) {
                switch (e.severity) {
                case INFO:
                    continue;
                case MILD:
                case SEVERE:
                    break CheckLoop;
                }
            }
            return;
        } while(false);

        provisioning.errorTitle = title;
        switchTo(new ErrorTextFragment());
    }

    private boolean quickExitTouchListenerThunk(View v, MotionEvent e) {
        // This just to make the compiler happy: doesn't like 'static' at point of call
        return quickExitTouchListener(v, e);
    }

    static public boolean quickExitTouchListener(View v, MotionEvent e) {
        long duration = e.getEventTime() - e.getDownTime();
        if (e.getAction() == MotionEvent.ACTION_MOVE
            || e.getAction() == MotionEvent.ACTION_UP) {
            if (duration > 3000) {
                v.setOnTouchListener(null);
                KioskModeHandler.forceExit((AppCompatActivity)v.getContext());
                return true;
            }
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            v.performClick();
            return true;
        }
        return false;
    }

    static public void quickExitDialog(Context c) {
        new AlertDialog.Builder(c)
            .setTitle(c.getString(R.string.dialog_title_quick_exit))
            .setIcon(R.mipmap.ic_launcher)
            .setMessage(c.getString(R.string.dialog_message_quick_exit))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private boolean retainBooks;
    private Toast lastToast;
    @UiThread
    private void postMoveProgress_Inner(Provisioning.ProgressKind kind, String message) {
        switch (kind) {
        case SEND_TOAST: {
            if (lastToast != null)
                lastToast.cancel();
            lastToast = Toast.makeText(getAppContext(), message, Toast.LENGTH_SHORT);
            lastToast.show();
            break;
        }
        case FILESYSTEMS_FULL: {
            new AlertDialog.Builder(getAppContext())
                    .setTitle(getString(R.string.error_dialog_title_file_system_full))
                    .setIcon(R.mipmap.ic_launcher)
                    .setMessage(getString(R.string.error_dialog_file_system_full))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            break;
        }
        case BOOK_DONE: {
            if (activeCandidateFragment != null) activeCandidateFragment.notifyDataSetChanged();
            EventBus.getDefault().post(new MediaStoreUpdateEvent());
            break;
        }
        case ALL_DONE: {
            EventBus.getDefault().post(new MediaStoreUpdateEvent());
            if (!retainBooks) {
                // Rebuild everything (if needed)
                if (activeCandidateFragment != null) {
                    activeCandidateFragment.buildCandidateList();
                    activeCandidateFragment.notifyDataSetChanged();
                }
            }
            postResults(getAppContext().getString(R.string.fragment_title_errors_new_books));
            break;
        }
        }
    }

    @WorkerThread
    private void postMoveProgress(Provisioning.ProgressKind kind, String message) {
        runOnUiThread(()->postMoveProgress_Inner(kind,message));
    }

    @WorkerThread
    private void moveAllSelected_Task() {
        if (activeCandidateFragment != null) activeCandidateFragment.stopChecker();
        provisioning.moveAllSelected_Task(this::postMoveProgress, retainBooks);
        if (activeCandidateFragment != null) activeCandidateFragment.startChecker();
    }

    @UiThread
    void moveAllSelected() {
        retainBooks = globalSettings.getRetainBooks() && provisioning.candidateDirectory.canWrite();
        Thread t = new Thread(this::moveAllSelected_Task);
        t.start();
    }

    @UiThread
    private void postDeleteProgress_Inner(Provisioning.ProgressKind kind, @SuppressWarnings("unused") String message) {
        switch (kind) {
        case SEND_TOAST:
        case FILESYSTEMS_FULL: {
            throw new RuntimeException("Bad Progress Value");
        }
        case BOOK_DONE: {
            if (activeInventoryFragment != null) activeInventoryFragment.notifyDataSetChanged();
            break;
        }
        case ALL_DONE: {
            EventBus.getDefault().post(new MediaStoreUpdateEvent());
            provisioning.buildBookList();
            if (activeInventoryFragment != null) {
                activeInventoryFragment.refreshSubtitle();
                activeInventoryFragment.notifyDataSetChanged();
            }
            postResults(getAppContext().getString(R.string.fragment_title_errors_deleting_books));
            break;
        }
        }
    }

    @WorkerThread
    private void postDeleteProgress(Provisioning.ProgressKind kind, String message) {
        runOnUiThread(()->postDeleteProgress_Inner(kind,message));
    }

    @UiThread
    void deleteAllSelected() {
        Thread t = new Thread(this::deleteAllSelected_Task);
        t.start();
    }

    private void deleteAllSelected_Task() {
        provisioning.deleteAllSelected_Task(this::postDeleteProgress);
    }
}
