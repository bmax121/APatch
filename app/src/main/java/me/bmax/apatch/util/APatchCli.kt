package me.bmax.apatch.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.internal.MainShell
import com.topjohnwu.superuser.io.SuFile
import me.bmax.apatch.APApplication
import me.bmax.apatch.APApplication.Companion.SUPERCMD
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.screen.MODULE_TYPE
import java.io.File
import java.util.Properties

private const val TAG = "APatchCli"

private fun getKPatchPath(): String {
    return apApp.applicationInfo.nativeLibraryDir + File.separator + "libkpatch.so"
}

class RootShellInitializer : Shell.Initializer() {
    override fun onInit(context: Context, shell: Shell): Boolean {
        shell.newJob().add("export PATH=\$PATH:/system_ext/bin:/vendor/bin").exec()
        return true
    }
}

fun createRootShell(globalMnt: Boolean = false): Shell {
    Shell.enableVerboseLogging = BuildConfig.DEBUG
    val builder = Shell.Builder.create().setInitializers(RootShellInitializer::class.java)
    return try {
        builder.build(
            SUPERCMD, APApplication.superKey, "-Z", APApplication.MAGISK_SCONTEXT
        )
    } catch (e: Throwable) {
        Log.e(TAG, "su failed: ", e)
        return try {
            Log.e(TAG, "retry compat kpatch su")
            if (globalMnt) {
                builder.build(
                    getKPatchPath(), APApplication.superKey, "su", "-Z", APApplication.MAGISK_SCONTEXT, "--mount-master"
                )
            }else{
                builder.build(
                    getKPatchPath(), APApplication.superKey, "su", "-Z", APApplication.MAGISK_SCONTEXT
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "retry kpatch su failed: ", e)
            return try {
                Log.e(TAG, "retry su: ", e)
                if (globalMnt) {
                    builder.build("su","-mm")
                }else{
                    builder.build("su")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "retry su failed: ", e)
                return builder.build("sh")
            }
        }
    }
}

private fun createMainRootShell() : Shell {
    val builder = Shell.Builder.create()
        .setInitializers(RootShellInitializer::class.java)
    val shell = try {
        builder.build(SUPERCMD, APApplication.superKey, "-Z", APApplication.MAGISK_SCONTEXT)
    } catch (e: Throwable) {
        Log.e(TAG, "su failed: ", e)
        builder.setCommands(getKPatchPath(), APApplication.superKey, "su", "-Z", APApplication.MAGISK_SCONTEXT)
        try {
            builder.build()
        } catch (e: Throwable) {
            Log.e(TAG, "retry kpatch su failed: ", e)
            builder.setCommands("su")
            try {
                builder.build()
            } catch (e: Throwable) {
                Log.e(TAG, "retry su failed: ", e)
                builder.setCommands("sh")
                builder.build()
            }
        }
    }

    MainShell.setBuilder(builder)
    return shell
}

object APatchCli {
    @Volatile
    var SHELL: Shell = createMainRootShell()
    val GLOBAL_MNT_SHELL: Shell = createRootShell(true)

    // Serialized so a reader can never observe the half-reset MainShell (private
    // fields cleared via reflection) between the reset and the SHELL swap.
    @Synchronized
    fun refresh() {
        val tmp = SHELL

        val clazz = MainShell::class.java // reset MainShell
        clazz.getDeclaredField("isInitMain").apply {
            isAccessible = true
            setBoolean(null, false)
            isAccessible = false
        }

        clazz.getDeclaredField("mainShell").apply {
            isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val arr = get(null) as Array<Any?>
            arr[0] = null
            isAccessible = false
        }

        clazz.getDeclaredField("mainBuilder").apply {
            isAccessible = true
            set(null, null)
            isAccessible = false
        }

        SHELL = createMainRootShell()
        tmp.close()
    }
}

fun getRootShell(globalMnt: Boolean = false): Shell {

    return if (globalMnt) APatchCli.GLOBAL_MNT_SHELL else {
        APatchCli.SHELL
    }
}

inline fun <T> withNewRootShell(
    globalMnt: Boolean = false,
    block: Shell.() -> T
): T {
    return createRootShell(globalMnt).use(block)
}

fun rootAvailable(): Boolean {
    val shell = getRootShell()
    return shell.isRoot
}

fun tryGetRootShell(): Shell {
    Shell.enableVerboseLogging = BuildConfig.DEBUG
    val builder = Shell.Builder.create()
    return try {
        builder.build(
            SUPERCMD, APApplication.superKey, "-Z", APApplication.MAGISK_SCONTEXT
        )
    } catch (e: Throwable) {
        Log.e(TAG, "su failed: ", e)
        return try {
            Log.e(TAG, "retry compat kpatch su")
            builder.build(
                getKPatchPath(), APApplication.superKey, "su", "-Z", APApplication.MAGISK_SCONTEXT
            )
        } catch (e: Throwable) {
            Log.e(TAG, "retry kpatch su failed: ", e)
            return try {
                Log.e(TAG, "retry su: ", e)
                builder.build("su")
            } catch (e: Throwable) {
                Log.e(TAG, "retry su failed: ", e)
                builder.build("sh")
            }
        }
    }
}

fun shellForResult(shell: Shell, vararg cmds: String): Shell.Result {
    val out = ArrayList<String>()
    val err = ArrayList<String>()
    return shell.newJob().add(*cmds).to(out, err).exec()
}

fun rootShellForResult(vararg cmds: String): Shell.Result {
    val out = ArrayList<String>()
    val err = ArrayList<String>()
    return getRootShell().newJob().add(*cmds).to(out, err).exec()
}

fun execApd(args: String, newShell: Boolean = false): Boolean {
    return if (newShell) {
        withNewRootShell {
            ShellUtils.fastCmdResult(this, "${APApplication.APD_PATH} $args")
        }
    } else {
        ShellUtils.fastCmdResult(getRootShell(), "${APApplication.APD_PATH} $args")
    }
}

fun listModules(): String {
    val shell = getRootShell()
    val out =
        shell.newJob().add("${APApplication.APD_PATH} module list").to(ArrayList(), null).exec().out
    return out.joinToString("\n").ifBlank { "[]" }
}

// Devices patched via PATCH_ONLY and flashed manually (e.g. fastboot) never go
// through the patch-completion handoff, so their stock boot backup is still in
// the app-private patch dir. Move it next to apd once root is available;
// idempotent and a no-op when nothing is pending.
fun migrateStockBootBackup() {
    withNewRootShell {
        newJob().add(
            "mkdir -p /data/adb/ap && cp /data/user/*/me.bmax.apatch/patch/ori.img /data/adb/ap/ 2>/dev/null && rm -f /data/user/*/me.bmax.apatch/patch/ori.img; true"
        ).exec()
    }
}

fun hasMetaModule(): Boolean {
    return getMetaModuleImplement() != "None"
}

fun getMetaModuleImplement(): String {
    try {
        val metaModuleProp = SuFile.open("/data/adb/metamodule/module.prop")
        if (!metaModuleProp.isFile) {
            Log.i(TAG, "Meta module implement: None")
            return "None"
        }

        val prop = Properties()
        metaModuleProp.newInputStream().use { prop.load(it) }

        val name = prop.getProperty("name")
        Log.i(TAG, "Meta module implement: $name")
        return name
    } catch (t : Throwable) {
        Log.i(TAG, "Meta module implement: None")
        return "None"
    }
}

fun toggleModule(id: String, enable: Boolean): Boolean {
    val cmd = if (enable) {
        "module enable $id"
    } else {
        "module disable $id"
    }
    val result = execApd(cmd,true)
    Log.i(TAG, "$cmd result: $result")
    return result
}

fun uninstallModule(id: String): Boolean {
    val cmd = "module uninstall $id"
    val result = execApd(cmd,true)
    Log.i(TAG, "uninstall module $id result: $result")
    return result
}

fun undoRemoveModule(id: String): Boolean {
    val cmd = "module undo-uninstall $id"
    val result = execApd(cmd,true)
    Log.i(TAG, "undo-uninstall module $id result: $result")
    return result
}

fun installModule(
    uri: Uri, type: MODULE_TYPE, onFinish: (Boolean) -> Unit, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    val resolver = apApp.contentResolver
    val file = File(apApp.cacheDir, "module_$type.zip")
    resolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output ->
            input.copyTo(output)
        }
    } ?: run {
        onFinish(false)
        return false
    }

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

    val shell = getRootShell()

    var result = false
    if(type == MODULE_TYPE.APM) {
        val cmd = "${APApplication.APD_PATH} module install ${file.absolutePath}"
        result = shell.newJob().add(cmd).to(stdoutCallback, stderrCallback)
                .exec().isSuccess
    } else {
//            ZipUtils.
    }

    Log.i(TAG, "install $type module $uri result: $result")

    file.delete()

    onFinish(result)
    return result
}

fun runAPModuleAction(
    moduleId: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
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

    val result = withNewRootShell{ 
        newJob().add("${APApplication.APD_PATH} module action $moduleId")
        .to(stdoutCallback, stderrCallback).exec()
    }
    Log.i(TAG, "APModule runAction result: $result")

    return result.isSuccess
}

fun reboot(reason: String = "") {
    if (reason == "soft_reboot") {
        softReboot()
        return
    }
    if (reason == "recovery") {
        // KEYCODE_POWER = 26, hide incorrect "Factory data reset" message
        getRootShell().newJob().add("/system/bin/input keyevent 26").exec()
    }
    getRootShell().newJob()
        .add("/system/bin/svc power reboot $reason || /system/bin/reboot $reason").exec()
}

/** Soft reboot: restart the Android framework while keeping runtime-loaded modules. */
fun softReboot() {
    getRootShell().newJob().add("${APApplication.APD_PATH} soft-reboot").exec()
}

/**
 * Detect the Kernel Module Interface (KMI) of the running kernel, e.g.
 * `android14-5.15`, from `uname -r` (same parsing as KernelSU).
 */
fun getKmi(): String? {
    val release = runCatching { Os.uname().release }.getOrNull() ?: return null
    val m = Regex("(.* )?(\\d+\\.\\d+)(\\S+)?(android\\d+)(.*)").find(release) ?: return null
    return "${m.groupValues[4]}-${m.groupValues[2]}"
}

/** Asset name of the KernelPatch ko matching this device's kernel (KMI). */
fun jailbreakAssetName(): String? {
    val kmi = getKmi() ?: return null
    return "${kmi}_kernelpatch.ko"
}

/** Extract the bundled kernelpatch.ko for this device's kernel to the app files dir. */
fun extractJailbreakKo(): File? {
    val name = jailbreakAssetName() ?: return null
    val file = File(apApp.filesDir, "kernelpatch.ko")
    return runCatching {
        apApp.assets.open(name).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file
    }.getOrNull()
}

/**
 * Install jailbreak mode: extract the bundled kernelpatch.ko for this kernel to
 * the app files dir (no root needed), then trigger the magica chain via the
 * isolated app-zygote service. The apd then escalates to full root through adb
 * and runs `late-load` (loads the module, applies Magisk policy, marks jailbreak).
 */
fun installJailbreak(): Boolean {
    val ko = extractJailbreakKo() ?: return false
    if (!ko.exists() || ko.length() == 0L) {
        Log.e(TAG, "extracted jailbreak ko is missing or empty")
        return false
    }
    return try {
        val intent = Intent(apApp, me.bmax.apatch.magica.MagicaService::class.java)
        apApp.startService(intent)
        Log.i(TAG, "MagicaService started for jailbreak")
        true
    } catch (e: Throwable) {
        Log.e(TAG, "start MagicaService failed: $e")
        false
    }
}

/** Whether the SELinux mode is permissive (getenforce), the prerequisite for jailbreak. */
fun isSELinuxPermissive(): Boolean {
    Shell.Builder.create().build("sh").use { shell ->
        val out = ArrayList<String>()
        val result = shell.newJob().add("getenforce").to(out, ArrayList()).exec()
        return result.isSuccess &&
            out.firstOrNull()?.trim()?.equals("Permissive", ignoreCase = true) == true
    }
}

/** Whether jailbreak mode is active (the ko has been loaded and a marker written). */
fun isJailbreakMode(): Boolean {
    return runCatching { SuFile(APApplication.JAILBREAK_FILE).exists() }.getOrDefault(false)
}

fun hasMagisk(): Boolean {
    val shell = getRootShell()
    val result = shell.newJob().add("nsenter --mount=/proc/1/ns/mnt which magisk").exec()
    Log.i(TAG, "has magisk: ${result.isSuccess}")
    return result.isSuccess
}

fun isGlobalNamespaceEnabled(): Boolean {
    val shell = getRootShell()
    val result = ShellUtils.fastCmd(shell, "cat ${APApplication.GLOBAL_NAMESPACE_FILE}")
    Log.i(TAG, "is global namespace enabled: $result")
    return result == "1"
}

fun setGlobalNamespaceEnabled(value: String) {
    getRootShell().newJob().add("echo $value > ${APApplication.GLOBAL_NAMESPACE_FILE}")
        .submit { result ->
            Log.i(TAG, "setGlobalNamespaceEnabled result: ${result.isSuccess} [${result.out}]")
        }
}

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var fileName: String? = null
    val contentResolver: ContentResolver = context.contentResolver
    val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }
    return fileName
}

