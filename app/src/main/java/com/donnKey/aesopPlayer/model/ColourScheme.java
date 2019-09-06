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
package com.donnKey.aesopPlayer.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Use the first batch from Kenneth Kelly's max contrast color palette.
// See https://eleanormaclure.files.wordpress.com/2011/03/colour-coding.pdf
// More documents on color summarised here: http://stackoverflow.com/a/4382138/3892517
public enum ColourScheme {
    VIVID_YELLOW(0xFFFFB300, 0xFF000000),
    STRONG_PURPLE(0xFF803E75, 0xFFFFFFFF),
    VIVID_ORANGE(0xFFFF6800, 0xFF000000),
    VERY_LIGHT_BLUE(0xFFA6BDD7, 0xFF000000),
    VIVID_RED(0xFFC10020, 0xFFFFFFFF),
    GREYISH_YELLOW(0xFFCEA262, 0xFF000000),
    MEDIUM_GREY(0xFF817066, 0xFFFFFFFF);

    public final int backgroundColour;
    public final int textColour;

    ColourScheme(int backgroundColour, int textColour) {
        this.backgroundColour = backgroundColour;
        this.textColour = textColour;
    }

    private static Random random;

    public static ColourScheme getRandom(List<ColourScheme> avoidColours) {
        int totalColours = ColourScheme.values().length;
        List<ColourScheme> availableColourSchemes = new ArrayList<>(totalColours);
        for (ColourScheme colour : ColourScheme.values()) {
            if (!avoidColours.contains(colour))
                availableColourSchemes.add(colour);
        }

        return availableColourSchemes.get(getRandom().nextInt(availableColourSchemes.size()));
    }


    public static ColourScheme get(int i) {
        return ColourScheme.values()[i % ColourScheme.values().length];
    }

    private static Random getRandom() {
        if (random == null)
            random = new Random();
        return random;
    }
}