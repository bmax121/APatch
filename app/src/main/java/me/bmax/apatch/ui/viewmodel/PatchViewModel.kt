package me.bmax.apatch.ui.viewmodel

import android.net.Uri
import android.system.Os
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bmax.apatch.apApp
import me.bmax.apatch.util.*
import org.ini4j.Ini
import java.io.File
import java.io.IOException
import java.io.StringReader

private const val TAG = "PatchViewModel"
class PatchViewModel : ViewModel() {
    var running by mutableStateOf(false)
        private set

    var error by mutableStateOf("")

    var imgPatchInfo by mutableStateOf<KPModel.ImgPatchInfo>(KPModel.ImgPatchInfo(
        kimgInfo = KPModel.KImgInfo("", false),
        kpimgInfo = KPModel.KPImgInfo("","",""),
        existedExtras = mutableListOf(),
        addedExtras = mutableListOf(),
    ))

    var outPath: File? = null

    private val patchDir: ExtendedFile = FileSystemManager.getLocal().getFile(apApp.filesDir.parent, "patch")
    private var srcBoot: ExtendedFile = patchDir.getChildFile("boot.img")
    private var kpimg: ExtendedFile = patchDir.getChildFile("kpimg")


    fun prepareAndParseKpimg() {
        viewModelScope.launch(Dispatchers.IO) {
            running = true
            error = ""
            patchDir.deleteRecursively()
            patchDir.mkdirs()
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

            var cmds = arrayOf(
                "cd $patchDir",
                "./kptools -l -k kpimg"
            )
            val out = ArrayList<String>()
            val err = ArrayList<String>()

            val shell = Shell.getShell()
            val result = shell.newJob().add(*cmds).to(out, err).exec()

            if(result.isSuccess) {
                val ini = Ini(StringReader(result.out.joinToString("\n")))
                var kpimg = ini.get("kpimg")
                if (kpimg != null) {
                    imgPatchInfo.kpimgInfo = KPModel.KPImgInfo(
                        kpimg["version"].toString(),
                        kpimg["compile_time"].toString(),
                        kpimg["config"].toString(),
                        kpimg["superkey"].toString(),
                    )
                } else {
                    error += "no kpimg section\n";
                }
            } else {
                error = err.joinToString("\n")
            }

            out.clear()
            err.clear()
            running = false
        }

    }


    fun copyAndParseBootimg(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            if(running) return@launch

            running = true

            error = ""

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

            var cmds = arrayOf(
                "cd $patchDir",
                "./magiskboot unpack boot.img >/dev/null 2>&1",
                "./kptools -l -i kernel"
            )
            val out = ArrayList<String>()
            val err = ArrayList<String>()

            val shell = Shell.getShell()
            val result = shell.newJob().add(*cmds).to(out, err).exec()

            if(result.isSuccess) {
                val ini = Ini(StringReader(result.out.joinToString("\n")))
                Log.d(TAG, "kernel image info: " + ini.toString())
                var kernel = ini.get("kernel")
                if (kernel != null) {
                    imgPatchInfo.kimgInfo = KPModel.KImgInfo(kernel["banner"].toString(), kernel["patched"].toBoolean())
                }
                if(imgPatchInfo.kimgInfo.patched) {

                }
            } else {
                error = err.joinToString("\n")
            }
            running = false
        }

    }


    fun embedKPM(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            if (running) return@launch
            running = true
            error = ""

            val rand = (1..4).map { ('a'..'z').random() }.joinToString("")
            var kpm: ExtendedFile = patchDir.getChildFile("${rand}_${File(uri.path).name}")

            Log.d(TAG, "Copy kpm to: " + kpm.path)
            if (uri != null) {
                try {
                    uri.inputStream().buffered().use { src ->
                        kpm.also {
                            src.copyAndCloseOut(it.newOutputStream())
                        }
                    }
                } catch (e: IOException) {
                    Log.d(TAG, "Copy kpm error: " + e)
                }
            }

            var cmds = arrayOf(
                "cd $patchDir",
                "./kptools -l -M ${kpm.path}"
            )
            val out = ArrayList<String>()
            val err = ArrayList<String>()

            val shell = Shell.getShell()
            val result = shell.newJob().add(*cmds).to(out, err).exec()

            if (result.isSuccess) {
                val ini = Ini(StringReader(result.out.joinToString("\n")))
                Log.d(TAG, "add new kpm info: " + ini.toString())
                var kpm = ini.get("kpm")
                if (kpm != null) {
                    val kpmInfo = KPModel.KPMInfo(
                        KPModel.ExtraType.KPM,
                        kpm["name"].toString(),
                        kpm["version"].toString(),
                        kpm["license"].toString(),
                        kpm["author"].toString(),
                        kpm["description"].toString(),
                        ""
                        )
                    imgPatchInfo.addedExtras.add(kpmInfo)
                }
            } else {
                error = err.joinToString("\n")
            }
            running = false
        }

    }
}
