package me.bmax.apatch.util;

import android.util.Log;

import java.lang.reflect.Method;

import kotlinx.coroutines.internal.SystemPropsKt;

public class DeviceUtils {
    private static boolean getSystemProperty(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("getBoolean", String.class, boolean.class);
            return (boolean) get.invoke(c, key, false);
        } catch (Exception e) {
            Log.e("APatch", "[DeviceUtils] Failed to get system property: ", e);
        }
        return false;
    }

    // Check to see if device supports A/B (seamless) system updates
    public static boolean isABDevice() {
        return getSystemProperty("ro.build.ab_update");
    }
}
