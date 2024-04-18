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
    ) : IExtraInfo

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