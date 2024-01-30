package me.bmax.apatch.ui.viewmodel

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.android.parcel.Parcelize

object KPModel {
    @Immutable
    @Parcelize
    @Keep
    data class KPMInfo(
        var name: String,
        var version: String,
        var license: String,
        var author: String,
        var description: String,
        var args: String
    ): Parcelable{}



}