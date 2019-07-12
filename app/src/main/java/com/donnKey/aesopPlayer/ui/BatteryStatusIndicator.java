package com.donnKey.aesopPlayer.ui;

import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import com.donnKey.aesopPlayer.battery.BatteryStatus;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.battery.ChargeLevel;
import com.donnKey.aesopPlayer.events.BatteryStatusChangeEvent;

import java.util.EnumMap;

import de.greenrobot.event.EventBus;

public class BatteryStatusIndicator {

    private final ImageView indicatorView;
    private final EventBus eventBus;

    private static final EnumMap<ChargeLevel, Integer> BATTERY_DRAWABLE =
            new EnumMap<>(ChargeLevel.class);

    private static final EnumMap<ChargeLevel, Integer> CHARGING_DRAWABLE =
            new EnumMap<>(ChargeLevel.class);

    static {
        BATTERY_DRAWABLE.put(ChargeLevel.CRITICAL, R.drawable.battery_critical);
        BATTERY_DRAWABLE.put(ChargeLevel.LEVEL_1, R.drawable.battery_red_1);

        CHARGING_DRAWABLE.put(ChargeLevel.CRITICAL, R.drawable.battery_charging_0);
        CHARGING_DRAWABLE.put(ChargeLevel.LEVEL_1, R.drawable.battery_charging_0);
        CHARGING_DRAWABLE.put(ChargeLevel.LEVEL_2, R.drawable.battery_charging_1);
        CHARGING_DRAWABLE.put(ChargeLevel.LEVEL_3, R.drawable.battery_charging_2);
        CHARGING_DRAWABLE.put(ChargeLevel.FULL, R.drawable.battery_3);
    }

    public BatteryStatusIndicator(ImageView indicatorView, EventBus eventBus) {
        this.indicatorView = indicatorView;
        this.eventBus = eventBus;
        this.eventBus.registerSticky(this);
    }

    public void startAnimations() {
        Drawable indicatorDrawable = indicatorView.getDrawable();
        if (indicatorDrawable instanceof AnimationDrawable)
            ((AnimationDrawable) indicatorDrawable).start();
    }

    public void shutdown() {
        // TODO: find an automatic way to unregister
        eventBus.unregister(this);
    }

    private void updateBatteryStatus(BatteryStatus batteryStatus) {
        Integer statusDrawable = batteryStatus.isCharging
                ? CHARGING_DRAWABLE.get(batteryStatus.chargeLevel)
                : BATTERY_DRAWABLE.get(batteryStatus.chargeLevel);

        if (statusDrawable == null) {
            indicatorView.setVisibility(View.GONE);
        } else {
            if (indicatorView.getVisibility() != View.VISIBLE)
                indicatorView.setVisibility(View.VISIBLE);
            indicatorView.setImageResource(statusDrawable);
            startAnimations();
        }
    }

    @SuppressWarnings("unused")
    public void onEvent(BatteryStatusChangeEvent batteryEvent) {
        updateBatteryStatus(batteryEvent.batteryStatus);
    }
}
