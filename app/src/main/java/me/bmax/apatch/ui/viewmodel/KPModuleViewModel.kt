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
import kotlinx.coroutines.launch
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.util.HanziToPinyin
import me.bmax.apatch.util.rootShellForResult
import org.ini4j.Ini
import java.io.StringReader
import java.text.Collator
import java.util.Locale

private const val TAG = "KPModuleViewModel"

/** Keep filesystem paths safe: no separators, shell metacharacters or traversal. */
fun safeKpmModuleId(name: String): String = name.trim()
    .replace(Regex("[^A-Za-z0-9._-]"), "_")
    .trim('.', '_', '-')
    .take(64)
    .ifEmpty { "kpm" }

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

class KPModuleViewModel : ViewModel() {
    companion object { private var modules by mutableStateOf<List<KPModel.KPMInfo>>(emptyList()) }

    var search by mutableStateOf("")
    var isRefreshing by mutableStateOf(false)
        private set
    var isNeedRefresh by mutableStateOf(false)
        private set

    val moduleList by derivedStateOf {
        val comparator = compareBy(Collator.getInstance(Locale.getDefault()), KPModel.KPMInfo::name)
        modules.filter {
            it.name.contains(search, true) || it.moduleId.contains(search, true) ||
                HanziToPinyin.getInstance().toPinyinString(it.name)?.contains(search, true) == true
        }.sortedWith(comparator).also { isRefreshing = false }
    }

    fun markNeedRefresh() { isNeedRefresh = true }

    fun fetchModuleList() {
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing = true
            val start = SystemClock.elapsedRealtime()
            runCatching {
                val result = linkedMapOf<String, KPModel.KPMInfo>()
                var names = Natives.kernelPatchModuleList()
                if (Natives.kernelPatchModuleNum() <= 0) names = ""
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
                    // Only KPMs with a persistent file in KPMS_DIR are "installed";
                    // a plain Load (load-source "file") has no disable marker to
                    // control, so it must not show the toggle.
                    result[info.moduleId] = info.copy(installed = false)
                }
                val dirs = rootShellForResult("find ${APApplication.KPMS_DIR} -mindepth 1 -maxdepth 1 -type d -print").out
                dirs.map { it.trim().substringAfterLast('/') }.filter(String::isNotBlank).forEach { id ->
                    val file = "${APApplication.KPMS_DIR}$id/$id.kpm"
                    val parsed = rootShellForResult("${APApplication.APATCH_FOLDER}bin/kptools -l -M '$file'")
                        .out.joinToString("\n").let { parseKpmInfo(it, id) } ?: return@forEach
                    val key = safeKpmModuleId(id)
                    val old = result[key]
                    val disabled = rootShellForResult("[ -e '${APApplication.KPMS_DIR}$id/disable' ]").isSuccess
                    result[key] = old?.copy(
                        moduleId = id, installed = true, disabled = disabled,
                        version = parsed.version, license = parsed.license,
                        author = parsed.author, description = parsed.description
                    ) ?: parsed.copy(moduleId = id, installed = true, disabled = disabled, loadSource = "")
                }
                modules = result.values.toList()
                isNeedRefresh = false
            }.onFailure { Log.e(TAG, "fetchModuleList", it) }
            isRefreshing = false
            Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}, modules: ${modules.size}")
        }
    }
}
