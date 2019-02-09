package com.studio4plus.homerplayer.ui.classic;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.crashlytics.android.Crashlytics;
import com.studio4plus.homerplayer.ApplicationComponent;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.HomerPlayerApplication;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.ui.InitUi;
import com.studio4plus.homerplayer.ui.UiControllerInit;

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
        ApplicationComponent component = HomerPlayerApplication.getComponent(view.getContext());
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
