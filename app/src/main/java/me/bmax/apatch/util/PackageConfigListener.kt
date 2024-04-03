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
            if (it.action == Intent.ACTION_PACKAGE_ADDED ||
                it.action == Intent.ACTION_PACKAGE_REPLACED ||
                it.action == Intent.ACTION_PACKAGE_REMOVED
            ) {
                val prefs =
                    context?.getSharedPreferences(APApplication.SP_NAME, Context.MODE_PRIVATE)
                if (prefs?.getString(APatchKeyHelper.SUPER_KEY_ENC, null).isNullOrBlank()) {
                    if (prefs?.getString(APatchKeyHelper.SUPER_KEY, null).isNullOrBlank()) {
                        return
                    }
                }

                APatchKeyHelper.setSharedPreferences(prefs)
                APApplication.superKey = APatchKeyHelper.readSPSuperKey()
                var configs: HashMap<String, PkgConfig.Config> = HashMap()
                thread {
                    Natives.su()
                    configs = PkgConfig.readConfigs()
                }.join()

                it.data?.encodedSchemeSpecificPart?.let { packageName ->
                    val currPackageConfig = configs[packageName] ?: return
                    val currentUid = currPackageConfig.profile.uid
                    val packageInfo = context?.packageManager?.getPackageInfo(packageName, 0)
                    val newUid = packageInfo?.applicationInfo?.uid

                    if (newUid != null && newUid != currentUid) {
                        Log.d(TAG, "UID has changed for package $packageName! New UID: $newUid")

                        currPackageConfig.profile.uid = newUid
                        PkgConfig.changeConfigByPkgChange(currPackageConfig)
                    }

                }
            }
        }
    }
}
