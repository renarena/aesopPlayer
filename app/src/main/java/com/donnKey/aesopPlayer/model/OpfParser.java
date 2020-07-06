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

package com.donnKey.aesopPlayer.model;

import android.util.Xml;

import androidx.annotation.NonNull;

import com.donnKey.aesopPlayer.analytics.CrashWrapper;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.donnKey.aesopPlayer.model.AudioBook.titleClean;

// Does a minimal parse on the opf (Open Talking Book) file to find title and author.
// See: https://www.daisy.org/z3986/specifications/Z39-86-2002.html
// which in turn references the EPUB standard.
class OpfParser {
    public AudioBook.TitleAndAuthor getTitle(File file) {
        // Again... order unclear
        String title = null;
        String author = null;
        String creator = null;
        try {
            try {
                try (InputStream xmlStream = new FileInputStream(file)) {

                    XmlPullParser xmlParser = Xml.newPullParser();
                    xmlParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                    xmlParser.setInput(xmlStream, null);
                    xmlParser.nextTag();

                    // EPUB says there must be one "package" element and it must be the root
                    xmlParser.require(XmlPullParser.START_TAG, null, "package");

                    while (xmlParser.next() != XmlPullParser.END_TAG) {
                        if (xmlParser.getEventType() != XmlPullParser.START_TAG) {
                            continue;
                        }
                        // The standard isn't clear about whether the "metadata" tag is first
                        if (xmlParser.getName().equals("metadata")) {
                            while (xmlParser.next() != XmlPullParser.END_TAG) {
                                if (xmlParser.getEventType() != XmlPullParser.START_TAG) {
                                    continue;
                                }

                                // Again... order unclear
                                if (xmlParser.getName().equals("dc-metadata")) {
                                    while (xmlParser.next() != XmlPullParser.END_TAG) {
                                        if (xmlParser.getEventType() != XmlPullParser.START_TAG) {
                                            continue;
                                        }
                                        // Take the first title entry
                                        if (xmlParser.getName().equals("dc:Title")) {
                                            String t = xmlParser.nextText();
                                            if (title == null) {
                                                title = t;
                                            }
                                        }
                                        else if (xmlParser.getName().equals("dc:Creator")) {
                                            String role = xmlParser.getAttributeValue(null, "role");
                                            String c = xmlParser.nextText();
                                            if (creator == null) {
                                                creator = c;
                                            }
                                            if (role != null && role.equals("aut") && author == null) {
                                                author = c;
                                            }
                                        }
                                        else {
                                            xmlParser.nextText();
                                        }
                                    }
                                }
                                else {
                                    skipTag(xmlParser);
                                }
                            }
                        }
                        else {
                            skipTag(xmlParser);
                        }
                    }
                }
            } catch (IOException e) {
                CrashWrapper.recordException(e);
            }
        } catch (XmlPullParserException e) {
            CrashWrapper.recordException(e);
        }

        if (title == null) {
            return null;
        }

        // No explicit author... use creator
        if (author == null) {
            author = creator;
        }

        if (author == null) {
            author = "";
        }

        title = titleClean(title.trim());
        author = titleClean(author.trim());

        return new AudioBook.TitleAndAuthor(title, author);
    }

    private void skipTag(@NonNull XmlPullParser xmlParser)
        throws XmlPullParserException, IOException {
        if (xmlParser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int nest = 1;
        while (nest > 0) {
            switch (xmlParser.next()) {
                case XmlPullParser.END_TAG:
                    nest--;
                    break;
                case XmlPullParser.START_TAG:
                    nest++;
                    break;
            }
        }
    }
}
