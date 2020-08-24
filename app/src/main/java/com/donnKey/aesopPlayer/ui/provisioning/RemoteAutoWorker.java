/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Donn S. Terry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.donnKey.aesopPlayer.ui.provisioning;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.BuildConfig;
import com.donnKey.aesopPlayer.GlobalSettings;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import static com.donnKey.aesopPlayer.AesopPlayerApplication.getAppContext;

public class RemoteAutoWorker extends Worker {
    @Inject
    public GlobalSettings globalSettings;

    private long interval = TimeUnit.MINUTES.toMillis(5);
    final RemoteAuto remoteAuto;

    public RemoteAutoWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        AesopPlayerApplication.getComponent(getAppContext()).inject(this);
        remoteAuto = new RemoteAuto();
        if (BuildConfig.DEBUG) {
            interval = TimeUnit.SECONDS.toMillis(10);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        pollLoop();
        return Result.success();
    }

    @SuppressLint("UsableSpace")
    @WorkerThread
    public void pollLoop() {
        while (true)
        {
            if (isStopped()) {
                // Give up until we get another start
                return;
            }

            remoteAuto.pollSources();

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                // ignored
            }
        }
    }
}
