package com.donnKey.aesopPlayer.ui.classic;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.crashlytics.android.Crashlytics;
import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.ApplicationComponent;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.ui.InitUi;
import com.donnKey.aesopPlayer.ui.UiControllerInit;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;


@SuppressWarnings("FieldCanBeLocal")
public class ClassicInitUi extends Fragment implements InitUi {

    @SuppressWarnings("WeakerAccess")
    @Inject public GlobalSettings globalSettings;

    @SuppressWarnings("unused")
    private UiControllerInit controller;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view;

        // Trivial screen with our logo just to avoid a long black screen if there are
        // a lot of books.
        view = inflater.inflate(R.layout.fragment_init, container, false);
        ApplicationComponent component = AesopPlayerApplication.getComponent(view.getContext());
        component.inject(this);

        return view;
    }

    @Override
    public void initWithController(@NonNull UiControllerInit controller) {
        this.controller = controller;
    }

    @Override
    public void onResume() {
        super.onResume();
        Crashlytics.log("UI: ClassicInit fragment resumed");
    }
}
