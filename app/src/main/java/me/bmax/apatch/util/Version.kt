package me.bmax.apatch.util

import com.topjohnwu.superuser.ShellUtils
import me.bmax.apatch.APApplication
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.Natives
import me.bmax.apatch.apApp

/**
 * version string is like 0.9.0 or 0.9.0-dev
 * version uint is hex number like: 0x000900
 */
object Version {

    fun string2UInt(ver: String): UInt {
        val v = ver.trim().split("-")[0]
        val vn = v.split('.')
        val vi = vn[0].toInt(16).shl(16) + vn[1].toInt(16).shl(8) + vn[2].toInt(16)
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

    /**
     * version of /data/adb/kpatch
     */
    fun installedKPBinVUInt(): UInt {
        val result = ShellUtils.fastCmd("${APApplication.SUPERCMD} ${APApplication.superKey} " +
                "${APApplication.KPATCH_PATH} ${APApplication.superKey} -v")
        var verUInt = 0u
        if(!result.isNullOrEmpty()) {
            verUInt = result.trim().toUInt(0x10)
        }
        return verUInt
    }

    fun installedKPatchVString(): String {
        return uInt2String(installedKPBinVUInt())
    }

    fun getManagerVersion(): Pair<String, Int> {
        val packageInfo = apApp.packageManager.getPackageInfo(apApp.packageName, 0)
        return Pair(packageInfo.versionName, packageInfo.versionCode)
    }

    var installedApVersion: Int = -1
    var installedApVersionString: String = "0"

//    var buildApVersion =

}