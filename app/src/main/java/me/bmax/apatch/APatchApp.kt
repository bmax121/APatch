package me.bmax.apatch

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import coil.Coil
import coil.ImageLoader
import com.topjohnwu.superuser.CallbackList
import me.bmax.apatch.util.*
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import java.io.File
import kotlin.concurrent.thread

lateinit var apApp: APApplication

val TAG = "APatch"

class APApplication : Application() {
    enum class State {
        UNKNOWN_STATE,

        KERNELPATCH_INSTALLED,
        KERNELPATCH_NEED_UPDATE,
        KERNELPATCH_NEED_REBOOT,
        KERNELPATCH_UNINSTALLING,

        ANDROIDPATCH_NOT_INSTALLED,
        ANDROIDPATCH_INSTALLED,
        ANDROIDPATCH_INSTALLING,
        ANDROIDPATCH_NEED_UPDATE,
        ANDROIDPATCH_UNINSTALLING,
    }


    companion object {
        val APD_PATH = "/data/adb/apd"
        val KPATCH_PATH = "/data/adb/kpatch"
        val SUPERCMD = "/system/bin/truncate"
        val APATCH_FOLDER = "/data/adb/ap/"
        val APATCH_BIN_FOLDER = APATCH_FOLDER + "bin/"
        val APATCH_LOG_FOLDER = APATCH_FOLDER + "log/"
        val APD_LINK_PATH = APATCH_BIN_FOLDER + "apd"
        val KPATCH_LINK_PATH = APATCH_BIN_FOLDER + "kpatch"
        val PACKAGE_CONFIG_FILE = APATCH_FOLDER + "package_config"
        val SU_PATH_FILE = APATCH_FOLDER + "su_path"
        val SAFEMODE_FILE = "/dev/.safemode"
        val NEED_REBOOT_FILE = "/dev/.need_reboot"
        val GLOBAL_NAMESPACE_FILE = "/data/adb/.global_namespace_enable"

        @Deprecated("Use 'apd -V'")
        val APATCH_VERSION_PATH = APATCH_FOLDER + "version"
        val MAGISKPOLICY_BIN_PATH = APATCH_BIN_FOLDER + "magiskpolicy"
        val BUSYBOX_BIN_PATH = APATCH_BIN_FOLDER + "busybox"
        val RESETPROP_BIN_PATH = APATCH_BIN_FOLDER + "resetprop"
        val MAGISK_SCONTEXT = "u:r:magisk:s0"

        val DEFAULT_SU_PATH = "/system/bin/kp"
        val LEGACY_SU_PATH = "/system/bin/su"

        // TODO: encrypt super_key before saving it on SharedPreferences
        const val SUPER_KEY = "super_key"
        const val SKIP_STORE_SUPER_KEY = "skip_store_super_key"
        private const val SHOW_BACKUP_WARN = "show_backup_warning"
        private lateinit var sharedPreferences: SharedPreferences

        private val logCallback: CallbackList<String?> = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                Log.d(TAG, s.toString())
            }
        }

        private val _kpStateLiveData = MutableLiveData<State>(State.UNKNOWN_STATE)
        val kpStateLiveData: LiveData<State> = _kpStateLiveData

        private val _apStateLiveData = MutableLiveData<State>(State.UNKNOWN_STATE)
        val apStateLiveData: LiveData<State> = _apStateLiveData

        fun uninstallApatch() {
            if (_apStateLiveData.value != State.ANDROIDPATCH_INSTALLED) return
            _apStateLiveData.value = State.ANDROIDPATCH_UNINSTALLING

            Natives.resetSuPath(DEFAULT_SU_PATH)

            val cmds = arrayOf(
                "rm -f ${APD_PATH}",
                "rm -f ${KPATCH_PATH}",
                "rm -rf ${APATCH_FOLDER}",
            )

            val shell = getRootShell()
            shell.newJob().add(*cmds).to(logCallback, logCallback).exec()

            Log.d(TAG, "APatch uninstalled...")
            if (_kpStateLiveData.value == State.UNKNOWN_STATE) {
                _apStateLiveData.postValue(State.UNKNOWN_STATE)
            } else {
                _apStateLiveData.postValue(State.ANDROIDPATCH_NOT_INSTALLED)
            }
        }

        fun installApatch() {
            val state = _apStateLiveData.value
            if (state != State.ANDROIDPATCH_NOT_INSTALLED &&
                state != State.ANDROIDPATCH_NEED_UPDATE) {
                return
            }
            _apStateLiveData.value = State.ANDROIDPATCH_INSTALLING
            val nativeDir = apApp.applicationInfo.nativeLibraryDir

            Natives.resetSuPath(LEGACY_SU_PATH)

            val cmds = arrayOf(
                "mkdir -p ${APATCH_BIN_FOLDER}",
                "mkdir -p ${APATCH_LOG_FOLDER}",

                // kpatch extracted from kernel
                "cp -f ${nativeDir}/libkpatch.so ${KPATCH_PATH}",
                "chmod +x ${KPATCH_PATH}",
                "ln -s ${KPATCH_PATH} ${KPATCH_LINK_PATH}",
                "restorecon ${KPATCH_PATH}",

                "cp -f ${nativeDir}/libapd.so ${APD_PATH}",
                "chmod +x ${APD_PATH}",
                "ln -s ${APD_PATH} ${APD_LINK_PATH}",
                "restorecon ${APD_PATH}",

                "cp -f ${nativeDir}/libmagiskpolicy.so ${MAGISKPOLICY_BIN_PATH}",
                "chmod +x ${MAGISKPOLICY_BIN_PATH}",
                "cp -f ${nativeDir}/libresetprop.so ${RESETPROP_BIN_PATH}",
                "chmod +x ${RESETPROP_BIN_PATH}",
                "cp -f ${nativeDir}/libbusybox.so ${BUSYBOX_BIN_PATH}",
                "chmod +x ${BUSYBOX_BIN_PATH}",

                "touch ${PACKAGE_CONFIG_FILE}",
                "touch ${SU_PATH_FILE}",
                "[ -s ${SU_PATH_FILE} ] || echo ${LEGACY_SU_PATH} > ${SU_PATH_FILE}",
                "echo ${Version.getManagerVersion().second} > ${APATCH_VERSION_PATH}",
                "restorecon -R ${APATCH_FOLDER}",

                "${KPATCH_PATH} ${superKey} android_user init",
            )

            val shell = getRootShell()
            shell.newJob().add(*cmds).to(logCallback, logCallback).exec()

            Log.d(TAG, "APatch installed...")
            _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
        }

        fun markNeedReboot() {
            val result = rootShellForResult("touch ${NEED_REBOOT_FILE}")
            _kpStateLiveData.postValue(State.KERNELPATCH_NEED_REBOOT)
            Log.d(TAG, "mark reboot ${result.code}")
        }

        var superKey: String = ""
            get
            set(value) {
                field = value
                val ready = Natives.nativeReady(value)
                _kpStateLiveData.value = if (ready) State.KERNELPATCH_INSTALLED else State.UNKNOWN_STATE
                _apStateLiveData.value = if (ready) State.ANDROIDPATCH_NOT_INSTALLED else State.UNKNOWN_STATE
                Log.d(TAG, "state: " + _kpStateLiveData.value)
                if(!ready) return

                if (!isSkipStoreSuperKeyEnabled())
                    sharedPreferences.edit().putString(SUPER_KEY, value).apply()

                thread {
                    val rc = Natives.su(0, null)
                    if (!rc) {
                        Log.e(TAG, "Native.su failed")
                        return@thread
                    }

                    // KernelPatch version
                    val buildV = Version.buildKPVUInt()
                    val installedV = Version.installedKPVUInt()

                    Log.d(TAG, "kp installed version: ${installedV}, build version: ${buildV}")

                    // use != instead of > to enable downgrade,
                    if (buildV != installedV) {
                        _kpStateLiveData.postValue(State.KERNELPATCH_NEED_UPDATE)
                    }
                    Log.d(TAG, "kp state: " + _kpStateLiveData.value)

                    if(File(NEED_REBOOT_FILE).exists()) {
                        _kpStateLiveData.postValue(State.KERNELPATCH_NEED_REBOOT)
                    }
                    Log.d(TAG, "kp state: " + _kpStateLiveData.value)

                    // AndroidPatch version
                    val mgv = Version.getManagerVersion().second
                    val installedApdVInt = Version.installedApdVUInt()
                    Log.d(TAG, "manager version: $mgv, installed apd version: $installedApdVInt")

                    if(Version.installedApdVInt > 0) {
                        _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
                    }

                    if (Version.installedApdVInt > 0 && mgv != Version.installedApdVInt) {
                        _apStateLiveData.postValue(State.ANDROIDPATCH_NEED_UPDATE)
                        // su path
                        val suPathFile = File(SU_PATH_FILE)
                        if (suPathFile.exists()) {
                            val suPath = suPathFile.readLines()[0].trim()
                            if (!Natives.suPath().equals(suPath)) {
                                Log.d(TAG, "su path: " + suPath)
                                Natives.resetSuPath(suPath)
                            }
                        }
                    }
                    Log.d(TAG, "ap state: " + _apStateLiveData.value)

                    return@thread
                }
            }
    }

    fun clearKey() {
        _kpStateLiveData.value = State.UNKNOWN_STATE
        _apStateLiveData.value = State.UNKNOWN_STATE
        sharedPreferences.edit().putString(SUPER_KEY, "").apply()
    }

    override fun onCreate() {
        super.onCreate()
        apApp = this

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

    fun getBackupWarningState(): Boolean {
        return sharedPreferences.getBoolean(SHOW_BACKUP_WARN, true)
    }

    fun updateBackupWarningState(state: Boolean) {
        sharedPreferences.edit().putBoolean(SHOW_BACKUP_WARN, state).apply()
    }
}
