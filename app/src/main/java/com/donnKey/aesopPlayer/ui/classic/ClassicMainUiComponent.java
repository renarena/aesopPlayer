package com.donnKey.aesopPlayer.ui.classic;

import com.donnKey.aesopPlayer.ApplicationComponent;
import com.donnKey.aesopPlayer.ui.ActivityModule;
import com.donnKey.aesopPlayer.ui.MainUiComponent;
import com.donnKey.aesopPlayer.ui.ActivityScope;

import dagger.Component;

@ActivityScope
@Component(dependencies = ApplicationComponent.class,
        modules ={ActivityModule.class, ClassicMainUiModule.class})
public
interface ClassicMainUiComponent extends MainUiComponent {
}
