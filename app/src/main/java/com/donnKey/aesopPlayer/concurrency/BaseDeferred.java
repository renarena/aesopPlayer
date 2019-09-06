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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A straightforward implementation of SimpleFuture.
 * It's intended for use only on a single thread.
 *
 * Note: I don't need the full power of ListenableFutures nor Rx yet.
 */
public class BaseDeferred<V> implements SimpleFuture<V> {

    private final @NonNull List<Listener<V>> listeners = new ArrayList<>();
    private @Nullable V result;
    private @Nullable Throwable exception;

    @Override
    public void addListener(@NonNull Listener<V> listener) {
        listeners.add(listener);
        if (result != null)
            listener.onResult(result);
        else if (exception != null)
            listener.onException(exception);
    }

    @Override
    public void removeListener(@NonNull Listener<V> listener) {
        listeners.remove(listener);
    }

    void setResult(@NonNull V result) {
        this.result = result;
        for (Listener<V> listener : listeners)
            listener.onResult(result);
    }

    void setException(@NonNull Throwable exception) {
        this.exception = exception;
        for (Listener<V> listener : listeners)
            listener.onException(exception);
    }
}
