package me.bmax.apatch.ui.viewmodel

import android.net.Uri
import android.os.SystemClock
import android.system.Os
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bmax.apatch.Natives
import me.bmax.apatch.apApp
import me.bmax.apatch.util.*
import java.io.File
import java.io.IOException

private const val TAG = "PatchViewModel"
class PatchViewModel : ViewModel() {

    var running by mutableStateOf(false)
        private set

    var outPath: File? = null

    val patchDir: ExtendedFile = FileSystemManager.getLocal().getFile(apApp.filesDir.parent, "patch")
    var srcBoot: ExtendedFile = patchDir.getChildFile("boot.img")


    fun copyAndParseBootimg(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            running = true

            patchDir.deleteRecursively()
            patchDir.mkdirs()

            if (uri != null) {
                try {
                    uri.inputStream().buffered().use { src ->
                        srcBoot.also {
                            src.copyAndCloseOut(it.newOutputStream())
                        }
                    }
                } catch (e: IOException) {
                    Log.d(TAG, "Copy boot image error: " + e)
                }
            }

            val execs = listOf("libkptools.so", "libmagiskboot.so", "libbusybox.so")

            val info = apApp.applicationInfo
            var libs = File(info.nativeLibraryDir).listFiles { _, name ->
                execs.contains(name)
            } ?: emptyArray()

            for (lib in libs) {
                val name = lib.name.substring(3, lib.name.length - 3)
                Os.symlink(lib.path, "$patchDir/$name")
            }

            // Extract scripts
            for (script in listOf("boot_patch.sh", "boot_unpatch.sh", "util_functions.sh", "kpimg")) {
                val dest = File(patchDir, script)
                apApp.assets.open(script).writeTo(dest)
            }

            val cmds = arrayOf(
                "cd $patchDir",
                "./magiskboot unpack boot.img >/dev/null 2>&1",
                "./kptools -l -i kernel"
            )
            val output = ShellUtils.fastCmd(*cmds)
            Log.d(TAG, "out: ${output}")

            running = false
        }

    }
}
