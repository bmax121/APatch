package me.bmax.apatch.util

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import me.bmax.apatch.APApplication
import me.bmax.apatch.APApplication.Companion.SUPERCMD
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.screen.MODULE_TYPE
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

private const val TAG = "APatchCli"

@Suppress("DEPRECATION")
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

object APatchCli {
    var SHELL: Shell = createRootShell()
    val GLOBAL_MNT_SHELL: Shell = createRootShell(true)
    fun refresh() {
        val tmp = SHELL
        SHELL = createRootShell()
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
     val result = withNewRootShell{ 
        newJob().add("cp /data/user/*/me.bmax.apatch/patch/ori.img /data/adb/ap/ && rm /data/user/*/me.bmax.apatch/patch/ori.img")
        .to(ArrayList(),null).exec()
    }
    return out.joinToString("\n").ifBlank { "[]" }
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

fun installModule(
    uri: Uri, type: MODULE_TYPE, onFinish: (Boolean) -> Unit, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    val resolver = apApp.contentResolver
    with(resolver.openInputStream(uri)) {
        val file = File(apApp.cacheDir, "module_" + type + ".zip")
        file.outputStream().use { output ->
            this?.copyTo(output)
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
            result = shell.newJob().add("$cmd").to(stdoutCallback, stderrCallback)
                    .exec().isSuccess
        } else {
//            ZipUtils.
        }

        Log.i(TAG, "install $type module $uri result: $result")

        file.delete()

        onFinish(result)
        return result
    }
}

fun runAPModuleAction(
    moduleId: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
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

    val result = withNewRootShell{ 
        newJob().add("${APApplication.APD_PATH} module action $moduleId")
        .to(stdoutCallback, stderrCallback).exec()
    }
    Log.i(TAG, "APModule runAction result: $result")

    return result.isSuccess
}

fun reboot(reason: String = "") {
    if (reason == "recovery") {
        // KEYCODE_POWER = 26, hide incorrect "Factory data reset" message
        getRootShell().newJob().add("/system/bin/input keyevent 26").exec()
    }
    getRootShell().newJob()
        .add("/system/bin/svc power reboot $reason || /system/bin/reboot $reason").exec()
}

fun overlayFsAvailable(): Boolean {
    return true
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

@Suppress("DEPRECATION")
private fun signatureFromAPI(context: Context): ByteArray? {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
        }

        val signatures: Array<out Signature>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                packageInfo.signatures
            }

        signatures?.firstOrNull()?.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun signatureFromAPK(context: Context): ByteArray? {
    var signatureBytes: ByteArray? = null
    try {
        ZipFile(context.packageResourcePath).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements() && signatureBytes == null) {
                val entry = entries.nextElement()
                if (entry.name.matches("(META-INF/.*)\\.(RSA|DSA|EC)".toRegex())) {
                    zipFile.getInputStream(entry).use { inputStream ->
                        val certFactory = CertificateFactory.getInstance("X509")
                        val x509Cert =
                            certFactory.generateCertificate(inputStream) as X509Certificate
                        signatureBytes = x509Cert.encoded
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return signatureBytes
}

private fun validateSignature(signatureBytes: ByteArray?, validSignature: String): Boolean {
    signatureBytes ?: return false
    val digest = MessageDigest.getInstance("SHA-256")
    val signatureHash = Base64.encodeToString(digest.digest(signatureBytes), Base64.NO_WRAP)
    return signatureHash == validSignature
}

fun verifyAppSignature(validSignature: String): Boolean {
    val context = apApp.applicationContext
    val apkSignature = signatureFromAPK(context)
    val apiSignature = signatureFromAPI(context)

    return validateSignature(apiSignature, validSignature) && validateSignature(
        apkSignature,
        validSignature
    )
}
