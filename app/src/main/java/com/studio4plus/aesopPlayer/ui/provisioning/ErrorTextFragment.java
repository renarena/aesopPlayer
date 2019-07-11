package com.studio4plus.aesopPlayer.ui.provisioning;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.common.base.Preconditions;
import com.studio4plus.aesopPlayer.R;

import java.util.Objects;

public class ErrorTextFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Provisioning provisioning = ViewModelProviders.of(Objects.requireNonNull(getActivity())).get(Provisioning.class);
        View view = inflater.inflate(R.layout.error_text_fragment, container, false);

        ActionBar actionBar = ((AppCompatActivity) Objects.requireNonNull(getActivity())).getSupportActionBar();
        Preconditions.checkNotNull(actionBar);
        actionBar.setTitle(provisioning.windowTitle);
        actionBar.setSubtitle(provisioning.errorTitle);

        ArrayAdapter<Provisioning.ErrorInfo> adapter = new ArrayAdapter<>(Objects.requireNonNull(getContext()),
                android.R.layout.simple_list_item_1, provisioning.errorLogs);
        ListView listView = view.findViewById(R.id.string_list);
        listView.setAdapter(adapter);

        ((ProvisioningActivity) Objects.requireNonNull(getActivity())).navigation.
                setVisibility(View.GONE);

        return view;
    }
}
