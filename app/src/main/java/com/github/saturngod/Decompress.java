package com.github.saturngod;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Decompress {
    private final InputStream _zipInputStream;
    private final String _location;

    public Decompress(InputStream zipInputStream, String location) {
        _zipInputStream = zipInputStream;
        _location = location;

        _dirChecker("");
    }

    public void unzip() {
        try  {
            ZipInputStream zin = new ZipInputStream(_zipInputStream);
            ZipEntry ze;
            while ((ze = zin.getNextEntry()) != null) {
                if (ze.isDirectory()) {
                    _dirChecker(ze.getName());
                } else {


                    int size;
                    byte[] buffer = new byte[2048];

                    FileOutputStream f_out = new FileOutputStream(_location + File.separatorChar + ze.getName());
                    BufferedOutputStream bufferOut = new BufferedOutputStream(f_out, buffer.length);

                    while ((size = zin.read(buffer, 0, buffer.length)) != -1) {
                        bufferOut.write(buffer, 0, size);
                    }




                    bufferOut.flush();
                    bufferOut.close();

                    zin.closeEntry();
                    f_out.close();



                }
            }

            zin.close();

        } catch(Exception e) {
            Log.e("Decompress", "unzip", e);
        }

    }

    private void _dirChecker(String dir) {

        try {
            char lastChar = _location.charAt(_location.length() - 1);
            String loc = _location;

            if(lastChar != File.separatorChar)
            {
                loc = loc + File.separator;
            }

            File f = new File(loc + dir);

            if(!f.isDirectory()) {
                if (!f.mkdirs()) {
                    Log.w("creating dir error", loc + dir);
                }
            }
        }
        catch(Exception e){
            Log.w("creating file error", e.toString());
        }
    }
}
