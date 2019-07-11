package com.studio4plus.aesopPlayer.ui.classic;

import com.studio4plus.aesopPlayer.ApplicationComponent;
import com.studio4plus.aesopPlayer.ui.ActivityModule;
import com.studio4plus.aesopPlayer.ui.MainUiComponent;
import com.studio4plus.aesopPlayer.ui.ActivityScope;

import dagger.Component;

@ActivityScope
@Component(dependencies = ApplicationComponent.class,
        modules ={ActivityModule.class, ClassicMainUiModule.class})
interface ClassicMainUiComponent extends MainUiComponent {
}
