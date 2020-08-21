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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import static com.donnKey.aesopPlayer.AesopPlayerApplication.getAppContext;

public class RemoteAutoWorker extends Worker {
    @Inject
    public GlobalSettings globalSettings;

    // ?????????????? Make these times sensible for real life, not testing
    // Conditional on DEBUG?
    //private final long interval = TimeUnit.MINUTES.toMillis(10);
    private final long interval = TimeUnit.SECONDS.toMillis(10);
    final RemoteAuto remoteAuto;

    public RemoteAutoWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        AesopPlayerApplication.getComponent(getAppContext()).inject(this);
        Log.w("AESOP " + getClass().getSimpleName() + " " + android.os.Process.myPid() + " " + Thread.currentThread().getId(), "RA Worker constructor ");
        remoteAuto = new RemoteAuto();  //????????????????? singleton?
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.w("AESOP " + getClass().getSimpleName() + " "+ android.os.Process.myPid() + " " + Thread.currentThread().getId(), "Do Work!!!!!!!!!!");
        pollLoop();
        return Result.success();
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.w("AESOP " + getClass().getSimpleName() + " "+ android.os.Process.myPid() + " " + Thread.currentThread().getId(), "Got Stop");
        // ??? delete this function?
    }

    @SuppressLint("UsableSpace")
    @WorkerThread
    public void pollLoop() {
        Log.w("AESOP " + getClass().getSimpleName(), "loop starts");
        while (true)
        {
            if (isStopped()) {
                // Give up until we get another start
                Log.w("AESOP" + getClass().getSimpleName(), "Got isStopped ");
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
