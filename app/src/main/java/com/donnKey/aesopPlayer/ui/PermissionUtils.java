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
package com.donnKey.aesopPlayer.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.donnKey.aesopPlayer.R;

import java.util.Arrays;
import java.util.Collection;

public class PermissionUtils {

    public static boolean checkAndRequestPermission(
            final AppCompatActivity activity, String[] permissions, int requestCode) {
        Collection<String> missingPermissions = Collections2.filter(Arrays.asList(permissions), new Predicate<String>() {
            @Override
            public boolean apply(String permission) {
                Preconditions.checkNotNull(permission);
                return ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED;
            }
        });
        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    activity, missingPermissions.toArray(new String[0]), requestCode);
            return false;
        }
        return true;
    }

    public static AlertDialog.Builder permissionRationaleDialogBuilder(
            AppCompatActivity activity, @StringRes int rationaleMessage) {
        return new AlertDialog.Builder(activity)
                .setMessage(rationaleMessage)
                .setTitle(R.string.permission_rationale_title)
                .setIcon(R.drawable.ic_launcher);
    }

    public static void openAppSettings(AppCompatActivity activity) {
        activity.startActivity(new Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + activity.getApplication().getPackageName())));
    }
}
