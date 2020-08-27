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

import com.donnKey.aesopPlayer.analytics.CrashWrapper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

class Ftp {
    private static final String TAG = "Ftp";

    public static String getFile(String server, int port, String user, String password,
                        String fileToGet, File fileToPut) {
        String result = ""; // "" is success, otherwise error text

        if (port == -1) {
            port = 21;
        }
        if (user == null || user.isEmpty()) {
            user = "anonymous";
        }
        if (password == null || password.isEmpty()) {
            password = "anonymous";
        }

        FTPClient ftpClient = new FTPClient();
        try ( OutputStream outputStream1 = new BufferedOutputStream(new FileOutputStream(fileToPut)) ){
            ftpClient.setDataTimeout(1000);

            ftpClient.connect(server, port);
            ftpClient.login(user, password);
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            boolean success = ftpClient.retrieveFile(fileToGet, outputStream1);

            if (!success) {
                result = "Ftp download failed for unknown reason.";
            }
        } catch (IOException e) {
            CrashWrapper.recordException(TAG, e);
            result = e.getMessage();
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException e) {
                result = e.getMessage();
                CrashWrapper.recordException(TAG, e);
            }
        }

        return result;
    }
}
