package me.bmax.apatch.ui.viewmodel

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

object KPModel {

    enum class TriggerEvent(val event: String) {
        PAGING_INIT("paging-init"),
        PRE_KERNEL_INIT("pre-kernel-init"),
        POST_KERNEL_INIT("post-kernel-init"),
    }


    enum class ExtraType(val desc: String) {
        NONE("none"),
        KPM("kpm"),
        SHELL("shell"),
        EXEC("exec"),
        RAW("raw"),
        ANDROID_RC("android_rc");
    }

    interface IExtraInfo : Parcelable {
        var type: ExtraType
        var name: String
        var event: String
        var args: String
    }

    @Immutable
    @Parcelize
    @Keep
    data class KPMInfo(
        override var type: ExtraType,
        override var name: String,
        override var event: String,
        override var args: String,
        var version: String,
        var license: String,
        var author: String,
        var description: String,
        /** Directory name used by the file-backed KPM store. */
        var moduleId: String = "",
        /** file, embedded, or empty when the kernel did not report a source. */
        var loadSource: String = "",
        var installed: Boolean = false,
        var disabled: Boolean = false,
        /**
         * Version reported by the persisted file, which differs from [version]
         * while a loaded module has not been reloaded since the file changed.
         */
        var storedVersion: String = "",
    ) : IExtraInfo

    /** A newer release the repository offers for an installed KPM. */
    @Immutable
    @Keep
    data class KpmUpdateInfo(
        val version: String,
        /** The address the module document names; the repository relay backs it up. */
        val authorUrl: String = "",
    )

    @Immutable
    @Parcelize
    @Keep
    data class KPImgInfo(
        var version: String,
        var compileTime: String,
        var config: String,
        var superKey: String,
        var rootSuperkey: String
    ) : Parcelable

    @Immutable
    @Parcelize
    @Keep
    data class KImgInfo(
        var banner: String,
        var patched: Boolean,
    ) : Parcelable

}
