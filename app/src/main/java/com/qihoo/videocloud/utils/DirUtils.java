
package com.qihoo.videocloud.utils;

import android.os.Environment;

import com.qihoo.livecloud.utils.FileUtils;

import java.io.File;

/**
 * Created by LeiXiaojun on 2017/9/21.
 */
public class DirUtils {

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void createDir(String dir) {
        File file = new File(dir);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    public static String getRootDir() {
        String dir = Environment.getExternalStorageDirectory().getAbsolutePath();
        dir = dir + File.separator + "LiveCloud" + File.separator;
        createDir(dir);
        return dir;
    }

    public static String getLocalServerCacheDir() {
        String dir = getRootDir() + "LocalServerCache" + File.separator;
        FileUtils.createDir(dir);
        return dir;
    }
}
