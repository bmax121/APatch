package me.bmax.apatch.ui.viewmodel

import android.net.Uri
import android.system.Os
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.util.*
import org.ini4j.Ini
import java.io.File
import java.io.IOException
import java.io.StringReader

private const val TAG = "PatchViewModel"
class PatchViewModel : ViewModel() {
    enum class PatchMode(val sId: Int) {
        PATCH(R.string.patch_mode_bootimg_patch),
        UPDATE(R.string.patch_mode_update_patch),
        UNPATCH(R.string.patch_mode_uninstall_patch),
    }

    var running by mutableStateOf(false)
    var patching by mutableStateOf(false)
    var patchdone by mutableStateOf(false)
    var error by mutableStateOf("")
    var superkey by mutableStateOf("")
    var patchLog by mutableStateOf("")

    var imgPatchInfo by mutableStateOf<KPModel.ImgPatchInfo>(KPModel.ImgPatchInfo(
        kimgInfo = KPModel.KImgInfo("", false),
        kpimgInfo = KPModel.KPImgInfo("","","", ""),
        existedExtras = mutableListOf(),
        addedExtras = mutableListOf(),
    ))
    private var addedExtrasFileName = mutableListOf<String>()

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

            try { uri.inputStream().buffered().use { src ->
                srcBoot.also {
                    src.copyAndCloseOut(it.newOutputStream())
                }
            } } catch (e: IOException) {
                Log.d(TAG, "Copy boot image error: " + e)
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
            val kpmFileName = "${rand}_${File(uri.path).name}"
            var kpmFile: ExtendedFile = patchDir.getChildFile(kpmFileName)

            Log.d(TAG, "copy kpm to: " + kpmFile.path)
            try {
                uri.inputStream().buffered().use { src ->
                    kpmFile.also {
                        src.copyAndCloseOut(it.newOutputStream())
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG, "Copy kpm error: " + e)
            }

            var cmds = arrayOf(
                "cd $patchDir",
                "./kptools -l -M ${kpmFile.path}"
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
                    addedExtrasFileName.add(kpmFileName)
                }
            } else {
                error = err.joinToString("\n")
            }
            running = false
        }

    }

    fun doPatch(mode: PatchMode) {
        viewModelScope.launch(Dispatchers.IO) {
            patching = true
            Log.d(TAG, "starting patching..., final patch info: ${imgPatchInfo}")

            val apVer = Version.getManagerVersion().second
            val rand = (1..4).map { ('a'..'z').random() }.joinToString("")
            val outFilename = "apatch_${apVer}_${rand}_boot.img"

            val patchCommand = when(mode) {
                PatchMode.PATCH -> "sh boot_patch.sh $superkey"
                PatchMode.UPDATE -> "sh boot_patch.sh $superkey"
                PatchMode.UNPATCH -> "sh boot_unpatch.sh"
                else -> "sh boot_patch.sh $superkey ${srcBoot?.path}"
            }


            val shell: Shell = if(mode.equals(PatchMode.PATCH)) Shell.getShell() else getRootShell()



            patchdone = true
            patching = false
        }
    }
}
