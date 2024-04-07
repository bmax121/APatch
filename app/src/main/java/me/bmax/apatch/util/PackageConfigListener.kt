package me.bmax.apatch.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.CallSuper
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import kotlin.concurrent.thread

private const val TAG = "APatchPkgListener"
open class PackageConfigListener : BroadcastReceiver() {
    private fun getUid(intent: Intent): Int? {
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
        return if (uid == -1) null else uid
    }

    private fun getPkg(intent: Intent): String? {
        val pkg = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
        return pkg ?: intent.data?.schemeSpecificPart
    }


    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i(TAG, "Recieved: ${intent?.action}")
        intent?.let { it ->
            if (it.action == Intent.ACTION_PACKAGE_REMOVED || it.action == Intent.ACTION_PACKAGE_FULLY_REMOVED) {
                val prefs =
                    context?.getSharedPreferences(APApplication.SP_NAME, Context.MODE_PRIVATE)
                if (prefs?.getString(APatchKeyHelper.SUPER_KEY_ENC, null).isNullOrBlank()) {
                    if (prefs?.getString(APatchKeyHelper.SUPER_KEY, null).isNullOrBlank()) {
                        return
                    }
                }
                APatchKeyHelper.setSharedPreferences(prefs)
                val superKey = APatchKeyHelper.readSPSuperKey()
                if (superKey.isNullOrBlank())
                    return
                APApplication.superKey = superKey

                val packageUid = getUid(intent)
                val packageName = getPkg(intent)

                if (packageUid != null && packageName != null) {
                    val config =
                        PkgConfig.Config(packageName, 1, 0, Natives.Profile(packageUid, 0, ""));
                    PkgConfig.changeConfig(config)
                    Log.i(TAG, "Package $packageName uninstall, uid: $packageUid")
                }
            }
        }
    }
}
