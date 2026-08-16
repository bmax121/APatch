package me.bmax.apatch.util

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.R

private fun querySELinuxStatus(): Pair<Boolean, String> {
    Shell.Builder.create().build("sh").use { shell ->
        val list = ArrayList<String>()
        val result = shell.newJob().add("getenforce").to(list, list).exec()
        return result.isSuccess to result.out.joinToString("\n").trim()
    }
}

@Composable
fun getSELinuxStatus(): String {
    // The getenforce shell round trip runs once on the IO dispatcher, not on
    // every recomposition of the caller.
    val status by produceState<Pair<Boolean, String>?>(null) {
        value = withContext(Dispatchers.IO) { querySELinuxStatus() }
    }
    val (success, output) = status ?: return stringResource(R.string.home_selinux_status_unknown)
    if (success) {
        return when (output) {
            "Enforcing" -> stringResource(R.string.home_selinux_status_enforcing)
            "Permissive" -> stringResource(R.string.home_selinux_status_permissive)
            "Disabled" -> stringResource(R.string.home_selinux_status_disabled)
            else -> stringResource(R.string.home_selinux_status_unknown)
        }
    }

    return if (output.endsWith("Permission denied")) {
        stringResource(R.string.home_selinux_status_enforcing)
    } else {
        stringResource(R.string.home_selinux_status_unknown)
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