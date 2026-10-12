package me.bmax.apatch.util

import android.util.Log
import me.bmax.apatch.APApplication
import org.ini4j.Ini
import org.json.JSONArray
import java.io.StringReader

private const val TAG = "ModuleStore"

/** Keep filesystem paths safe: no separators, shell metacharacters or traversal. */
fun safeKpmModuleId(name: String): String = name.trim()
    .replace(Regex("[^A-Za-z0-9._-]"), "_")
    .trim('.', '_', '-')
    .take(64)
    .ifEmpty { "kpm" }

/** Identity of an installed module, whichever store it lives in. */
data class InstalledModule(
    val id: String,
    val version: String = "",
    val versionCode: Int = 0,
)

/**
 * Reads the two stores the manager installs into, so a downloaded module can be
 * told apart from one that is already present: the system modules apd keeps
 * under /data/adb/modules, and the persisted KPMs under /data/adb/ap/kpm.
 *
 * A KPM's store id is derived from the `name` in its kptools metadata, which is
 * also what the repository uses as the module id, so ids from the two sides can
 * be compared directly.
 */
object ModuleStore {
    /** System modules by module id; requires root and must run off the main thread. */
    fun listSystemModules(): Map<String, InstalledModule> = runCatching {
        val array = JSONArray(listModules())
        buildMap {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val id = obj.optString("id")
                if (id.isBlank()) continue
                put(id, InstalledModule(id, obj.optString("version"), obj.optInt("versionCode", 0)))
            }
        }
    }.onFailure { Log.w(TAG, "list system modules failed", it) }.getOrDefault(emptyMap())

    /** Persisted KPMs by store id; requires root and must run off the main thread. */
    fun listKpmModules(): Map<String, InstalledModule> = runCatching {
        rootShellForResult(
            "find ${APApplication.KPMS_DIR} -mindepth 1 -maxdepth 1 -type d -print"
        ).out
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { path ->
                val id = safeKpmModuleId(path.substringAfterLast('/'))
                if (id.isEmpty()) null else id to kpmInfo(id)
            }
            .toMap()
    }.onFailure { Log.w(TAG, "list KPM modules failed", it) }.getOrDefault(emptyMap())

    /** The metadata of an installed KPM, read from its persisted file. */
    fun kpmInfo(id: String): InstalledModule {
        val file = "${APApplication.KPMS_DIR}$id/$id.kpm"
        val raw = runCatching {
            rootShellForResult("${APApplication.APATCH_FOLDER}bin/kptools -l -M '$file'")
                .out.joinToString("\n")
        }.getOrDefault("")
        val section = runCatching { Ini(StringReader(raw))["kpm"] }.getOrNull()
        return InstalledModule(
            id = id,
            version = section?.get("version")?.trim().orEmpty(),
        )
    }
}
