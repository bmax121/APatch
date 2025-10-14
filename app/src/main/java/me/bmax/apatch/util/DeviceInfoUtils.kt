package me.bmax.apatch.util

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.topjohnwu.superuser.io.SuFile
import me.bmax.apatch.R

@Composable
fun getSELinuxStatus() = SuFile("/sys/fs/selinux/enforce").run {
    when {
        !exists() -> stringResource(R.string.home_selinux_status_disabled)
        !isFile -> stringResource(R.string.home_selinux_status_unknown)
        !canRead() -> stringResource(R.string.home_selinux_status_enforcing)
        else -> when (runCatching { newInputStream() }.getOrNull()?.bufferedReader()
            ?.use { it.runCatching { readLine() }.getOrNull()?.trim()?.toIntOrNull() }) {
            1 -> stringResource(R.string.home_selinux_status_enforcing)
            0 -> stringResource(R.string.home_selinux_status_permissive)
            else -> stringResource(R.string.home_selinux_status_unknown)
        }
    }
}

private fun getSystemProperty(key: String): Boolean {
    try {
        val c = Class.forName("android.os.SystemProperties")
        val get = c.getMethod(
            "getBoolean",
            String::class.java,
            Boolean::class.javaPrimitiveType
        )
        return get.invoke(c, key, false) as Boolean
    } catch (e: Exception) {
        Log.e("APatch", "[DeviceUtils] Failed to get system property: ", e)
    }
    return false
}

// Check to see if device supports A/B (seamless) system updates
fun isABDevice(): Boolean {
    return getSystemProperty("ro.build.ab_update")
}