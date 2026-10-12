package me.bmax.apatch.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.apApp
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "ModuleRepo"

/**
 * Client for the community module repository (https://repo.apatch.dev).
 *
 * The service publishes a catalogue: every entry names the module and, once its
 * author has set one, the address of the `module.json` document that carries the
 * details (version, download address, description). The document is served
 * verbatim, so parsing it is the client's job — that is what [RepoModuleDoc] is.
 */
object ModuleRepo {
    const val BASE_URL = "https://repo.apatch.dev"

    /** How many catalogue entries are read per request. */
    const val PAGE_SIZE = 30

    /** One entry of `GET /api/modules`. */
    data class RepoModule(
        val id: String,
        val kpm: Boolean,
        val author: String,
        val description: String,
        /**
         * The pointed repository's star count, rounded to two significant digits
         * by the service; 0 when it points at no repository or the count has not
         * been read yet.
         */
        val stars: Int = 0,
    )

    /** A module's `module.json`, as served by `/m/<id>/info`. */
    data class RepoModuleDoc(
        val moduleId: String,
        val name: String,
        val version: String,
        val versionCode: Int,
        val license: String,
        val authors: List<String>,
        val metamodule: Boolean,
        val sourceUrl: String,
        val downloadUrl: String,
        val summary: Map<String, String>,
        val description: Map<String, String>,
    ) {
        /** One-line summary in the device language, falling back to English. */
        fun summaryText(locale: Locale = Locale.getDefault()): String = localized(summary, locale)

        /** Longer description in the device language, falling back to English. */
        fun descriptionText(locale: Locale = Locale.getDefault()): String = localized(description, locale)
    }

    /** Reads one page of the catalogue. [kpm] filters by category when non-null. */
    suspend fun listModules(
        page: Int = 1,
        perPage: Int = PAGE_SIZE,
        kpm: Boolean? = null,
        search: String = "",
    ): List<RepoModule> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api/modules".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", perPage.toString())
            .apply {
                if (kpm != null) addQueryParameter("kpm", if (kpm) "1" else "0")
                if (search.isNotBlank()) addQueryParameter("search", search)
            }
            .build()
        val body = get(url.toString()) ?: return@withContext emptyList()
        val array = runCatching { JSONObject(body).optJSONArray("modules") }.getOrNull() ?: JSONArray()
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val id = obj.optString("id")
            if (id.isBlank()) null else RepoModule(
                id = id,
                kpm = obj.optBoolean("kpm", false),
                author = obj.optString("author"),
                description = obj.optString("description"),
                stars = obj.optInt("stars", 0),
            )
        }
    }

    /**
     * Reads a module's document. Null when the module is unknown, has no pointer
     * set yet, or the service could not fetch it.
     */
    suspend fun moduleDoc(module: RepoModule): RepoModuleDoc? = moduleDoc(module.id, module.kpm)

    suspend fun moduleDoc(id: String, kpm: Boolean): RepoModuleDoc? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/m/$id/info".toHttpUrl().newBuilder()
            .apply { if (kpm) addQueryParameter("kpm", "1") }
            .build()
        val body = get(url.toString()) ?: return@withContext null
        parseDoc(body)
    }

    /**
     * Downloads a module's file, trying the address the document names first and
     * falling back to the repository's own relay.
     *
     * The relay is the fallback rather than the primary because a direct download
     * costs the repository nothing and is one hop shorter. It is also the reason
     * the fallback exists at all: authors publish `http://` links, and the app's
     * network policy refuses cleartext before a byte is exchanged, so those links
     * can only be served by the relay. Such an address is skipped rather than
     * retried as `https`, which is what a host may or may not serve.
     */
    suspend fun downloadModuleFile(
        id: String,
        kpm: Boolean,
        fileName: String,
        authorUrl: String = "",
        cancel: DownloadCancelHandle? = null,
        onProgress: (Int) -> Unit = {},
    ): File? {
        val candidates = LinkedHashSet<String>()
        val author = authorUrl.trim()
        when {
            author.startsWith("https://", ignoreCase = true) -> candidates += author
            author.isNotBlank() -> Log.i(TAG, "$id: author url is not https, using the relay")
        }
        candidates += downloadUrl(id, kpm)
        for (url in candidates) {
            downloadToCache(url, fileName, cancel, onProgress)?.let { return it }
            // A cancelled download is the user's decision, not a failed address:
            // moving on to the fallback would start over what they just stopped.
            if (cancel?.isCancelled == true) return null
        }
        return null
    }

    /**
     * The repository's relay address for a module's file: the service streams the
     * bytes its document points at, so a client that cannot reach the author's
     * host (or may not, over cleartext) can still download the module.
     */
    fun downloadUrl(id: String, kpm: Boolean): String =
        "$BASE_URL/m/$id/download.zip" + if (kpm) "?kpm=1" else ""

    /** Parses a `module.json` document; null when it is not valid JSON. */
    fun parseDoc(body: String): RepoModuleDoc? = runCatching {
        val obj = JSONObject(body)
        val moduleId = obj.optString("moduleid")
        RepoModuleDoc(
            moduleId = moduleId,
            name = obj.optString("modulename").ifBlank { moduleId },
            version = obj.optString("version"),
            versionCode = if (obj.has("VersionCode")) obj.optInt("VersionCode") else obj.optInt("versionCode"),
            license = obj.optString("license"),
            authors = obj.optJSONArray("authors").toStringList(),
            metamodule = obj.optBoolean("metamodule", false),
            sourceUrl = obj.optString("sourceUrl"),
            downloadUrl = obj.optString("downloadUrl").ifBlank { obj.optString("download_url") }.trim(),
            summary = obj.optJSONObject("summary").toStringMap(),
            description = obj.optJSONObject("description").toStringMap(),
        )
    }.onFailure { Log.w(TAG, "parse module document failed", it) }.getOrNull()

    /**
     * Renders a star count the way the repository's own pages do — `7`, `99`,
     * `120`, `4.3k`, `12k`, `990k`, `1.2M` — so the app and the site never show
     * two different numbers for the same module. The value is already rounded to
     * two significant digits by the service; this only picks the suffix.
     */
    fun formatStars(stars: Int): String = when {
        stars < 1000 -> stars.toString()
        stars < 1_000_000 -> {
            val thousands = stars / 1000.0
            if (thousands >= 100) "${thousands.roundToInt()}k" else "${trimZero(thousands)}k"
        }
        else -> trimZero(stars / 1_000_000.0) + "M"
    }

    private fun trimZero(value: Double): String {
        val tenths = (value * 10).roundToInt()
        return if (tenths % 10 == 0) (tenths / 10).toString() else "%.1f".format(Locale.ROOT, tenths / 10.0)
    }

    /**
     * Whether [remote] is a release other than the installed one. A version code
     * is preferred when both sides have one; otherwise the version text decides.
     */
    fun isNewerVersion(remote: RepoModuleDoc, installedVersion: String, installedVersionCode: Int): Boolean {
        if (remote.versionCode > 0 && installedVersionCode > 0) {
            return remote.versionCode > installedVersionCode
        }
        return remote.version.isNotBlank() && remote.version != installedVersion
    }

    private fun localized(texts: Map<String, String>, locale: Locale): String {
        if (texts.isEmpty()) return ""
        for (key in listOf(locale.toLanguageTag(), locale.language, "en")) {
            texts[key]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return texts.values.firstOrNull { it.isNotBlank() } ?: ""
    }

    private fun get(url: String): String? = runCatching {
        apApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "GET $url -> ${response.code}")
                null
            } else {
                response.body.string()
            }
        }
    }.onFailure { Log.w(TAG, "GET $url failed", it) }.getOrNull()

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { value -> value.isNotBlank() } }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return keys().asSequence()
            .mapNotNull { key -> optString(key).takeIf { it.isNotBlank() }?.let { key to it } }
            .toMap()
    }
}
