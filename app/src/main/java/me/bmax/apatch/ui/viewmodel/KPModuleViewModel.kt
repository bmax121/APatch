package me.bmax.apatch.ui.viewmodel

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bmax.apatch.Natives
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.screen.inputStream
import me.bmax.apatch.ui.screen.writeTo
import java.io.IOException
import java.text.Collator
import java.util.*

class KPModuleViewModel : ViewModel() {
    companion object {
        private const val TAG = "KPModuleViewModel"
        private var modules by mutableStateOf<List<Natives.KPMInfo>>(emptyList())
    }


    var isRefreshing by mutableStateOf(false)
        private set

    val moduleList by derivedStateOf {
        val comparator = compareBy(Collator.getInstance(Locale.getDefault()), Natives.KPMInfo::name)
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
                val nameList = names.split('\n').toList()

                Log.d(TAG, "nameList: " + nameList)

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

    fun loadModule(uri: Uri) {
        var kpmDir: ExtendedFile = FileSystemManager.getLocal().getFile(apApp.filesDir.parent, "kpm")
        kpmDir.deleteRecursively()
        kpmDir.mkdirs()

        val kpm = kpmDir.getChildFile(uri.path)

        try {
            uri.inputStream().buffered().writeTo(kpm)
        } catch (e: IOException) {
            Log.e(TAG, "Copy kpm error: " + e)
        }
    }
}
