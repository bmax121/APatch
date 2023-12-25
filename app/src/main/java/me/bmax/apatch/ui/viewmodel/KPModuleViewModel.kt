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
import me.bmax.apatch.Natives
import me.bmax.apatch.util.listModules
import me.bmax.apatch.util.overlayFsAvailable
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.*

class KPModuleViewModel : ViewModel() {
    companion object {
        private const val TAG = "KPModuleViewModel"
        private var modules by mutableStateOf<List<ModuleInfo>>(emptyList())
    }

    enum class KPMWhence {
        KERNEL_EMBED,   // todo: impl
        APM_EMBED,
        DYNAMIC
    }

    class ModuleInfo(
        val name: String,
        val author: String,
        val version: String,
        val license: String,
        val description: String,
        val enabled: Boolean,
        val remove: Boolean,
        val whence: KPMWhence
    )

    var isRefreshing by mutableStateOf(false)
        private set

    val moduleList by derivedStateOf {
        val comparator = compareBy(Collator.getInstance(Locale.getDefault()), ModuleInfo::name)
        modules.sortedWith(comparator).also {
            isRefreshing = false
        }
    }

    var isNeedRefresh by mutableStateOf(false)
        private set

    fun markNeedRefresh() {
        isNeedRefresh = true
    }

    fun fetchModuleList() {
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing = true

            val oldModuleList = modules

            val start = SystemClock.elapsedRealtime()

            kotlin.runCatching {
                val names = Natives.kernelPatchModuleList()
                Log.d(TAG, "names: " + names)

//                val array = JSONArray(result)
//                modules = (0 until array.length())
//                    .asSequence()
//                    .map { array.getJSONObject(it) }
//                    .map { obj ->
//                        ModuleInfo(
//                            obj.getString("id"),
//                            obj.optString("name"),
//                            obj.optString("author", "Unknown"),
//                            obj.optString("version", "Unknown"),
//                            obj.optInt("versionCode", 0),
//                            obj.optString("description"),
//                            obj.getBoolean("enabled")
//                        )
//                    }.toList()
                isNeedRefresh = false
            }.onFailure { e ->
                Log.e(TAG, "fetchModuleList: ", e)
                isRefreshing = false
            }

            // when both old and new is kotlin.collections.EmptyList
            // moduleList update will don't trigger
            if (oldModuleList === modules) {
                isRefreshing = false
            }

            Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}, modules: $modules")
        }
    }
}
