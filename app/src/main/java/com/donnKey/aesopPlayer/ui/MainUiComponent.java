package com.donnKey.aesopPlayer.ui;

import androidx.appcompat.app.AppCompatActivity;

public interface MainUiComponent {
    @SuppressWarnings("unused")
    AppCompatActivity activity();
    void inject(MainActivity mainActivity);
}
