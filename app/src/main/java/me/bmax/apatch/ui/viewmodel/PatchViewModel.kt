package me.bmax.apatch.ui.viewmodel

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.system.Os
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.Shell.FLAG_NON_ROOT_SHELL
import com.topjohnwu.superuser.Shell.getShell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import dev.utils.app.MediaStoreUtils
import dev.utils.app.UriUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import me.bmax.apatch.BuildConfig
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

    var bootSlot by mutableStateOf("")
    var bootDev by mutableStateOf("")
    var kimgInfo by mutableStateOf(KPModel.KImgInfo("", false))
    var kpimgInfo by mutableStateOf(KPModel.KPImgInfo("","","", ""))
    var superkey by mutableStateOf("")
    var existedExtras = mutableListOf<KPModel.IExtraInfo>()
    var addedExtras = mutableListOf<KPModel.IExtraInfo>()
    var addedExtrasFileName = mutableListOf<String>()

    var running by mutableStateOf(false)
    var patching by mutableStateOf(false)
    var patchdone by mutableStateOf(false)
    var needReboot by mutableStateOf(false)

    var error by mutableStateOf("")
    var patchLog by mutableStateOf("")

    private val patchDir: ExtendedFile = FileSystemManager.getLocal().getFile(apApp.filesDir.parent, "patch")
    private var srcBoot: ExtendedFile = patchDir.getChildFile("boot.img")
    private val currentBootPath: String = ""
    private var shell: Shell = tryGetRootShell()

    private fun prepare() {
        patchDir.deleteRecursively()
        patchDir.mkdirs()
        val execs = listOf("libkptools.so", "libmagiskboot.so", "libbusybox.so", "libkpatch.so")
        error = ""

        val info = apApp.applicationInfo
        var libs = File(info.nativeLibraryDir).listFiles { _, name ->
            execs.contains(name)
        } ?: emptyArray()

        for (lib in libs) {
            val name = lib.name.substring(3, lib.name.length - 3)
            Os.symlink(lib.path, "$patchDir/$name")
        }

        // Extract scripts
        for (script in listOf(
            "boot_patch.sh",
            "boot_unpatch.sh",
            "boot_extract.sh",
            "util_functions.sh",
            "kpimg"
        )) {
            val dest = File(patchDir, script)
            apApp.assets.open(script).writeTo(dest)
        }

    }
    private fun parseKpimg() {
        val result = shellForResult(
            shell,
            "cd $patchDir",
            "./kptools -l -k kpimg"
        )

        if(result.isSuccess) {
            val ini = Ini(StringReader(result.out.joinToString("\n")))
            var kpimg = ini.get("kpimg")
            if (kpimg != null) {
                kpimgInfo = KPModel.KPImgInfo(
                    kpimg["version"].toString(),
                    kpimg["compile_time"].toString(),
                    kpimg["config"].toString(),
                    kpimg["superkey"].toString(),   // empty
                )
                superkey = kpimgInfo.superKey
            } else {
                error += "parse kpimg error\n";
            }
        } else {
            error = result.err.joinToString("\n")
        }
    }

    private fun parseBootimg(bootimg: String) {
        val result = shellForResult(shell,
            "cd $patchDir",
            "./magiskboot unpack ${bootimg} >/dev/null 2>&1",
            "./kptools -l -i kernel",
        )
        if(result.isSuccess) {
            val ini = Ini(StringReader(result.out.joinToString("\n")))
            Log.d(TAG, "kernel image info: " + ini.toString())
            var kernel = ini.get("kernel")
            if (kernel != null) {
                kimgInfo = KPModel.KImgInfo(kernel["banner"].toString(), kernel["patched"].toBoolean())
            }
            if(kimgInfo.patched) {
                superkey = ini.get("kpimg")?.getOrDefault("superkey", "") ?: ""
            }
        } else {
            error += result.err.joinToString("\n")
        }
    }


    fun copyAndParseBootimg(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            if (running) return@launch
            running = true
            try { uri.inputStream().buffered().use { src ->
                srcBoot.also {
                    src.copyAndCloseOut(it.newOutputStream())
                }
            } } catch (e: IOException) {
                Log.d(TAG, "copy boot image error: " + e)
            }
            parseBootimg(srcBoot.path)
            running = false
        }
    }

    fun extractAndParseBootimg() {
        val result = shellForResult(shell,
            "cd $patchDir",
            "sh boot_extract.sh",
        )
        if(result.isSuccess) {
            bootSlot = result.out.filter { it.startsWith("SLOT=") }[0].removePrefix("SLOT=")
            bootDev = result.out.filter { it.startsWith("BOOTIMAGE=") }[0].removePrefix("BOOTIMAGE=")
            Log.d(TAG, "current slot: ${bootSlot}")
            Log.d(TAG, "current bootimg: ${bootDev}")
            parseBootimg(bootDev)
        } else {
            error = result.err.joinToString("\n")
        }
        running = false
    }

    fun prepare(mode: PatchMode) {
        viewModelScope.launch(Dispatchers.IO) {
            running = true
            shell = if(mode.equals(PatchMode.PATCH)) getShell() else getRootShell()
            prepare()
            if (!mode.equals(PatchMode.UNPATCH)) {
                parseKpimg()
            }
            if (mode.equals(PatchMode.UPDATE) || mode.equals(PatchMode.UNPATCH)) {
                extractAndParseBootimg()
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

            val result = shellForResult(shell,
                "cd $patchDir",
                "./kptools -l -M ${kpmFile.path}"
            )

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
                    addedExtras.add(kpmInfo)
                    addedExtrasFileName.add(kpmFileName)
                }
            } else {
                error = "Invalid KPM\n"
            }
            running = false
        }

    }


    fun doUnpatch() {
        viewModelScope.launch(Dispatchers.IO) {
            patching = true
            patchLog = ""
            Log.d(TAG, "starting unpatching...")

            val logs = object : CallbackList<String>() {
                override fun onAddElement(e: String?) {
                    patchLog += e
                    Log.d(TAG, "" + e)
                    patchLog += "\n"
                }
            }

            val result = shell.newJob().add(
                "cd $patchDir",
                "sh boot_unpatch.sh ${bootDev}",
            ).to(logs, logs).exec()

            if (result.isSuccess) {
                logs.add(" Unpatch successful")
                needReboot = true
            } else {
                logs.add(" Unpatched failed")
                error = result.err.toString()
            }
            logs.add("****************************")

            patchdone = true
            patching = false
        }
    }

    fun doPatch() {
        viewModelScope.launch(Dispatchers.IO) {
            patching = true
            Log.d(TAG, "starting patching...")

            val apVer = Version.getManagerVersion().second
            val rand = (1..4).map { ('a'..'z').random() }.joinToString("")
            val outFilename = "apatch_${apVer}_${BuildConfig.buildKPV}_${rand}.img"

            val logs = object: CallbackList<String>(){
                override fun onAddElement(e: String?) {
                    patchLog += e
                    Log.d(TAG, "" + e)
                    patchLog += "\n"
                }
            }

            logs.add("****************************")

            var patchCommand = "sh boot_patch.sh $superkey boot.img "

            for(i in 0..addedExtrasFileName.size - 1) {
                patchCommand += "-E ${addedExtrasFileName[i]} "
                if(addedExtras[i].type.equals(KPModel.ExtraType.KPM)) {
                    val args = (addedExtras[i] as KPModel.KPMInfo).args
                    if(args.isNotEmpty()){
                        patchCommand += "-A ${args} "
                    }
                }
            }
            Log.d(TAG, "patchCommand: ${patchCommand}")

            shell.newJob().add(
                "cd $patchDir",
                patchCommand,
            ).to(logs, logs).exec()

            var succ = true
            val newBootFile = patchDir.getChildFile("new-boot.img")
            if (newBootFile.exists()) {
                val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!outDir.exists()) outDir.mkdirs()
                val outPath = File(outDir, outFilename)
                val inputUri = UriUtils.getUriForFile(newBootFile)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val outUri = MediaStoreUtils.createDownloadUri(outFilename)
                    succ = MediaStoreUtils.insertDownload(outUri, inputUri)
                } else {
                    newBootFile.inputStream().copyAndClose(outPath.outputStream())
                }

                if (succ) {
                    logs.add(" Write patched boot.img was successful")
                    logs.add(" Output file is written to ")
                    logs.add(" ${outPath?.path}")
                } else {
                    logs.add(" Write patched boot.img failed")
                }
            } else {
                val msg = "Patch failed."
                error = msg
                logs.add(msg)
            }
            logs.add("****************************")

            patchdone = true
            patching = false
        }
    }
}
