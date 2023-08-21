package me.bmax.akpatch

object Natives {
    // todo:
    val SUPERCALL_RES_SUCCEED = 0
    val SUPERCALL_RES_FAILED = 1
    val SUPERCALL_RES_NOT_IMPL = 2

    private var superKey = "";

    init {
        System.loadLibrary("akp")
    }
    external fun nativeHello(superKey: String): Long
    external fun nativeInstalled(superKey: String): Boolean
    external fun nativeKernelVersion(superKey: String): Long
    external fun nativeKernelPatchVersion(superKey: String): Long
    external fun nativeLoadKernelPatchModule(superKey: String, modulePath: String): Long
    external fun nativeUnloadKernelPatchModule(superKey: String, modulePath: String): Long
    external fun nativeMakeMeSu(superKey: String): Long
    external fun nativeThreadSu(superKey: String, uid: Int): Long
    external fun nativeThreadUnsu(superKey: String, uid: Int): Long

   fun kernelVersion(): Long {
       return nativeKernelVersion(superKey)
   }
   fun kernelPatchVersion(): Long {
       return nativeKernelPatchVersion(superKey);
   }
   fun loadKernelPatchModule(modulePath: String): Long {
       return nativeLoadKernelPatchModule(superKey, modulePath)
   }
   fun unloadKernelPatchModule(modulePath: String): Long {
       return nativeUnloadKernelPatchModule(superKey, modulePath)
   }
   fun makeMeSu(): Long {
       return nativeMakeMeSu(superKey);
   }
   fun threadSu(tid: Int): Long {
       return nativeThreadSu(superKey, tid)
   }
   fun threadUnsu(tid: Int): Long {
       return nativeThreadUnsu(superKey, tid)
   }

    fun installed(superKey: String): Boolean {
        return nativeInstalled(superKey)
    }

    fun installed(): Boolean {
        return installed(superKey)
    }

    fun setSuperKey(superKey: String) {
        this.superKey = superKey
    }
}