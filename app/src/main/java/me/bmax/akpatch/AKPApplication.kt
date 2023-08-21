package me.bmax.akpatch

import android.app.Application
import androidx.compose.runtime.compositionLocalOf
import coil.Coil
import coil.ImageLoader
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer

lateinit var pApp: AKPApplication
val sckey = compositionLocalOf<String> { "" }
val TAG: String = "AKPatch";

class AKPApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        pApp = this

        val context = this
        val iconSize = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(iconSize, false, context))
                }
                .build()
        )
    }


}