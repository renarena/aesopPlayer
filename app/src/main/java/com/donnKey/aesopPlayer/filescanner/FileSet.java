/*
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
package com.donnKey.aesopPlayer.filescanner;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;

import java.io.File;

public class FileSet {

    public final String id;
    public final String directoryName;
    public final File path;
    public final File[] files;
    public final boolean isDemoSample;
    public final boolean isReference; // Never mark this "Completed", thus never default delete in Provisioning

    public FileSet(String id, @NonNull File absolutePath, File[] files, boolean isDemoSample, boolean isReference) {
        Preconditions.checkArgument(absolutePath.isDirectory());
        this.id = id;
        this.path = absolutePath;
        this.directoryName = absolutePath.getName();
        this.files = files;
        this.isDemoSample = isDemoSample;
        this.isReference = isReference;
    }

    /**
     * Compares only the id field.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileSet fileSet = (FileSet) o;

        return id.equals(fileSet.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
