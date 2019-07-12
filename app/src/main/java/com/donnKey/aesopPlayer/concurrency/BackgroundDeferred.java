package com.donnKey.aesopPlayer.concurrency;

import android.os.Handler;
import androidx.annotation.NonNull;

import java.util.concurrent.Callable;

class BackgroundDeferred<V> extends BaseDeferred<V> implements Runnable {

    private final @NonNull Callable<V> task;
    private final @NonNull Handler mainThreadHandler;

    BackgroundDeferred(@NonNull Callable<V> task, @NonNull Handler mainThreadHandler) {
        this.task = task;
        this.mainThreadHandler = mainThreadHandler;
    }

    @Override
    public void run() {
        try {
            final @NonNull V newResult = task.call();
            mainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    setResult(newResult);
                }
            });
        } catch (final Exception e) {
            mainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    setException(e);
                }
            });
        }
    }
}
