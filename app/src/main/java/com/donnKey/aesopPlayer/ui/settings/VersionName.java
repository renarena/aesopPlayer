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
package com.donnKey.aesopPlayer.ui.settings;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class VersionName implements Comparable<VersionName>{
    private int major = -1;
    private int minor = 0;
    private int revisions = 0;
    // detail and tag could go here if needed.

    public VersionName(String s) {
        // That's what git describe yields (assuming the most recent annotated tag
        // follows the vx.x.x pattern). The first character of what becomes tag is 'g' for GIT.
        try {
            Pattern p = Pattern.compile("v(\\d+)\\.(\\d+)\\.(\\d+)(-(\\d+)-(.+))?$");
            Matcher m = p.matcher(s);
            if (!m.find()) {
                return;
            }
            major = Integer.parseInt(m.group(1));
            minor = Integer.parseInt(m.group(2));
            @SuppressWarnings("unused")
            int detail = Integer.parseInt(m.group(3));
            if (m.group(5) != null) {
                revisions = Integer.parseInt(m.group(5));
                String tag = m.group(6);
            }
        } catch (Exception e) {
            major = -1;
        }
    }

    @Override
    public int compareTo(VersionName o) {
        return innerCompare(o);
    }
    private int innerCompare(VersionName o) {
        if (major < 0) {
            return 1;
        }
        if (major != o.major) {
            return Integer.compare(major, o.major);
        }
        if (minor != o.minor) {
            return Integer.compare(minor, o.minor);
        }
        // detail and tag are not used in this comparison
        // We rely on revisions being 0 when it's just vx.x.x for both
        return Integer.compare(revisions, o.revisions);
    }
}
