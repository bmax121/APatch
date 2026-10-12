package me.bmax.apatch.util

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.apApp
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.IOException
import androidx.core.content.ContextCompat

private const val TAG = "Downloader"

@SuppressLint("Range")
fun download(
    context: Context,
    url: String,
    fileName: String,
    description: String,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {}
) {
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val query = DownloadManager.Query()

    query.setFilterByStatus(DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_PENDING)
    downloadManager.query(query)?.use { cursor ->
        while (cursor.moveToNext()) {
            val uri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI))
            val localUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            val columnTitle = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE))
            if (url == uri || fileName == columnTitle) {
                if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                    onDownloading()
                    return
                } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    onDownloaded(localUri.toUri())
                    return
                }
            }
        }
    }

    val request = DownloadManager.Request(url.toUri()).setDestinationInExternalPublicDir(
        Environment.DIRECTORY_DOWNLOADS, fileName
    ).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setMimeType("application/zip").setTitle(fileName).setDescription(description)

    downloadManager.enqueue(request)
}

/**
 * Cancels a download in flight. The transfer is aborted at the socket, so a
 * cancelled download stops costing bandwidth at once — and it is never retried
 * by a fallback address, because the user asked for it to stop.
 */
class DownloadCancelHandle {
    @Volatile
    private var call: Call? = null

    @Volatile
    var isCancelled = false
        private set

    fun cancel() {
        isCancelled = true
        call?.cancel()
    }

    internal fun attach(call: Call) {
        this.call = call
        // A cancel that arrived before the request was handed to OkHttp still
        // has to take effect.
        if (isCancelled) call.cancel()
    }
}

/**
 * Downloads [url] into the app cache and reports progress in whole percent, as
 * soon as the length is known. Returns the downloaded file, or null when the
 * transfer failed or [cancel] was triggered; a failed or empty download is
 * deleted rather than passed on.
 *
 * The file is handed to the installer as a `file://` Uri, so it has to be
 * readable by this process only — the app cache is exactly that.
 */
suspend fun downloadToCache(
    url: String,
    fileName: String,
    cancel: DownloadCancelHandle? = null,
    onProgress: (Int) -> Unit = {},
): File? = withContext(Dispatchers.IO) {
    val dir = File(apApp.cacheDir, "online").apply { mkdirs() }
    // Downloads are consumed by the install flow right away and never reused,
    // so an earlier one is dead weight from here on.
    dir.listFiles()?.forEach { it.delete() }
    val target = File(dir, sanitizeFileName(fileName))
    runCatching {
        val call = apApp.okhttpClient.newCall(Request.Builder().url(url).build())
        cancel?.attach(call)
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val percent = ((copied * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        }
        target.takeIf { it.length() > 0 } ?: throw IOException("empty file")
    }.onFailure {
        if (cancel?.isCancelled == true) {
            Log.i(TAG, "download $url cancelled")
        } else {
            Log.w(TAG, "download $url failed", it)
        }
        target.delete()
    }.getOrNull()
}

private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('.', '_')
        .take(96)
        .ifEmpty { "module.zip" }

fun checkNewVersion(): LatestVersionInfo {
    val url = "https://api.github.com/repos/bmax121/APatch/releases/latest"
    val defaultValue = LatestVersionInfo()
    runCatching {
        apApp.okhttpClient.newCall(okhttp3.Request.Builder().url(url).build()).execute()
            .use { response ->
                if (!response.isSuccessful) {
                    return defaultValue
                }
                val body = response.body?.string() ?: return defaultValue

                val json = org.json.JSONObject(body)
                val changelog = json.optString("body")
                val versionCode = json.getInt("name")

                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (!name.endsWith(".apk")) {
                        continue
                    }
                    val downloadUrl = asset.getString("browser_download_url")

                    return LatestVersionInfo(
                        versionCode, downloadUrl, changelog
                    )
                }
            }
    }
    return defaultValue
}

@Composable
fun DownloadListener(context: Context, onDownloaded: (Uri) -> Unit) {
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("Range")
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val id = intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, -1
                    )
                    val downloadManager = context?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    val query = DownloadManager.Query().setFilterById(id)

                    downloadManager?.query(query)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                val uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                                uriString?.toUri()?.let { onDownloaded(it) }
                            }
                        }
                    }
                }
            }
        }
        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            context,
            receiver,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}
