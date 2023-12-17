package me.bmax.apatch

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
    enum class State {
        UNKNOWN_STATE,
        KERNELPATCH_READY,
        KERNELPATCH_NEED_UPDATE,
        ANDROIDPATCH_NEED_UPDATE,
        ANDROIDPATCH_INSTALLING,
        ANDROIDPATCH_UNINSTALLING,
        ANDROIDPATCH_INSTALLED,
    }
    companion object {
        val APD_PATH = "/data/adb/apd"
        val KPATCH_PATH = "/data/adb/kpatch"
        val KPATCH_SHADOW_PATH = "/system/bin/truncate"
        val APATCH_FLODER = "/data/adb/ap/"
        val APATCH_BIN_FLODER = "/data/adb/ap/bin/"
        val APATCH_LOG_FLODER = "/data/adb/ap/log/"
        val APD_LINK_PATH = APATCH_FLODER + "apd"
        val KPATCH_LINK_PATH = APATCH_FLODER + "kpatch"

        val APATCH_VERSION_PATH = APATCH_FLODER + "version"
        val MAGISKPOLICY_BIN_PATH = APATCH_BIN_FLODER + "magiskpolicy"
        val BUSYBOX_BIN_PATH = APATCH_BIN_FLODER + "busybox"
        val RESETPROP_BIN_PATH = APATCH_BIN_FLODER + "resetprop"
        val MAGISK_SCONTEXT = "u:r:magisk:s0"

        val DEFAULT_SU_PATH = "/system/bin/kp"
        val LEGACY_SU_PATH = "/system/bin/su"

        val RESTORECON_PATH = "restorecon"

        // todo: should we store super_key in SharedPreferences
        private const val SUPER_KEY = "super_key"
        private lateinit var sharedPreferences: SharedPreferences

        private val _apStateLiveData = MutableLiveData<State>(State.UNKNOWN_STATE)
        val apStateLiveData: LiveData<State> = _apStateLiveData

        fun uninstall() {
            if(_apStateLiveData.value != State.ANDROIDPATCH_INSTALLED) return
            _apStateLiveData.value = State.ANDROIDPATCH_UNINSTALLING
            thread {
                Natives.su(0, null)
                Log.d(TAG, "APatch uninstalling ...")

//                Thread.sleep(3000)

                Natives.resetSuPath(DEFAULT_SU_PATH)
                File(APATCH_VERSION_PATH).delete()
                File(APD_PATH).delete()
                File(KPATCH_PATH).delete()
                File(APATCH_BIN_FLODER).deleteRecursively()

                Log.d(TAG, "APatch removed ...")

                _apStateLiveData.postValue(State.KERNELPATCH_READY)
            }
        }

        fun install() {
            if(_apStateLiveData.value != State.KERNELPATCH_READY
                && _apStateLiveData.value != State.ANDROIDPATCH_NEED_UPDATE) {
                return
            }
            _apStateLiveData.value = State.ANDROIDPATCH_INSTALLING

            thread {
                Natives.su(0, null)
                Log.d(TAG, "APatch installing ...")

//                Thread.sleep(3000)

                Natives.resetSuPath(LEGACY_SU_PATH)

                if(!File(APATCH_FLODER).exists()) File(APATCH_FLODER).mkdir()
                if(!File(APATCH_BIN_FLODER).exists()) File(APATCH_BIN_FLODER).mkdir()
                if(!File(APATCH_LOG_FLODER).exists()) File(APATCH_LOG_FLODER).mkdir()

                val nativeDir = apApp.applicationInfo.nativeLibraryDir

                File(nativeDir, "libkpatch.so").copyTo(File(KPATCH_PATH), true)
                File(KPATCH_PATH).setExecutable(true, true)

                if(!File(APATCH_BIN_FLODER, "kpatch").exists()) {
                    Paths.get(APATCH_BIN_FLODER, "kpatch").createSymbolicLinkPointingTo(Paths.get(
                        KPATCH_PATH))
                }

                File(nativeDir, "libmagiskpolicy.so").copyTo(File(MAGISKPOLICY_BIN_PATH), true)
                File(MAGISKPOLICY_BIN_PATH).setExecutable(true, true)

                File(nativeDir, "libresetprop.so").copyTo(File(RESETPROP_BIN_PATH), true)
                File(RESETPROP_BIN_PATH).setExecutable(true, true)

                File(nativeDir, "libbusybox.so").copyTo(File(BUSYBOX_BIN_PATH), true)
                File(BUSYBOX_BIN_PATH).setExecutable(true, true)

                File(nativeDir, "libapd.so").copyTo(File(APD_PATH), true)
                File(APD_PATH).setExecutable(true, true)

                if(!File(APATCH_BIN_FLODER, "apd").exists()) {
                    Paths.get(APATCH_BIN_FLODER, "apd").createSymbolicLinkPointingTo(Paths.get(
                        APD_PATH))
                }

                ShellUtils.fastCmdResult("${RESTORECON_PATH} ${APD_PATH}")
                ShellUtils.fastCmdResult("${RESTORECON_PATH} ${KPATCH_PATH}")
                ShellUtils.fastCmdResult("${RESTORECON_PATH} -R ${APATCH_FLODER}")

                val mgv = getManagerVersion().second
                File(APATCH_VERSION_PATH).writeText(mgv.toString())

                ShellUtils.fastCmdResult("${RESTORECON_PATH} ${APATCH_VERSION_PATH}")

                ShellUtils.fastCmdResult("${KPATCH_PATH} ${superKey} --android_user_init")

                Log.d(TAG, "APatch installed ...")

                _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
            }
        }

        var superKey: String = ""
            get
            private set(value) {
                field = value
                val ready = Natives.nativeReady(value)
                _apStateLiveData.value = if(ready) State.KERNELPATCH_READY else  State.UNKNOWN_STATE
                Log.d(TAG, "state: " + _apStateLiveData.value)

                sharedPreferences.edit().putString(SUPER_KEY, value).apply()

                thread {
                    Natives.su(0, null)
                    val vf = File(APATCH_VERSION_PATH)
                    val mgv = getManagerVersion().second
                    if(vf.exists() && vf.readLines().get(0).toInt() == mgv) {   //
                        _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
                        Log.d(TAG, "state: " + _apStateLiveData.value)
                        return@thread
                    }
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