package me.bmax.apatch.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

object ManagerAuth {
    // Keep in sync with KernelPatch's trusted_managers in patch/android/userd.c.
    private val trustedSigners = mapOf(
        "me.bmax.apatch" to "d71dadc0ca07bdf594383bfb2a445134a07339f12a27044a1b326981acf5f319",
        "com.example.apatch" to "e51133125fef56aa528391fcc20494ebb538bd8e093d6c475d6d002a7a121a8f",
    )

    @Suppress("DEPRECATION")
    fun supportsSignatureAuth(context: Context): Boolean = runCatching {
        val expected = trustedSigners[context.packageName] ?: return false
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
                    else PackageManager.GET_SIGNATURES
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners
                      else info.signatures
        val signer = signers?.singleOrNull() ?: return false
        val digest = MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())
            .joinToString("") { "%02x".format(it) }
        digest == expected && ApkSignatureFormat.isV2Only(File(context.applicationInfo.sourceDir))
    }.getOrDefault(false)

    fun isValidSuperKey(key: String): Boolean =
        key.toByteArray(Charsets.UTF_8).size in 8..63 && key.any { it.isDigit() } && key.any { it.isLetter() }
}
