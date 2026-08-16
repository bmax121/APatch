package me.bmax.apatch.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options

class AppIconFetcher(
    private val context: Context,
    private val applicationInfo: ApplicationInfo,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val drawable: Drawable = applicationInfo.loadIcon(context.packageManager)
        return ImageFetchResult(
            image = drawable.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<PackageInfo> {
        override fun create(
            data: PackageInfo,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val applicationInfo = data.applicationInfo ?: return null
            return AppIconFetcher(context, applicationInfo)
        }
    }
}

class AppIconKeyer : Keyer<PackageInfo> {
    override fun key(data: PackageInfo, options: Options): String? =
        "${data.packageName}:${data.lastUpdateTime}"
}
