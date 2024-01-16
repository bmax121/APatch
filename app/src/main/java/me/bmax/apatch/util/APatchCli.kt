package me.bmax.apatch.util

import android.net.Uri
import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import me.bmax.apatch.APApplication
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.Natives
import me.bmax.apatch.apApp
import org.json.JSONArray
import java.io.File
import kotlin.concurrent.thread

private const val TAG = "APatchCli"

private fun getKPatchPath(): String {
    return apApp.applicationInfo.nativeLibraryDir + File.separator + "libkpatch.so"
}

object APatchCli {
    val SHELL: Shell = createRootShell()
}

fun getRootShell(): Shell {
    return APatchCli.SHELL
}

fun createRootShell(): Shell {
    Shell.enableVerboseLogging = BuildConfig.DEBUG
    val builder = Shell.Builder.create().apply {
        setFlags(Shell.FLAG_MOUNT_MASTER)
    }
    return try {
        builder.build(
            getKPatchPath(),
            APApplication.superKey,
            "su",
            "-x",
            APApplication.MAGISK_SCONTEXT
        )
    } catch (e: Throwable) {
        Log.e(TAG, "su failed: ", e)
        builder.build("sh")
    }
}

fun createRootShellForLog(): Shell {
    Shell.enableVerboseLogging = BuildConfig.DEBUG
    val builder = Shell.Builder.create()
    return try {
        builder.build(
            getKPatchPath(),
            APApplication.superKey,
            "su",
            "-x",
            APApplication.MAGISK_SCONTEXT
        )
    } catch (e: Throwable) {
        Log.e(TAG, "su failed: ", e)
        return try {
            Log.e(TAG, "retry su: ", e)
            builder.build("su")
        } catch (e: Throwable) {
            Log.e(TAG, "retry su failed: ", e)
            builder.build("sh")
        }
    }
}

fun execApd(args: String): Boolean {
    val shell = getRootShell()
    return ShellUtils.fastCmdResult(shell, "${APApplication.APD_PATH} $args")
}

fun listModules(): String {
    val shell = getRootShell()
    val out =
        shell.newJob().add("${APApplication.APD_PATH} module list").to(ArrayList(), null).exec().out
    return out.joinToString("\n").ifBlank { "[]" }
}

fun getModuleCount(): Int {
    val result = listModules()
    runCatching {
        val array = JSONArray(result)
        return array.length()
    }.getOrElse { return 0 }
}

fun getSuperuserCount(): Int {
    val shell = getRootShell()
    val out = shell.newJob().add("${APApplication.KPATCH_PATH} ${APApplication.superKey} sumgr list | wc -l").to(ArrayList(), null).exec().out
    return out[0].toIntOrNull() ?: 0
}

fun toggleModule(id: String, enable: Boolean): Boolean {
    val cmd = if (enable) {
        "module enable $id"
    } else {
        "module disable $id"
    }
    val result = execApd(cmd)
    Log.i(TAG, "$cmd result: $result")
    return result
}

fun uninstallModule(id: String): Boolean {
    val cmd = "module uninstall $id"
    val result = execApd(cmd)
    Log.i(TAG, "uninstall module $id result: $result")
    return result
}

fun installModule(
    uri: Uri,
    onFinish: (Boolean) -> Unit,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): Boolean {
    val resolver = apApp.contentResolver
    with(resolver.openInputStream(uri)) {
        val file = File(apApp.cacheDir, "module.zip")
        file.outputStream().use { output ->
            this?.copyTo(output)
        }
        val cmd = "module install ${file.absolutePath}"

        val shell = getRootShell()

        val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                onStdout(s ?: "")
            }
        }

        val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                onStderr(s ?: "")
            }
        }

        val result =
            shell.newJob().add("${APApplication.APD_PATH} $cmd").to(stdoutCallback, stderrCallback)
                .exec()
        Log.i(TAG, "install module $uri result: $result")

        file.delete()

        onFinish(result.isSuccess)
        return result.isSuccess
    }
}

fun reboot(reason: String = "") {
    thread {
        Natives.su(0, "")
        if (reason == "recovery") {
            // KEYCODE_POWER = 26, hide incorrect "Factory data reset" message
            ShellUtils.fastCmd("/system/bin/input keyevent 26")
        }
        ShellUtils.fastCmd("/system/bin/svc power reboot $reason || /system/bin/reboot $reason")
    }
}

fun overlayFsAvailable(): Boolean {
    val shell = getRootShell()
    return ShellUtils.fastCmdResult(shell, "cat /proc/filesystems | grep overlay")
}

fun hasMagisk(): Boolean {
    val shell = getRootShell()
    val result = shell.newJob().add("nsenter --mount=/proc/1/ns/mnt which magisk").exec()
    Log.i(TAG, "has magisk: ${result.isSuccess}")
    return result.isSuccess
}

fun isGlobalNamespaceEnabled(): Boolean {
    val shell = getRootShell()
    val result =
        ShellUtils.fastCmd(shell, "nsenter --mount=/proc/1/ns/mnt cat ${APApplication.GLOBAL_NAMESPACE_FILE}")
    Log.i(TAG, "is global namespace enabled: $result")
    return result == "1"
}

fun setGlobalNamespaceEnabled(value: String) {
    getRootShell().newJob()
        .add("nsenter --mount=/proc/1/ns/mnt echo $value > ${APApplication.GLOBAL_NAMESPACE_FILE}")
        .submit { result ->
            Log.i(TAG, "setGlobalNamespaceEnabled result: ${result.isSuccess} [${result.out}]")
        }
}

fun forceStopApp(packageName: String) {
    val shell = getRootShell()
    val result = shell.newJob().add("am force-stop $packageName").exec()
    Log.i(TAG, "force stop $packageName result: $result")
}

fun launchApp(packageName: String) {
    val shell = getRootShell()
    val result =
        shell.newJob().add("monkey -p $packageName -c android.intent.category.LAUNCHER 1").exec()
    Log.i(TAG, "launch $packageName result: $result")
}

fun restartApp(packageName: String) {
    forceStopApp(packageName)
    launchApp(packageName)
}
