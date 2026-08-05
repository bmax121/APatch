package me.bmax.apatch.magica;

import android.app.ZygotePreload;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * Runs inside the app-zygote (limited root). Forks and execs the bundled apd
 * with `late-load --magica`, which escalates to full root via adb.
 */
public class AppZygotePreload implements ZygotePreload {
    public static final String TAG = "APatchMagica";

    private static native void forkDontCareAndExecApd(String apdPath, String modulePath, String packageName);

    @Override
    public void doPreload(@NonNull ApplicationInfo appInfo) {
        File apd = new File(appInfo.nativeLibraryDir, "libapd.so");
        File module = new File(appInfo.dataDir, "files/kernelpatch.ko");
        try {
            System.loadLibrary("apjni");
            Log.d(TAG, "executing magica ...");
            forkDontCareAndExecApd(apd.getAbsolutePath(), module.getAbsolutePath(), appInfo.packageName);
        } catch (Throwable t) {
            Log.e(TAG, "failed to jailbreak", t);
        }
    }
}
