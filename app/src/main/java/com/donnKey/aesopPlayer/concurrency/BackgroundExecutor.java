/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2019 Donn S. Terry
 * Copyright (c) 2015-2017 Marcin Simonides
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
package com.donnKey.aesopPlayer.concurrency;

import android.os.Handler;
import androidx.annotation.NonNull;

import java.util.concurrent.Callable;

public class BackgroundExecutor {

    private final @NonNull Handler mainThreadHandler;
    private final @NonNull Handler taskHandler;

    public BackgroundExecutor(@NonNull Handler mainThreadHandler, @NonNull Handler taskHandler) {
        this.mainThreadHandler = mainThreadHandler;
        this.taskHandler = taskHandler;
    }

    public <V> SimpleFuture<V> postTask(@NonNull Callable<V> task) {
        BackgroundDeferred<V> deferred = new BackgroundDeferred<>(task, mainThreadHandler);
        taskHandler.post(deferred);
        return deferred;
    }
}
