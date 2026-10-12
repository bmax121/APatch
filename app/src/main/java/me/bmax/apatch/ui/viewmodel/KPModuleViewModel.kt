package me.bmax.apatch.ui.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.util.HanziToPinyin
import me.bmax.apatch.util.ModuleRepo
import me.bmax.apatch.util.rootShellForResult
import me.bmax.apatch.util.safeKpmModuleId
import org.ini4j.Ini
import java.io.StringReader
import java.text.Collator
import java.util.Locale

private const val TAG = "KPModuleViewModel"

/** How long a repository lookup for one KPM is trusted before it is repeated. */
private const val UPDATE_CACHE_TTL_MS = 5 * 60 * 1000L

private fun parseKpmInfo(raw: String, fallbackId: String = ""): KPModel.KPMInfo? = runCatching {
    val section = Ini(StringReader(raw))["kpm"] ?: return null
    val name = section["name"]?.toString()?.trim().orEmpty().ifEmpty { fallbackId }
    KPModel.KPMInfo(
        KPModel.ExtraType.KPM, name, section["load_event"]?.toString().orEmpty(),
        section["args"]?.toString().orEmpty(), section["version"]?.toString().orEmpty(),
        section["license"]?.toString().orEmpty(), section["author"]?.toString().orEmpty(),
        section["description"]?.toString().orEmpty(), safeKpmModuleId(name),
        section["load_source"]?.toString().orEmpty()
    )
}.getOrNull()

private fun parseKernelKpmInfo(raw: String, fallbackName: String): KPModel.KPMInfo? {
    val lines = raw.split('\n')
    if (lines.none { it.startsWith("name=") }) return null
    fun value(key: String) = lines.firstOrNull { it.startsWith("$key=") }
        ?.removePrefix("$key=") ?: ""
    val name = value("name").ifBlank { fallbackName }
    return KPModel.KPMInfo(
        KPModel.ExtraType.KPM,
        name,
        value("load_event"),
        value("args"),
        value("version"),
        value("license"),
        value("author"),
        value("description"),
        safeKpmModuleId(fallbackName),
        value("load_source")
    )
}

class KPModuleViewModel : ViewModel() {
    companion object { private var modules by mutableStateOf<List<KPModel.KPMInfo>>(emptyList()) }

    var search by mutableStateOf("")

    /** Repository releases that are newer than the persisted KPM files, by store id. */
    var updates by mutableStateOf<Map<String, KPModel.KpmUpdateInfo>>(emptyMap())
        private set

    var isRefreshing by mutableStateOf(false)
        private set
    var isNeedRefresh by mutableStateOf(false)
        private set

    /** One repository answer per module, kept briefly so a refresh stays cheap. */
    private data class CachedUpdate(val checkedAt: Long, val storedVersion: String, val update: KPModel.KpmUpdateInfo?)

    private val updateCache = mutableMapOf<String, CachedUpdate>()

    val moduleList by derivedStateOf {
        val comparator = compareBy(Collator.getInstance(Locale.getDefault()), KPModel.KPMInfo::name)
        modules.filter {
            it.name.contains(search, true) || it.moduleId.contains(search, true) ||
                HanziToPinyin.getInstance().toPinyinString(it.name)?.contains(search, true) == true
        }.sortedWith(comparator).also { isRefreshing = false }
    }

    fun markNeedRefresh() { isNeedRefresh = true }

    fun updateModuleDisabled(moduleId: String, disabled: Boolean) {
        modules = modules.map { module ->
            if (module.moduleId == moduleId) module.copy(disabled = disabled) else module
        }
    }

    fun fetchModuleList() {
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing = true
            val start = SystemClock.elapsedRealtime()
            runCatching {
                val result = linkedMapOf<String, KPModel.KPMInfo>()
                val names = if (Natives.kernelPatchModuleNum() > 0) {
                    Natives.kernelPatchModuleList()
                } else {
                    ""
                }
                val loadedIds = names.split('\n')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .toSet()
                names.split('\n').filter(String::isNotBlank).forEach { kernelName ->
                    val lines = Natives.kernelPatchModuleInfo(kernelName).split('\n')
                    val info = KPModel.KPMInfo(
                        KPModel.ExtraType.KPM,
                        lines.firstOrNull { it.startsWith("name=") }?.removePrefix("name=") ?: kernelName,
                        lines.firstOrNull { it.startsWith("load_event=") }?.removePrefix("load_event=") ?: "",
                        lines.firstOrNull { it.startsWith("args=") }?.removePrefix("args=") ?: "",
                        lines.firstOrNull { it.startsWith("version=") }?.removePrefix("version=") ?: "",
                        lines.firstOrNull { it.startsWith("license=") }?.removePrefix("license=") ?: "",
                        lines.firstOrNull { it.startsWith("author=") }?.removePrefix("author=") ?: "",
                        lines.firstOrNull { it.startsWith("description=") }?.removePrefix("description=") ?: "",
                        safeKpmModuleId(kernelName),
                        lines.firstOrNull { it.startsWith("load_source=") }?.removePrefix("load_source=") ?: ""
                    )
                    // load_source=file only describes where this instance was loaded from.
                    // It does not mean the KPM belongs to APatch's persistent install store.
                    // The installed flag is set only when the directory scan below finds
                    // /data/adb/ap/kpm/<id>/<id>.kpm.
                    result[info.moduleId] = info.copy(installed = false, disabled = false)
                }
                val dirs = rootShellForResult("find ${APApplication.KPMS_DIR} -mindepth 1 -maxdepth 1 -type d -print").out
                dirs.map { it.trim().substringAfterLast('/') }.filter(String::isNotBlank).forEach { id ->
                    val file = "${APApplication.KPMS_DIR}$id/$id.kpm"
                    val parsed = rootShellForResult("${APApplication.APATCH_FOLDER}bin/kptools -l -M '$file'")
                        .out.joinToString("\n").let { parseKpmInfo(it, id) } ?: return@forEach
                    val key = safeKpmModuleId(id)
                    val old = result[key]
                    // Refresh the same live metadata exposed by `truncate su module info <name>`.
                    val live = if (id in loadedIds) {
                        parseKernelKpmInfo(Natives.kernelPatchModuleInfo(id), id)
                    } else {
                        null
                    }
                    val current = live ?: old
                    val disabled = rootShellForResult("[ -e '${APApplication.KPMS_DIR}$id/disable' ]").isSuccess
                    result[key] = current?.copy(
                        moduleId = id, installed = true, disabled = disabled,
                        version = current.version.ifBlank { parsed.version },
                        license = current.license.ifBlank { parsed.license },
                        author = current.author.ifBlank { parsed.author },
                        description = current.description.ifBlank { parsed.description },
                        storedVersion = parsed.version
                    ) ?: parsed.copy(
                        moduleId = id, installed = true, disabled = disabled,
                        loadSource = "", storedVersion = parsed.version
                    )
                }
                modules = result.values.toList()
                isNeedRefresh = false
            }.onFailure { Log.e(TAG, "fetchModuleList", it) }
            isRefreshing = false
            Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}, modules: ${modules.size}")

            // The list is shown first; the repository lookups that decide which
            // entries have an update land a moment later, and an unreachable
            // repository must not hold the refresh indicator.
            val installed = modules
            launch { updates = checkOnlineUpdates(installed) }
        }
    }

    /**
     * Re-checks the repository for the modules already in the list. The list
     * itself outlives a screen visit, so a returning screen has to ask for the
     * updates its fresh instance does not have yet.
     */
    fun refreshOnlineUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            updates = checkOnlineUpdates(modules)
        }
    }

    private suspend fun checkOnlineUpdates(
        installed: List<KPModel.KPMInfo>,
    ): Map<String, KPModel.KpmUpdateInfo> = coroutineScope {
        val now = SystemClock.elapsedRealtime()
        installed
            .filter { it.installed && it.loadSource != "embedded" && it.moduleId.isNotBlank() }
            .map { module ->
                async {
                    val id = module.moduleId
                    val stored = module.storedVersion.ifBlank { module.version }
                    val cached = updateCache[id]
                    if (cached != null && cached.storedVersion == stored && now - cached.checkedAt < UPDATE_CACHE_TTL_MS) {
                        return@async cached.update?.let { id to it }
                    }
                    val doc = runCatching { ModuleRepo.moduleDoc(id, kpm = true) }.getOrNull()
                    val update = doc?.takeIf {
                        it.version.isNotBlank() && it.downloadUrl.isNotBlank() && it.version != stored
                    }?.let { KPModel.KpmUpdateInfo(it.version, it.downloadUrl) }
                    updateCache[id] = CachedUpdate(now, stored, update)
                    update?.let { id to it }
                }
            }
            .awaitAll()
            .filterNotNull()
            .toMap()
    }
}
