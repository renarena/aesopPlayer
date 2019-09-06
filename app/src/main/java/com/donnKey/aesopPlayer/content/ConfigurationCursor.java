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
package com.donnKey.aesopPlayer.content;

import android.database.AbstractCursor;

class ConfigurationCursor extends AbstractCursor {

    private final static String[] COLUMN_NAMES = { "KioskModeAvailable", "KioskModeEnabled" };
    private final boolean[] values;

    ConfigurationCursor(boolean isKioskModeAvailable, boolean isKioskModeEnabled) {
        this.values = new boolean[]{ isKioskModeAvailable, isKioskModeEnabled };
    }

    @Override
    public int getCount() {
        return 1;
    }

    @Override
    public String[] getColumnNames() {
        return COLUMN_NAMES;
    }

    @Override
    public String getString(int i) {
        return Boolean.toString(values[i]);
    }

    @Override
    public short getShort(int i) {
        return (short) (values[i] ? 1 : 0);
    }

    @Override
    public int getInt(int i) {
        return values[i] ? 1 : 0;
    }

    @Override
    public long getLong(int i) {
        return values[i] ? 1 : 0;
    }

    @Override
    public float getFloat(int i) {
        return values[i] ? 1 : 0;
    }

    @Override
    public double getDouble(int i) {
        return values[i] ? 1 : 0;
    }

    @Override
    public boolean isNull(int i) {
        return false;
    }
}
