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
import com.topjohnwu.superuser.Shell
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import java.io.File
import kotlin.concurrent.thread
import org.json.JSONArray

lateinit var apApp: APApplication

val TAG = "APatch"

class APApplication : Application() {
    enum class State {
        UNKNOWN_STATE,
        KERNELPATCH_INSTALLED,
        KERNELPATCH_NEED_UPDATE,
        ANDROIDPATCH_READY,
        ANDROIDPATCH_INSTALLED,
        ANDROIDPATCH_INSTALLING,
        ANDROIDPATCH_NEED_UPDATE,
        ANDROIDPATCH_UNINSTALLING,
    }

    companion object {
        val APD_PATH = "/data/adb/apd"
        val KPATCH_PATH = "/data/adb/kpatch"
        val KPATCH_SHADOW_PATH = "/system/bin/truncate"
        val APATCH_FLODER = "/data/adb/ap/"
        val APATCH_BIN_FLODER = "/data/adb/ap/bin/"
        val APATCH_LOG_FLODER = "/data/adb/ap/log/"
        val APD_LINK_PATH = APATCH_BIN_FLODER + "apd"
        val KPATCH_LINK_PATH = APATCH_BIN_FLODER + "kpatch"
        val PACKAGE_CONFIG_FILE = APATCH_FLODER + "package_config"
        val SU_PATH_FILE = APATCH_FLODER + "su_path"
        val SAFEMODE_FILE = "/dev/.safemode"
        val GLOBAL_NAMESPACE_FILE = "/data/adb/.global_namespace_enable"

        val KPATCH_VERSION_PATH = APATCH_FLODER + "kpatch_version"
        val APATCH_VERSION_PATH = APATCH_FLODER + "apatch_version"
        val MAGISKPOLICY_BIN_PATH = APATCH_BIN_FLODER + "magiskpolicy"
        val BUSYBOX_BIN_PATH = APATCH_BIN_FLODER + "busybox"
        val RESETPROP_BIN_PATH = APATCH_BIN_FLODER + "resetprop"
        val MAGISK_SCONTEXT = "u:r:magisk:s0"

        val DEFAULT_SU_PATH = "/system/bin/kp"
        val LEGACY_SU_PATH = "/system/bin/su"

        // TODO: encrypt super_key before saving it on SharedPreferences
        private const val SUPER_KEY = "super_key"
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

        var kPatchVersion: String = ""
        var aPatchVersion: Int = 0

        fun installKpatch() {
            if (_kpStateLiveData.value != State.KERNELPATCH_NEED_UPDATE) {
                return
            }

            thread {
                val rc = Natives.su(0, null)
                if (!rc) {
                    Log.e(TAG, "Native.su failed: " + rc)
                    return@thread
                }

                kPatchVersion = getKernelPatchVersion()

                val cmds = arrayOf(
                    "echo ${kPatchVersion} > ${KPATCH_VERSION_PATH}",
                )

                Shell.getShell().newJob().add(*cmds).to(logCallback, logCallback).exec()

                Log.d(TAG, "KPatch installed ...")
                _kpStateLiveData.postValue(State.KERNELPATCH_INSTALLED)
            }
        }

        fun uninstallApatch() {
            if (_apStateLiveData.value != State.ANDROIDPATCH_INSTALLED) return
            _apStateLiveData.value = State.ANDROIDPATCH_UNINSTALLING

            thread {
                val rc = Natives.su(0, null)
                if (!rc) {
                    Log.e(TAG, "Native.su failed: " + rc)
                    _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
                    return@thread
                }
                Log.d(TAG, "APatch uninstalling ...")

                Natives.resetSuPath(DEFAULT_SU_PATH)
                File(APATCH_VERSION_PATH).delete()
                File(APD_PATH).delete()
                // Reserved, used to obtain logs
                // File(KPATCH_PATH).delete()
                File(APATCH_FLODER).deleteRecursively()

                Log.d(TAG, "APatch removed ...")

                _apStateLiveData.postValue(State.ANDROIDPATCH_READY)
            }
        }

        fun installApatch() {
            val state = _apStateLiveData.value
            if (_apStateLiveData.value != State.ANDROIDPATCH_READY &&
                _apStateLiveData.value != State.ANDROIDPATCH_NEED_UPDATE) {
                return
            }
            _apStateLiveData.value = State.ANDROIDPATCH_INSTALLING

            val nativeDir = apApp.applicationInfo.nativeLibraryDir

            thread {
                val rc = Natives.su(0, null)
                if (!rc) {
                    Log.e(TAG, "Native.su failed: " + rc)
                    // revert state
                    _apStateLiveData.postValue(state)
                    return@thread
                }

                val cmds = arrayOf(
                    "mkdir -p ${APATCH_BIN_FLODER}",
                    "mkdir -p ${APATCH_LOG_FLODER}",

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
                    "echo ${getManagerVersion().second} > ${APATCH_VERSION_PATH}",

                    "touch ${KPATCH_VERSION_PATH}",
                    "[ -s ${KPATCH_VERSION_PATH} ] || echo ${getKernelPatchVersion()} > ${KPATCH_VERSION_PATH}",

                    "restorecon -R ${APATCH_FLODER}",

                    "${KPATCH_PATH} ${superKey} android_user init",
                )

                Shell.getShell().newJob().add(*cmds).to(logCallback, logCallback).exec()

                aPatchVersion = getManagerVersion().second
                kPatchVersion = getKernelPatchVersion()

                Log.d(TAG, "APatch installed ...")
                _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
            }
        }

        fun getManagerVersion(): Pair<String, Int> {
            val packageInfo = apApp.packageManager.getPackageInfo(apApp.packageName, 0)
            return Pair(packageInfo.versionName, packageInfo.versionCode)
        }

        fun getKernelPatchVersion(): String {
            val kernelPatchVersion = Natives.kernelPatchVersion()
            return "%d.%d.%d".format(
                kernelPatchVersion.and(0xff0000).shr(16),
                kernelPatchVersion.and(0xff00).shr(8),
                kernelPatchVersion.and(0xff)
            )
        }

        var superKey: String = ""
            get
            private set(value) {
                field = value
                val ready = Natives.nativeReady(value)
                _kpStateLiveData.value = if (ready) State.KERNELPATCH_INSTALLED else State.UNKNOWN_STATE
                _apStateLiveData.value = if (ready) State.ANDROIDPATCH_READY else State.UNKNOWN_STATE
                Log.d(TAG, "state: " + _kpStateLiveData.value)
                sharedPreferences.edit().putString(SUPER_KEY, value).apply()

                thread {
                    val rc = Natives.su(0, null)
                    if (!rc) {
                        Log.e(TAG, "su failed: " + rc)
                        return@thread
                    }

                    val kv = File(KPATCH_VERSION_PATH)
                    val kvn = getKernelPatchVersion()
                    if (kv.exists()) {
                        kPatchVersion = kv.readLines().get(0)
                        if (kPatchVersion == kvn) {
                            _kpStateLiveData.postValue(State.KERNELPATCH_INSTALLED)
                            Log.d(TAG, "state: " + State.KERNELPATCH_INSTALLED + ", version: " + kPatchVersion)
                        } else {
                            _kpStateLiveData.postValue(State.KERNELPATCH_NEED_UPDATE)
                            Log.d(TAG, "state: " + State.KERNELPATCH_NEED_UPDATE + ", version: " + kPatchVersion + "->" + kvn)
                        }
                    }

                    val av = File(APATCH_VERSION_PATH)
                    val mgv = getManagerVersion().second
                    if (av.exists()) {
                        aPatchVersion = av.readLines().get(0).toInt()
                        if (aPatchVersion == mgv) {
                            _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
                            Log.d(TAG, "state: " + State.ANDROIDPATCH_INSTALLED + ", version: " + aPatchVersion)
                        } else {
                            _apStateLiveData.postValue(State.ANDROIDPATCH_NEED_UPDATE)
                            Log.d(TAG, "state: " + State.ANDROIDPATCH_NEED_UPDATE + ", version: " + aPatchVersion + "->" + mgv)
                        }

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

                    return@thread
                }
            }
    }

    fun getSuperKey(): String {
        return superKey
    }

    fun updateSuperKey(password: String) {
        superKey = password
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