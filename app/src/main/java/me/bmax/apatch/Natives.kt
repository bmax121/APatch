package me.bmax.apatch

object Natives {
    init {
        System.loadLibrary("apjni")
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

    private external fun nativeKernelPatchVersion(superKey: String): Long
    fun kerenlPatchVersion(): Long {
        return nativeKernelPatchVersion(APApplication.superKey)
    }
    external fun nativeLoadKernelPatchModule(superKey: String, modulePath: String): Long
    external fun nativeUnloadKernelPatchModule(superKey: String, modulePath: String): Long
    external fun nativeMakeMeSu(superKey: String, scontext: String?): Long
    external fun nativeThreadSu(superKey: String, uid: Int, scontext: String?): Long
    external fun nativeThreadUnsu(superKey: String, uid: Int): Long
    external fun nativeGrantSu(superKey: String, uid: Int): Long
    external fun nativeRevokeSu(superKey: String, uid: Int): Long
    external fun nativeGetAllowList(superKey: String): IntArray
    external fun nativeResetSu(superKey: String, cmd: String): Long

}