package com.studio4plus.homerplayer.ui;

import android.support.v7.app.AppCompatActivity;

public interface MainUiComponent {
    @SuppressWarnings("unused")
    AppCompatActivity activity();
    void inject(MainActivity mainActivity);
}
