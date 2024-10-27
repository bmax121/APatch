package me.bmax.apatch

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.topjohnwu.superuser.CallbackList
import me.bmax.apatch.ui.CrashHandleActivity
import me.bmax.apatch.util.APatchCli
import me.bmax.apatch.util.APatchKeyHelper
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.getRootShell
import me.bmax.apatch.util.rootShellForResult
import me.bmax.apatch.util.verifyAppSignature
import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess

lateinit var apApp: APApplication

const val TAG = "APatch"

class APApplication : Application(), Thread.UncaughtExceptionHandler {

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    enum class State {
        UNKNOWN_STATE,

        KERNELPATCH_INSTALLED, KERNELPATCH_NEED_UPDATE, KERNELPATCH_NEED_REBOOT, KERNELPATCH_UNINSTALLING,

        ANDROIDPATCH_NOT_INSTALLED, ANDROIDPATCH_INSTALLED, ANDROIDPATCH_INSTALLING, ANDROIDPATCH_NEED_UPDATE, ANDROIDPATCH_UNINSTALLING,
    }


    companion object {
        const val APD_PATH = "/data/adb/apd"

        @Deprecated("No more KPatch ELF from 0.11.0-dev")
        const val KPATCH_PATH = "/data/adb/kpatch"
        const val SUPERCMD = "/system/bin/truncate"
        const val APATCH_FOLDER = "/data/adb/ap/"
        private const val APATCH_BIN_FOLDER = APATCH_FOLDER + "bin/"
        private const val APATCH_LOG_FOLDER = APATCH_FOLDER + "log/"
        private const val APD_LINK_PATH = APATCH_BIN_FOLDER + "apd"
        const val PACKAGE_CONFIG_FILE = APATCH_FOLDER + "package_config"
        const val SU_PATH_FILE = APATCH_FOLDER + "su_path"
        const val SAFEMODE_FILE = "/dev/.safemode"
        private const val NEED_REBOOT_FILE = "/dev/.need_reboot"
        const val GLOBAL_NAMESPACE_FILE = "/data/adb/.global_namespace_enable"
        const val KPMS_DIR = APATCH_FOLDER + "kpms/"

        @Deprecated("Use 'apd -V'")
        const val APATCH_VERSION_PATH = APATCH_FOLDER + "version"
        private const val MAGISKPOLICY_BIN_PATH = APATCH_BIN_FOLDER + "magiskpolicy"
        private const val BUSYBOX_BIN_PATH = APATCH_BIN_FOLDER + "busybox"
        private const val RESETPROP_BIN_PATH = APATCH_BIN_FOLDER + "resetprop"
        private const val MAGISKBOOT_BIN_PATH = APATCH_BIN_FOLDER + "magiskboot"
        const val MAGISK_SCONTEXT = "u:r:magisk:s0"

        private const val DEFAULT_SU_PATH = "/system/bin/kp"
        private const val LEGACY_SU_PATH = "/system/bin/su"

        const val SP_NAME = "config"
        private const val SHOW_BACKUP_WARN = "show_backup_warning"
        lateinit var sharedPreferences: SharedPreferences

        private val logCallback: CallbackList<String?> = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                Log.d(TAG, s.toString())
            }
        }

        private val _kpStateLiveData = MutableLiveData(State.UNKNOWN_STATE)
        val kpStateLiveData: LiveData<State> = _kpStateLiveData

        private val _apStateLiveData = MutableLiveData(State.UNKNOWN_STATE)
        val apStateLiveData: LiveData<State> = _apStateLiveData

        @Suppress("DEPRECATION")
        fun uninstallApatch() {
            if (_apStateLiveData.value != State.ANDROIDPATCH_INSTALLED) return
            _apStateLiveData.value = State.ANDROIDPATCH_UNINSTALLING

            Natives.resetSuPath(DEFAULT_SU_PATH)

            val cmds = arrayOf(
                "rm -f $APD_PATH",
                "rm -f $KPATCH_PATH",
                "rm -rf $APATCH_BIN_FOLDER",
                "rm -rf $APATCH_LOG_FOLDER",
                "rm -rf $APATCH_VERSION_PATH",
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

        @Suppress("DEPRECATION")
        fun installApatch() {
            val state = _apStateLiveData.value
            if (state != State.ANDROIDPATCH_NOT_INSTALLED && state != State.ANDROIDPATCH_NEED_UPDATE) {
                return
            }
            _apStateLiveData.value = State.ANDROIDPATCH_INSTALLING
            val nativeDir = apApp.applicationInfo.nativeLibraryDir

            Natives.resetSuPath(LEGACY_SU_PATH)

            val cmds = arrayOf(
                "mkdir -p $APATCH_BIN_FOLDER",
                "mkdir -p $APATCH_LOG_FOLDER",

                "cp -f ${nativeDir}/libapd.so $APD_PATH",
                "chmod +x $APD_PATH",
                "ln -s $APD_PATH $APD_LINK_PATH",
                "restorecon $APD_PATH",

                "cp -f ${nativeDir}/libmagiskpolicy.so $MAGISKPOLICY_BIN_PATH",
                "chmod +x $MAGISKPOLICY_BIN_PATH",
                "cp -f ${nativeDir}/libresetprop.so $RESETPROP_BIN_PATH",
                "chmod +x $RESETPROP_BIN_PATH",
                "cp -f ${nativeDir}/libbusybox.so $BUSYBOX_BIN_PATH",
                "chmod +x $BUSYBOX_BIN_PATH",
                "cp -f ${nativeDir}/libmagiskboot.so $MAGISKBOOT_BIN_PATH",
                "chmod +x $MAGISKBOOT_BIN_PATH",

                "touch $PACKAGE_CONFIG_FILE",
                "touch $SU_PATH_FILE",
                "[ -s $SU_PATH_FILE ] || echo $LEGACY_SU_PATH > $SU_PATH_FILE",
                "echo ${Version.getManagerVersion().second} > $APATCH_VERSION_PATH",
                "restorecon -R $APATCH_FOLDER",

                "${nativeDir}/libmagiskpolicy.so --magisk --live",
            )

            val shell = getRootShell()
            shell.newJob().add(*cmds).to(logCallback, logCallback).exec()

            // clear shell cache
            APatchCli.refresh()

            Log.d(TAG, "APatch installed...")
            _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
        }

        fun markNeedReboot() {
            val result = rootShellForResult("touch $NEED_REBOOT_FILE")
            _kpStateLiveData.postValue(State.KERNELPATCH_NEED_REBOOT)
            Log.d(TAG, "mark reboot ${result.code}")
        }


        var superKey: String = ""
            set(value) {
                field = value
                val ready = Natives.nativeReady(value)
                _kpStateLiveData.value =
                    if (ready) State.KERNELPATCH_INSTALLED else State.UNKNOWN_STATE
                _apStateLiveData.value =
                    if (ready) State.ANDROIDPATCH_NOT_INSTALLED else State.UNKNOWN_STATE
                Log.d(TAG, "state: " + _kpStateLiveData.value)
                if (!ready) return

                APatchKeyHelper.writeSPSuperKey(value)

                thread {
                    val rc = Natives.su(0, null)
                    if (!rc) {
                        Log.e(TAG, "Native.su failed")
                        return@thread
                    }

                    // KernelPatch version
                    val buildV = Version.buildKPVUInt()
                    val installedV = Version.installedKPVUInt()

                    Log.d(TAG, "kp installed version: ${installedV}, build version: $buildV")

                    // use != instead of > to enable downgrade,
                    if (buildV != installedV) {
                        _kpStateLiveData.postValue(State.KERNELPATCH_NEED_UPDATE)
                    }
                    Log.d(TAG, "kp state: " + _kpStateLiveData.value)

                    if (File(NEED_REBOOT_FILE).exists()) {
                        _kpStateLiveData.postValue(State.KERNELPATCH_NEED_REBOOT)
                    }
                    Log.d(TAG, "kp state: " + _kpStateLiveData.value)

                    // AndroidPatch version
                    val mgv = Version.getManagerVersion().second
                    val installedApdVInt = Version.installedApdVUInt()
                    Log.d(TAG, "manager version: $mgv, installed apd version: $installedApdVInt")

                    if (Version.installedApdVInt > 0) {
                        _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
                    }

                    if (Version.installedApdVInt > 0 && mgv.toInt() != Version.installedApdVInt) {
                        _apStateLiveData.postValue(State.ANDROIDPATCH_NEED_UPDATE)
                        // su path
                        val suPathFile = File(SU_PATH_FILE)
                        if (suPathFile.exists()) {
                            val suPath = suPathFile.readLines()[0].trim()
                            if (Natives.suPath() != suPath) {
                                Log.d(TAG, "su path: $suPath")
                                Natives.resetSuPath(suPath)
                            }
                        }
                    }
                    Log.d(TAG, "ap state: " + _apStateLiveData.value)

                    return@thread
                }
            }
    }

    override fun onCreate() {
        super.onCreate()
        apApp = this

        val isArm64 = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
        if (!isArm64) {
            Toast.makeText(applicationContext, "Unsupported architecture!", Toast.LENGTH_LONG)
                .show()
            Thread.sleep(5000)
            exitProcess(0)
        }

        if (!BuildConfig.DEBUG && !verifyAppSignature("1x2twMoHvfWUODv7KkRRNKBzOfEqJwRKGzJpgaz18xk=")) {
            while (true) {
                val intent = Intent(Intent.ACTION_DELETE)
                intent.data = Uri.parse("package:$packageName")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                startActivity(intent)
                exitProcess(0)
            }
        }

        // TODO: We can't totally protect superkey from be stolen by root or LSPosed-like injection tools in user space, the only way is don't use superkey,
        // TODO: 1. make me root by kernel
        // TODO: 2. remove all usage of superkey
        sharedPreferences = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        APatchKeyHelper.setSharedPreferences(sharedPreferences)
        superKey = APatchKeyHelper.readSPSuperKey()
    }

    fun getBackupWarningState(): Boolean {
        return sharedPreferences.getBoolean(SHOW_BACKUP_WARN, true)
    }

    fun updateBackupWarningState(state: Boolean) {
        sharedPreferences.edit().putBoolean(SHOW_BACKUP_WARN, state).apply()
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val exceptionMessage = Log.getStackTraceString(e)
        val threadName = t.name
        Log.e(TAG, "Error on thread $threadName:\n $exceptionMessage")
        val intent = Intent(this, CrashHandleActivity::class.java).apply {
            putExtra("exception_message", exceptionMessage)
            putExtra("thread", threadName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        exitProcess(10)
    }
}
