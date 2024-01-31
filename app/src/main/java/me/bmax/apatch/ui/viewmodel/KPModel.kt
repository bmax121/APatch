package me.bmax.apatch.ui.viewmodel

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.android.parcel.Parcelize

object KPModel {
    enum class ExtraType {
        NONE,
        KPM,
        SHELL,
        EXEC,
        RAW
    }

    interface IExtraInfo: Parcelable {
        var type: ExtraType
        var name: String
    }

    @Immutable
    @Parcelize
    @Keep
    data class KPMInfo(
        override var type: ExtraType,
        override var name: String,
        var version: String,
        var license: String,
        var author: String,
        var description: String,
        var args: String,
    ): IExtraInfo {}

    @Immutable
    @Parcelize
    @Keep
    data class KPImgInfo(
        var version: String,
        var compileTime: String,
        var config: String,
        val superKey: String
    ): Parcelable {}

    @Immutable
    @Parcelize
    @Keep
    data class KImgInfo(
        var banner: String,
        var patched: Boolean
    ): Parcelable {}


    @Immutable
    @Parcelize
    @Keep
    data class ImgPatchInfo(
        var kimgInfo: KImgInfo,
        var kpimgInfo: KPImgInfo,
        var existedExtras: MutableList<IExtraInfo>,
        var addedExtras: MutableList<IExtraInfo>
    ): Parcelable{}

}