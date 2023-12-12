package me.bmax.apatch

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.ui.graphics.vector.Path
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import coil.Coil
import coil.ImageLoader
import com.topjohnwu.superuser.ShellUtils
import me.bmax.apatch.ui.screen.getManagerVersion
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import java.io.File
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.io.path.createSymbolicLinkPointingTo

lateinit var apApp: APApplication

val TAG = "APatch"

class APApplication : Application() {
    enum class ApState(val flag: Int){
        UNKNOWN(0),
        KERNELPATCH_READY(0x1),
        APATCH_INSTALLING(0x2),
        APATCH_INSTALLED(0x4)
    }
    companion object {
        val ANDROID_SU_PATH = "/system/bin/kp"
        val APD_PATH = "/data/adb/apd"
        val KPATCH_PATH = "/data/adb/kpatch"
        val KPATCH_SHADOW_PATH = "/system/bin/truncate"
        val ADB_FLODER = "/data/adb/"
        val APATCH_FLODER = "/data/adb/ap/"
        val APATCH_BIN_FLODER = "/data/adb/ap/bin/"
        val APATCH_LOG_FLODER = "/data/adb/ap/log/"

        val APATCH_VERSION_PATH = APATCH_FLODER + "version"
        val MAGISKPOLICY_BIN_PATH = APATCH_BIN_FLODER + "magiskpolicy"
        val BUSYBOX_BIN_PATH = APATCH_BIN_FLODER + "busybox"
        val RESETPROP_BIN_PATH = APATCH_BIN_FLODER + "resetprop"

        val RESTORECON_PATH = "restorecon"

        // todo: should we store super_key in SharedPreferences
        private const val SUPER_KEY = "super_key"
        private lateinit var sharedPreferences: SharedPreferences

        private val _apStateLiveData = MutableLiveData<ApState>(ApState.UNKNOWN)
        val apStateLiveData: LiveData<ApState> = _apStateLiveData

        var superKey: String = ""
            get
            private set(value) {
                field = value
                val ready = Natives.nativeReady(value)
                _apStateLiveData.value = ApState.KERNELPATCH_READY
                if(!ready) return

                sharedPreferences.edit().putString(SUPER_KEY, value).apply()

                thread {
                    Natives.su(0, null)
                    val vf = File(APATCH_VERSION_PATH)
                    val mgv = getManagerVersion(apApp).second
                    if(vf.exists() && vf.readLines().get(0).toInt() == mgv) {
                        _apStateLiveData.postValue(ApState.APATCH_INSTALLED)
                        return@thread
                    }

                    Log.d(TAG, "APatch installing ...")

                    _apStateLiveData.postValue(ApState.APATCH_INSTALLING)

                    if(!File(APATCH_FLODER).exists()) File(APATCH_FLODER).mkdir()
                    if(!File(APATCH_BIN_FLODER).exists()) File(APATCH_BIN_FLODER).mkdir()
                    if(!File(APATCH_LOG_FLODER).exists()) File(APATCH_LOG_FLODER).mkdir()

                    val nativeDir = apApp.applicationInfo.nativeLibraryDir

                    File(nativeDir, "libkpatch.so").copyTo(File(KPATCH_PATH), true)
                    File(KPATCH_PATH).setExecutable(true, true)

                    Paths.get(APATCH_BIN_FLODER, "kpatch").createSymbolicLinkPointingTo(Paths.get(
                        KPATCH_PATH))

                    File(nativeDir, "libmagiskpolicy.so").copyTo(File(MAGISKPOLICY_BIN_PATH), true)
                    File(MAGISKPOLICY_BIN_PATH).setExecutable(true, true)

                    File(nativeDir, "libresetprop.so").copyTo(File(RESETPROP_BIN_PATH), true)
                    File(RESETPROP_BIN_PATH).setExecutable(true, true)

                    File(nativeDir, "libbusybox.so").copyTo(File(BUSYBOX_BIN_PATH), true)
                    File(BUSYBOX_BIN_PATH).setExecutable(true, true)

                    File(nativeDir, "libapd.so").copyTo(File(APD_PATH), true)
                    File(APD_PATH).setExecutable(true, true)

                    Paths.get(APATCH_BIN_FLODER, "apd").createSymbolicLinkPointingTo(Paths.get(
                        APD_PATH))

                    vf.writeText(mgv.toString())

                    ShellUtils.fastCmdResult("${RESTORECON_PATH} ${APD_PATH}")
                    ShellUtils.fastCmdResult("${RESTORECON_PATH} ${KPATCH_PATH}")
                    ShellUtils.fastCmdResult("${RESTORECON_PATH} -R ${APATCH_FLODER}")

                    Log.d(TAG, "APatch install done ...")

                    _apStateLiveData.postValue(ApState.APATCH_INSTALLED)
                }
            }
    }

    fun updateSuperKey(password: String) {
        superKey = password
    }

    override fun onCreate() {
        super.onCreate()
        apApp = this

        // todo:
        sharedPreferences = getSharedPreferences("config", Context.MODE_PRIVATE)
        superKey = sharedPreferences.getString(SUPER_KEY, "") ?: ""

        val context = this
        val iconSize = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(iconSize, false, context))
                }
                .build()
        )
    }
}