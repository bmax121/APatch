package me.bmax.apatch.util

import android.util.Log
import me.bmax.apatch.APApplication
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.Natives
import me.bmax.apatch.apApp

/**
 * version string is like 0.9.0 or 0.9.0-dev
 * version uint is hex number like: 0x000900
 */
object Version {

    private fun string2UInt(ver: String): UInt {
        val v = ver.trim().split("-")[0]
        val vn = v.split('.')
        val vi = vn[0].toInt().shl(16) + vn[1].toInt().shl(8) + vn[2].toInt()
        return vi.toUInt()
    }

    fun uInt2String(ver: UInt): String {
        return "%d.%d.%d".format(
            ver.and(0xff0000u).shr(16).toInt(),
            ver.and(0xff00u).shr(8).toInt(),
            ver.and(0xffu).toInt()
        )
    }

    fun buildKPVUInt(): UInt {
        val buildVS = BuildConfig.buildKPV
        return string2UInt(buildVS)
    }

    fun buildKPVString(): String {
        return BuildConfig.buildKPV
    }

    /**
     * installed KernelPatch version (installed kpimg)
     */
    fun installedKPVUInt(): UInt {
        return Natives.kernelPatchVersion().toUInt()
    }

    fun installedKPVString(): String {
        return uInt2String(installedKPVUInt())
    }


    private fun installedKPatchVString(): String {
        val resultShell = rootShellForResult("${APApplication.APD_PATH} -V")
        val result = resultShell.out.toString()
        return result.trim().ifEmpty { "0" }
    }

    fun installedKPatchVUInt(): UInt {
        return installedKPatchVString().trim().toUInt(0x10)
    }

    private fun installedApdVString(): String {
        val resultShell = rootShellForResult("${APApplication.APD_PATH} -V")
        installedApdVString = if (resultShell.isSuccess) {
            val result = resultShell.out.toString()
            Log.i("APatch", "[installedApdVString@Version] resultFromShell: ${result}")
            Regex("\\d+").find(result)!!.value
        } else {
            "0"
        }
        return installedApdVString
    }

    fun installedApdVUInt(): Int {
        installedApdVInt = installedApdVString().toInt()
        return installedApdVInt
    }


    @Suppress("DEPRECATION")
    fun getManagerVersion(): Pair<String, Int> {
        val packageInfo = apApp.packageManager.getPackageInfo(apApp.packageName, 0)
        return Pair(packageInfo.versionName, packageInfo.versionCode)
    }

    var installedApdVInt: Int = 0
    var installedApdVString: String = "0"
}