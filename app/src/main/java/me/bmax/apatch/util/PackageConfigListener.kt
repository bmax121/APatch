package me.bmax.apatch.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import kotlin.concurrent.thread

class PackageConfigListener : BroadcastReceiver() {
    companion object {
        private const val TAG = "APatchPkgListener"
    }

    override fun onReceive(context: Context?, intent: Intent?) {

        intent?.let {
            if (it.action == Intent.ACTION_PACKAGE_REMOVED) {
                val prefs =
                    context?.getSharedPreferences(APApplication.SP_NAME, Context.MODE_PRIVATE)
                if (prefs?.getString(APatchKeyHelper.SUPER_KEY_ENC, null).isNullOrBlank()) {
                    if (prefs?.getString(APatchKeyHelper.SUPER_KEY, null).isNullOrBlank()) {
                        return
                    }
                }
                APatchKeyHelper.setSharedPreferences(prefs)
                APApplication.superKey = APatchKeyHelper.readSPSuperKey()

                it.data?.encodedSchemeSpecificPart?.let { packageName ->
                    val packageInfo = context?.packageManager?.getPackageInfo(packageName, 0)
                    val uid = packageInfo?.applicationInfo?.uid

                    if(uid == null) {
                        Log.e(TAG, "package $packageName uninstall, but can't find uid")
                    } else {
                        Log.e(TAG, "package $packageName uninstall, uid: $uid")

                        val config = PkgConfig.Config(packageName, 1, 0, Natives.Profile(uid, 0, ""));
                        PkgConfig.changeConfig(config)
                    }
                }
            }
        }
    }
}
