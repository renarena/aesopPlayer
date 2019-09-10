package com.donnKey.aesopPlayer.ui.provisioning;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.storage.StorageManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.util.FilesystemUtil;

import net.rdrei.android.dirchooser.DirectoryChooserActivity;
import net.rdrei.android.dirchooser.DirectoryChooserConfig;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

/* A list of candidate books that might be copied to AudioBooks, and tools
   to house-keep that.
 */
public class CandidateFragment extends Fragment {
    @SuppressWarnings("WeakerAccess")
    @Inject public GlobalSettings globalSettings;

    private Provisioning provisioning;
    private CandidateRecyclerViewAdapter recycler;
    static private boolean dirLookupPending = false;

    private final File defaultCandidateDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

    private final Handler handler = new Handler();
    private final Handler checkHandler = new Handler();
    Menu optionsMenu;

    static final int REQUEST_DIR_LOOKUP = 1234;
    private static final String KEY_MOST_RECENT_SOURCE_DIR = "most_recent_source_dir_preference";
    private SharedPreferences preferences;
    private RecyclerView view;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public CandidateFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = (RecyclerView)inflater.inflate(R.layout.fragment_candidate_list, container, false);
        AesopPlayerApplication.getComponent(view.getContext()).inject(this);
        preferences = globalSettings.appSharedPreferences();
        this.provisioning = new ViewModelProvider(Objects.requireNonNull(this.getActivity())).get(Provisioning.class);

        provisioning.candidateDirectory = new File(
                preferences.getString(KEY_MOST_RECENT_SOURCE_DIR,
                    defaultCandidateDirectory.getPath()));
        if (!provisioning.candidateDirectory.exists() || !provisioning.candidateDirectory.isDirectory()) {
            Toast.makeText(getContext(), getString(R.string.warning_toast_using_default_source),Toast.LENGTH_LONG).show();
            provisioning.candidateDirectory = defaultCandidateDirectory;
            provisioning.candidates.clear();

            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(KEY_MOST_RECENT_SOURCE_DIR, provisioning.candidateDirectory.getPath());
            editor.apply();
        }

        provisioning.windowTitle = getString(R.string.fragment_title_add_books);
        provisioning.windowSubTitle = provisioning.candidateDirectory.getPath();

        setHasOptionsMenu(true);

        Context context = view.getContext();
        view.setLayoutManager(new LinearLayoutManager(context));
        recycler = new CandidateRecyclerViewAdapter(provisioning, this);
        view.setAdapter(recycler);

        if (provisioning.candidates.size() <= 0
                || provisioning.candidateDirectory.lastModified() > provisioning.candidatesTimestamp ) {
            // force onResume->startChecker to rebuild the list.
            provisioning.candidatesTimestamp = 0;
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        // If we end up attached to a usb stick for files (via an OnTheGo cable), if that's detached
        // we want to recognize that. I haven't found a decent way to determine if the candidate
        // directory is on usb, but this is what we'd do if we could, anyway. So the only downside
        // is when a usb that isn't in use that gets unplugged while this fragment is running,
        // which should be vanishingly rare, and then this is just a long no-op.
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        Objects.requireNonNull(getContext()).registerReceiver(detachReceiver, filter);

        ActionBar actionBar = ((AppCompatActivity) Objects.requireNonNull(getActivity())).getSupportActionBar();
        Objects.requireNonNull(actionBar);
        actionBar.setTitle(provisioning.windowTitle);
        actionBar.setSubtitle(provisioning.windowSubTitle);

        ((ProvisioningActivity) Objects.requireNonNull(getActivity())).navigation.
                setVisibility(View.VISIBLE);

        ((ProvisioningActivity) Objects.requireNonNull(getActivity())).activeCandidateFragment = this;

        startChecker();
    }

    @Override
    public void onPause() {
        super.onPause();
        Objects.requireNonNull(getContext()).unregisterReceiver(detachReceiver);

        ((ProvisioningActivity) Objects.requireNonNull(getActivity())).activeCandidateFragment = null;
        stopChecker();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.candidate_action_bar, menu);
        this.optionsMenu = menu;

        MenuItem all = menu.findItem(R.id.check_all);
        AppCompatCheckBox allCheckBox = (AppCompatCheckBox) all.getActionView();
        allCheckBox.setText(getString(R.string.action_bar_word_all));

        allCheckBox.setOnCheckedChangeListener((v, b)-> {
                if (v.isPressed()) {
                    setAllSelected(b);
                }

                allCheckBox.setChecked(b);
                // The following otherwise pointless line makes the check box display it's current
                // checked state correctly. On most releases it isn't necessary, but on (my)
                // 4.4.4 (API20) it's needed, and doesn't do what it looks like it does.
                all.setIcon(R.drawable.battery_0);
            }
        );

        MenuItem archiveBox = menu.findItem(R.id.retain);
        archiveBox.setChecked(globalSettings.getRetainBooks());

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
        case R.id.install_books:
            int count = getSelectedCount();
            Resources res = getResources();
            String books = res.getQuantityString(R.plurals.numberOfBooks, count, count);
            new AlertDialog.Builder(Objects.requireNonNull(getContext()))
                    .setTitle(getString(R.string.dialog_title_install_books))
                    .setIcon(R.drawable.ic_launcher)
                    .setMessage(String.format(getString(R.string.dialog_ok_to_install), books))
                    .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> moveAllSelected())
                    .setNegativeButton(android.R.string.no, null)
                    .show();
            return true;

        case R.id.retain:
            new AlertDialog.Builder(Objects.requireNonNull(getContext()))
                    .setTitle(getString(R.string.dialog_title_set_archive_policy))
                    .setIcon(R.drawable.ic_launcher)
                    .setMessage(getString(R.string.dialog_archive_by_copy) )
                    .setPositiveButton(getString(R.string.dialog_policy_yes), (dialog, whichButton) -> {
                        item.setChecked(true);
                        globalSettings.setRetainBooks(true);
                        Objects.requireNonNull(getActivity()).invalidateOptionsMenu(); // see onPrepare below
                    })
                    .setNegativeButton(getString(R.string.dialog_policy_no), (dialog, whichButton) -> {
                        item.setChecked(false);
                        globalSettings.setRetainBooks(false);
                        Objects.requireNonNull(getActivity()).invalidateOptionsMenu(); // see onPrepare below
                    })
                    .show();
            return true;

        case R.id.search_dir:
            // We want to find/load from internal, removable SD, and USB.
            if (Build.VERSION.SDK_INT >= 21) { // L
                // This is the "right way" for L and above, but it's a lot more complicated
                // at the result end.

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.putExtra("android.content.extra.SHOW_ADVANCED",true);
                // DocumentsContract.EXTRA_INITIAL_URI: would be useful, but requires API26.
                // (It also requires a URI as argument.) No workaround discoverable.
                startActivityForResult(intent, REQUEST_DIR_LOOKUP);
            }
            else {
                // Down-level; works well enough on those
                final Intent chooserIntent = new Intent (Objects.requireNonNull(getContext()).getApplicationContext(),
                        DirectoryChooserActivity.class);

                final DirectoryChooserConfig config = DirectoryChooserConfig.builder()
                        .newDirectoryName("unused")
                        .initialDirectory(provisioning.candidateDirectory.getPath())
                        .allowReadOnlyDirectory(true)
                        .allowNewDirectoryNameModification(true) // does initialDir - false->usual place
                        .build();

                chooserIntent.putExtra(DirectoryChooserActivity.EXTRA_CONFIG, config);
                startActivityForResult(chooserIntent, REQUEST_DIR_LOOKUP);
            }
            dirLookupPending = true;

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Sometimes we get a UUID that isn't in mounts; this us ugly, but converts it to a real path.
    // We need reflection for K, L, M, at least.
    // After Anonymous in https://stackoverflow.com/questions/34927748/android-5-0-documentfile-from-tree-uri
    private String pathFromUUID(String UUID) {
        try {
            StorageManager mStorageManager =
                    (StorageManager) Objects.requireNonNull(getContext()).getSystemService(Context.STORAGE_SERVICE);
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getUuid = storageVolumeClazz.getMethod("getUuid");
            //noinspection JavaReflectionMemberAccess
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Object result = getVolumeList.invoke(mStorageManager);
            final int length = Array.getLength(result);

            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String uuid = (String) getUuid.invoke(storageVolumeElement);

                if (UUID.equals(uuid) ) {
                    return (String)getPath.invoke(storageVolumeElement);
                }
            }
        } catch (Exception e) {
            // Nothing, return null
        }
        return null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (!dirLookupPending) {
            // ProvisioningActivity makes a call to this "manually". This should be (and frequently
            // is) being called by the system, but in some cases it doesn't happen and this doesn't
            // work (Bug seen on Oreo). So, to prevent being called twice...
            return;
        }

        // There's a fragment tag in the top half.
        if ((requestCode&0xFFFF) == REQUEST_DIR_LOOKUP) {
            dirLookupPending = false;
            File pathToDir = null;
            label:
            //noinspection ConstantConditions
            do {
                if (Build.VERSION.SDK_INT >= 21) { // L
                    // This is the "right way" for L and above, but it's a lot more complicated
                    // because we have to work around DocumentProvider "stuff".
                    if (data == null) {
                        // Cancel
                        return;
                    }

                    Uri treeUri = data.getData();
                    if (treeUri == null) {
                        return;
                    }

                    String partialPath = treeUri.getPath();
                    if (partialPath == null) {
                        return;
                    }
                    String[] parts1 = partialPath.split(":");

                    // Get dir name
                    String dirName;
                    if (parts1.length == 1) {
                        dirName = "";
                    }
                    else if (parts1.length == 2) {
                        dirName = parts1[1];
                    }
                    else {
                        break;
                    }

                    // Figure out the path: the special paths below have to be translated
                    switch (parts1[0]) {
                    case "/tree/primary":
                        File defaultStorage = Environment.getExternalStorageDirectory();
                        pathToDir = new File(defaultStorage, dirName);
                        break;
                    case "/tree/raw":
                        // We get this for subdirectories when using "Downloads" option
                        // (Download itself is below.)
                        pathToDir = new File(dirName);
                        break;
                    case "/tree/downloads":
                        File downloadsStorage = Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS);
                        pathToDir = new File(downloadsStorage, dirName);
                        break;
                    default:
                        // Probably the external (removable) SD
                        String[] parts2 = parts1[0].split("/");
                        if (parts2.length != 3) {
                            break label;
                        }
                        String mntName = parts2[2];

                        // The mount directory we're looking for is probably /storage,
                        // but to be sure, use what's in roots with the same name.
                        List<File> roots = FilesystemUtil.fileSystemRoots(getContext());
                        for (File f : roots) {
                            if (f.getName().equals(mntName)) {
                                pathToDir = f;
                                break;
                            }
                        }
                        // Sometimes mntName is a UUID but that isn't in the names in roots.
                        if (pathToDir == null) {
                            String path = pathFromUUID(mntName);
                            if (path != null) {
                                pathToDir = new File(path);
                            }
                        }
                        if (pathToDir == null) {
                            break label;
                        }
                        pathToDir = new File(pathToDir, dirName);
                        break;
                    }
                }
                else {
                    // Down-level; works well enough on those
                    if (resultCode == DirectoryChooserActivity.RESULT_CODE_DIR_SELECTED) {
                        pathToDir = new File(
                                Objects.requireNonNull(data).getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR));
                    }
                    else {
                        return;
                    }
                }
            } while (false);

            if (pathToDir == null || !pathToDir.exists()) {
                // (We'll allow non-writable so we can pull from RO places.)
                Toast.makeText(getContext(),
                        getString(R.string.warning_toast_directory_not_available),
                        Toast.LENGTH_LONG).show();
                return;
            }

            provisioning.candidateDirectory = pathToDir;

            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(KEY_MOST_RECENT_SOURCE_DIR, pathToDir.getPath());
            editor.apply();

            // EVERYTHING changed... start from scratch.
            provisioning.candidates.clear();
            resetCandidates();
        }
    }

    private void resetCandidates() {
        Objects.requireNonNull(getActivity()).getSupportFragmentManager().beginTransaction()
                .replace(R.id.inventory_container, new CandidateFragment())
                .commitNowAllowingStateLoss();
    }

    @UiThread
    void buildCandidateList() {
        if (provisioning.candidates.size() > 0) {
            return;
        }
        Thread t = new Thread(this::buildCandidateList_Task);
        t.setPriority(Thread.MIN_PRIORITY); // for the UI thread's benefit
        t.start();
    }

    @UiThread
    private void buildCallback() {
        // Ideally this would be smarter and update each row as it changes, but there's some
        // synchronization problem that causes it to throw() that the positions are incorrect
        // when trying to recycle ViewHandlers (from previous passes through here).
        // I think it's "their" bug (how a ViewHandler position can be one more than
        // size() is not obvious). The performance is adequate even on slow machines this way --
        // the limiting factor is file operations, not the UI thread.
        synchronized (provisioning.candidates) {
            recycler.notifyDataSetChanged();
        }
        view.scrollToPosition(provisioning.candidates.size()-1);
    }

    @WorkerThread
    private void buildCandidateList_Task() {
        stopChecker();
        provisioning.buildCandidateList_Task(() -> {
            try {
                Objects.requireNonNull(getActivity()).runOnUiThread(this::buildCallback);
            } catch (Exception e) {
                // ignore (probably screen rotation)
            }
        });
        startChecker();
    }

    private void setAllSelected(boolean b)
    {
        for (Provisioning.Candidate candidate : provisioning.candidates) {
            if (!candidate.collides) {
                candidate.isSelected = b;
            }
        }
        recycler.notifyItemRangeChanged(0, provisioning.candidates.size());
    }

    private int getSelectedCount() {
        int count = 0;
        for (Provisioning.Candidate candidate : provisioning.candidates) {
            if (candidate.isSelected) count++;
        }
        return count;
    }

    @UiThread
    private void moveAllSelected() {
        ((ProvisioningActivity) Objects.requireNonNull(getActivity())).moveAllSelected();
    }

    // While we're on top, look to see if the directory changes (the user might add more files).
    // Unhappily I can't get FileObserver to report directory changes (but it will report
    // changes/reads to files in the directory!)
    void startChecker() {
        checkHandler.removeCallbacksAndMessages(null);
        checkHandler.postDelayed(this::checkChanged, 2000);
    }

    void stopChecker() {
        checkHandler.removeCallbacksAndMessages(null);
    }

    private void checkChanged()
    {
        long t = provisioning.candidateDirectory.lastModified();
        if (t > provisioning.candidatesTimestamp) {
            provisioning.candidates.clear();
            provisioning.candidatesTimestamp = t;
            buildCandidateList();
            // Return from here: buildCandidateList will restart posting
            return;
        }

        checkHandler.postDelayed(this::checkChanged, 2000);
    }

    // Usb was detached
    private final BroadcastReceiver detachReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if(Objects.requireNonNull(intent.getAction()).equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                // It takes the system a moment to "realize" the directory is gone, and
                // we rely on that to switch directories and refresh.
                handler.postDelayed(()->resetCandidates(), 1000);
            }
        }
    };

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);

        MenuItem installBooks = menu.findItem(R.id.install_books);
        if (globalSettings.getRetainBooks() || !provisioning.candidateDirectory.canWrite()) {
            installBooks.setIcon(R.drawable.copy_book);
        }
        else {
            installBooks.setIcon(R.drawable.move_book);
        }
    }

    void notifyDataSetChanged() {
        recycler.notifyDataSetChanged();
    }
}
