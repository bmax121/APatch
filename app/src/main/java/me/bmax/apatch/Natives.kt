package me.bmax.apatch

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.android.parcel.Parcelize

object Natives {
    init {
        System.loadLibrary("apjni")
    }

    @Immutable
    @Parcelize
    @Keep
    data class Profile (
        var pkg: String,
        var uid: Int = 0,
        var toUid: Int = 0,
        var scontext: String = "u:r:magisk:s0",
    ) : Parcelable {

    }


    private external fun nativeSu(superKey: String, to_uid: Int, scontext: String? ): Int

    fun su(to_uid: Int, scontext: String?): Boolean {
        return nativeSu(APApplication.superKey, to_uid, scontext) == 0
    }

    external fun nativeReady(superKey: String): Boolean

    private external fun nativeSuPath(superKey: String): String

    fun suPath(): String {
        return nativeSuPath(APApplication.superKey)
    }

    private external fun nativeSuUids(superKey: String): IntArray

    fun suUids(): IntArray {
        return nativeSuUids(APApplication.superKey)
    }

    private external fun nativeKernelPatchVersion(superKey: String): Long
    fun kerenlPatchVersion(): Long {
        return nativeKernelPatchVersion(APApplication.superKey)
    }
    external fun nativeLoadKernelPatchModule(superKey: String, modulePath: String): Long
    external fun nativeUnloadKernelPatchModule(superKey: String, modulePath: String): Long
    external fun nativeMakeMeSu(superKey: String, scontext: String?): Long
    external fun nativeThreadSu(superKey: String, uid: Int, scontext: String?): Long
    external fun nativeThreadUnsu(superKey: String, uid: Int): Long
    external private fun nativeGrantSu(superKey: String, uid: Int, to_uid: Int, scontext: String?): Long
    fun grantSu(uid: Int, to_uid: Int, scontext: String?): Long {
        return nativeGrantSu(APApplication.superKey, uid, to_uid, scontext)
    }

    external private fun nativeRevokeSu(superKey: String, uid: Int): Long
    fun revokeSu(uid: Int): Long {
        return nativeRevokeSu(APApplication.superKey, uid)
    }

    external private fun nativeSuProfile(superKey: String, uid: Int): Profile;

    fun suProfile(uid: Int): Profile {
        return nativeSuProfile(APApplication.superKey, uid)
    }

    external private fun nativeResetSuPath(superKey: String, path: String): Boolean
    fun resetSuPath(path: String): Boolean {
        return nativeResetSuPath(APApplication.superKey, path)
    }

}