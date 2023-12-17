package me.bmax.apatch.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ServicesUtil {
    public static final String TAG = "ServicesUtil";

    public static List<Integer> getUserIds(Context context) {
        List<Integer> result = new ArrayList<>();
        UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        List<UserHandle> userProfiles = um.getUserProfiles();
        for (UserHandle userProfile : userProfiles) {
            int userId = userProfile.hashCode();
            result.add(userProfile.hashCode());
        }
        return result;
    }

    public static ArrayList<PackageInfo> getInstalledPackagesAll(Context context, int flags) {
        ArrayList<PackageInfo> packages = new ArrayList<>();
        for (Integer userId : getUserIds(context)) {
            packages.addAll(getInstalledPackagesAsUser(context, flags, userId));
        }
        return packages;
    }

    public static List<PackageInfo> getInstalledPackagesAsUser(Context context, int flags, int userId) {
        try {
            PackageManager pm = context.getPackageManager();
            Method getInstalledPackagesAsUser = pm.getClass().getDeclaredMethod("getInstalledPackagesAsUser", int.class, int.class);
            return (List<PackageInfo>) getInstalledPackagesAsUser.invoke(pm, flags, userId);
        } catch (Throwable e) {
            Log.e(TAG, "err", e);
        }

        return new ArrayList<>();
    }
}
