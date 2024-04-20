package me.bmax.apatch.ui.viewmodel

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.system.Os
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import dev.utils.app.MediaStoreUtils
import dev.utils.app.UriUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bmax.apatch.APApplication
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.copyAndClose
import me.bmax.apatch.util.copyAndCloseOut
import me.bmax.apatch.util.createRootShell
import me.bmax.apatch.util.inputStream
import me.bmax.apatch.util.shellForResult
import me.bmax.apatch.util.writeTo
import org.ini4j.Ini
import java.io.File
import java.io.IOException
import java.io.StringReader

private const val TAG = "PatchViewModel"

class PatchesViewModel : ViewModel() {
    enum class PatchMode(val sId: Int) {
        PATCH_ONLY(R.string.patch_mode_bootimg_patch), PATCH_AND_INSTALL(R.string.patch_mode_patch_and_install), INSTALL_TO_NEXT_SLOT(
            R.string.patch_mode_install_to_next_slot
        ),
        UNPATCH(R.string.patch_mode_uninstall_patch),
    }

    var bootSlot by mutableStateOf("")
    var bootDev by mutableStateOf("")
    var kimgInfo by mutableStateOf(KPModel.KImgInfo("", false))
    var kpimgInfo by mutableStateOf(KPModel.KPImgInfo("", "", "", "", ""))
    var superkey by mutableStateOf(APApplication.superKey)
    var existedExtras = mutableStateListOf<KPModel.IExtraInfo>()
    var newExtras = mutableStateListOf<KPModel.IExtraInfo>()
    var newExtrasFileName = mutableListOf<String>()

    var running by mutableStateOf(false)
    var patching by mutableStateOf(false)
    var patchdone by mutableStateOf(false)
    var needReboot by mutableStateOf(false)

    var error by mutableStateOf("")
    var patchLog by mutableStateOf("")

    private val patchDir: ExtendedFile =
        FileSystemManager.getLocal().getFile(apApp.filesDir.parent, "patch")
    private var srcBoot: ExtendedFile = patchDir.getChildFile("boot.img")
    private var shell: Shell = createRootShell()
    private var prepared: Boolean = false

    private fun prepare() {
        patchDir.deleteRecursively()
        patchDir.mkdirs()
        val execs = listOf(
            "libkptools.so",
            "libmagiskboot.so",
            "libbusybox.so",
            "libkpatch.so",
            "libbootctl.so"
        )
        error = ""

        val info = apApp.applicationInfo
        val libs = File(info.nativeLibraryDir).listFiles { _, name ->
            execs.contains(name)
        } ?: emptyArray()

        for (lib in libs) {
            val name = lib.name.substring(3, lib.name.length - 3)
            Os.symlink(lib.path, "$patchDir/$name")
        }

        // Extract scripts
        for (script in listOf(
            "boot_patch.sh", "boot_unpatch.sh", "boot_extract.sh", "util_functions.sh", "kpimg"
        )) {
            val dest = File(patchDir, script)
            apApp.assets.open(script).writeTo(dest)
        }

    }

    private fun parseKpimg() {
        val result = shellForResult(
            shell, "cd $patchDir", "./kptools -l -k kpimg"
        )

        if (result.isSuccess) {
            val ini = Ini(StringReader(result.out.joinToString("\n")))
            val kpimg = ini["kpimg"]
            if (kpimg != null) {
                kpimgInfo = KPModel.KPImgInfo(
                    kpimg["version"].toString(),
                    kpimg["compile_time"].toString(),
                    kpimg["config"].toString(),
                    APApplication.superKey,     // current key
                    kpimg["root_superkey"].toString(),   // empty
                )
            } else {
                error += "parse kpimg error\n"
            }
        } else {
            error = result.err.joinToString("\n")
        }
    }

    private fun parseBootimg(bootimg: String) {
        val result = shellForResult(
            shell,
            "cd $patchDir",
            "./magiskboot unpack $bootimg",
            "./kptools -l -i kernel",
        )
        if (result.isSuccess) {
            val ini = Ini(StringReader(result.out.joinToString("\n")))
            Log.d(TAG, "kernel image info: $ini")

            val kernel = ini["kernel"]
            if (kernel == null) {
                error += "empty kernel section"
                Log.d(TAG, error)
                return
            }
            kimgInfo = KPModel.KImgInfo(kernel["banner"].toString(), kernel["patched"].toBoolean())
            if (kimgInfo.patched) {
                val superkey = ini["kpimg"]?.getOrDefault("superkey", "") ?: ""
                kpimgInfo.superKey = superkey
                if (checkSuperKeyValidation(superkey)) {
                    this.superkey = superkey
                }
                var kpmNum = kernel["extra_num"]?.toInt()
                if (kpmNum == null) {
                    val extras = ini["extras"]
                    kpmNum = extras?.get("num")?.toInt()
                }
                if (kpmNum != null && kpmNum > 0) {
                    for (i in 0..<kpmNum) {
                        val extra = ini["extra $i"]
                        if (extra == null) {
                            error += "empty extra section"
                            break
                        }
                        val type = KPModel.ExtraType.valueOf(extra["type"]!!.uppercase())
                        val name = extra["name"].toString()
                        val args = extra["args"].toString()
                        var event = extra["event"].toString()
                        if (event.isEmpty()) {
                            event = KPModel.TriggerEvent.PRE_KERNEL_INIT.event
                        }
                        if (type == KPModel.ExtraType.KPM) {
                            val kpmInfo = KPModel.KPMInfo(
                                type, name, event, args,
                                extra["version"].toString(),
                                extra["license"].toString(),
                                extra["author"].toString(),
                                extra["description"].toString(),
                            )
                            existedExtras.add(kpmInfo)
                        }
                    }

                }
            }
        } else {
            error += result.err.joinToString("\n")
        }
    }

    val checkSuperKeyValidation: (superKey: String) -> Boolean = { superKey ->
        superKey.length in 8..63 && superKey.any { it.isDigit() } && superKey.any { it.isLetter() }
    }

    fun copyAndParseBootimg(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            if (running) return@launch
            running = true
            try {
                uri.inputStream().buffered().use { src ->
                    srcBoot.also {
                        src.copyAndCloseOut(it.newOutputStream())
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "copy boot image error: $e")
            }
            parseBootimg(srcBoot.path)
            running = false
        }
    }

    private fun extractAndParseBootimg(mode: PatchMode) {
        var cmdBuilder = "./boot_extract.sh"

        if (mode == PatchMode.INSTALL_TO_NEXT_SLOT) {
            cmdBuilder += " true"
        }

        val result = shellForResult(
            shell,
            "export ASH_STANDALONE=1",
            "cd $patchDir",
            "./busybox sh $cmdBuilder",
        )

        if (result.isSuccess) {
            bootSlot = if (!result.out.toString().contains("SLOT=")) {
                ""
            } else {
                result.out.filter { it.startsWith("SLOT=") }[0].removePrefix("SLOT=")
            }
            bootDev =
                result.out.filter { it.startsWith("BOOTIMAGE=") }[0].removePrefix("BOOTIMAGE=")
            Log.i(TAG, "current slot: $bootSlot")
            Log.i(TAG, "current bootimg: $bootDev")
            srcBoot = FileSystemManager.getLocal().getFile(bootDev)
            parseBootimg(bootDev)
        } else {
            error = result.err.joinToString("\n")
        }
        running = false
    }

    fun prepare(mode: PatchMode) {
        viewModelScope.launch(Dispatchers.IO) {
            if (prepared) return@launch
            prepared = true

            running = true
            prepare()
            if (mode != PatchMode.UNPATCH) {
                parseKpimg()
            }
            if (mode == PatchMode.PATCH_AND_INSTALL || mode == PatchMode.UNPATCH || mode == PatchMode.INSTALL_TO_NEXT_SLOT) {
                extractAndParseBootimg(mode)
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
            val kpmFileName = "${rand}.kpm"
            val kpmFile: ExtendedFile = patchDir.getChildFile(kpmFileName)

            Log.i(TAG, "copy kpm to: " + kpmFile.path)
            try {
                uri.inputStream().buffered().use { src ->
                    kpmFile.also {
                        src.copyAndCloseOut(it.newOutputStream())
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Copy kpm error: $e")
            }

            val result = shellForResult(
                shell, "cd $patchDir", "./kptools -l -M ${kpmFile.path}"
            )

            if (result.isSuccess) {
                val ini = Ini(StringReader(result.out.joinToString("\n")))
                val kpm = ini["kpm"]
                if (kpm != null) {
                    val kpmInfo = KPModel.KPMInfo(
                        KPModel.ExtraType.KPM,
                        kpm["name"].toString(),
                        KPModel.TriggerEvent.PRE_KERNEL_INIT.event,
                        "",
                        kpm["version"].toString(),
                        kpm["license"].toString(),
                        kpm["author"].toString(),
                        kpm["description"].toString(),
                    )
                    newExtras.add(kpmInfo)
                    newExtrasFileName.add(kpmFileName)
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
            Log.i(TAG, "starting unpatching...")

            val logs = object : CallbackList<String>() {
                override fun onAddElement(e: String?) {
                    patchLog += e
                    Log.i(TAG, "" + e)
                    patchLog += "\n"
                }
            }

            val result = shell.newJob().add(
                "export ASH_STANDALONE=1",
                "rm -f ${APApplication.APD_PATH}",
                "rm -rf ${APApplication.APATCH_FOLDER}",
                "cd $patchDir",
                "./busybox sh ./boot_unpatch.sh $bootDev",
            ).to(logs, logs).exec()

            if (result.isSuccess) {
                logs.add(" Unpatch successful")
                needReboot = true
                APApplication.markNeedReboot()
            } else {
                logs.add(" Unpatched failed")
                error = result.err.joinToString("\n")
            }
            logs.add("****************************")

            patchdone = true
            patching = false
        }
    }

    fun doPatch(mode: PatchMode) {
        viewModelScope.launch(Dispatchers.IO) {
            patching = true
            Log.d(TAG, "starting patching...")

            val apVer = Version.getManagerVersion().second
            val rand = (1..4).map { ('a'..'z').random() }.joinToString("")
            val outFilename = "apatch_patched_${apVer}_${BuildConfig.buildKPV}_${rand}.img"

            val logs = object : CallbackList<String>() {
                override fun onAddElement(e: String?) {
                    patchLog += e
                    Log.d(TAG, "" + e)
                    patchLog += "\n"
                }
            }
            logs.add("****************************")

            var patchCommand = "boot_patch.sh $superkey ${srcBoot.path}"
            if (mode == PatchMode.PATCH_AND_INSTALL || mode == PatchMode.INSTALL_TO_NEXT_SLOT) {
                patchCommand += " true"
            }

            for (i in 0..<newExtrasFileName.size) {
                patchCommand += " -M ${newExtrasFileName[i]}"
                val extra = newExtras[i]
                if (extra.args.isNotEmpty()) {
                    patchCommand += " -A ${extra.args}"
                }
                if (extra.event.isNotEmpty()) {
                    patchCommand += " -V ${extra.event}"
                }
                patchCommand += " -T ${extra.type.desc}"
            }
            for (i in 0..<existedExtras.size) {
                val extra = existedExtras[i]
                patchCommand += " -E ${extra.name}"
                if (extra.args.isNotEmpty()) {
                    patchCommand += " -A ${extra.args}"
                }
                if (extra.event.isNotEmpty()) {
                    patchCommand += " -V ${extra.event}"
                }
                patchCommand += " -T ${extra.type.desc}"
            }
            patchCommand += " -K kpatch"

            Log.i(TAG, "patchCommand: $patchCommand")

            val result = shell.newJob().add(
                "export ASH_STANDALONE=1", "cd $patchDir", "./busybox sh $patchCommand"
            ).to(logs, logs).exec()
            var succ = result.isSuccess

            if (!succ) {
                val msg = " Patch failed."
                error = msg
                error += result.err.joinToString("\n")
                logs.add(error)
                logs.add("****************************")
                patching = false
                return@launch
            }

            if (mode == PatchMode.PATCH_AND_INSTALL) {
                logs.add(" Reboot to finish the installation~")
                needReboot = true
                APApplication.markNeedReboot()
            } else if (mode == PatchMode.INSTALL_TO_NEXT_SLOT) {
                val bootctlStatus = shell.newJob().add(
                    "cd $patchDir", "chmod 0777 $patchDir/bootctl", "./bootctl hal-info"
                ).to(logs, logs).exec()
                if (!bootctlStatus.isSuccess) {
                    logs.add(" [X] Failed to connect to boot hal")
                } else {
                    val currSlot = shell.newJob().add(
                        "cd $patchDir", "./bootctl get-current-slot"
                    ).exec()
                    val targetSlot = if (currSlot.out.toString().contains("0")) {
                        1
                    } else {
                        0
                    }
                    val setNextActiveSlot = shell.newJob().add(
                        "cd $patchDir", "./bootctl set-active-boot-slot $targetSlot"
                    ).exec()
                    if (setNextActiveSlot.isSuccess) {
                        logs.add(" Switch to next slot: $targetSlot done")
                    }
                }
                logs.add(" KernelPatch was successfully installed to next slot, reboot to finish the installation~")
                needReboot = true
                APApplication.markNeedReboot()
            } else if (mode == PatchMode.PATCH_ONLY) {
                val newBootFile = patchDir.getChildFile("new-boot.img")
                val outDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
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
                    logs.add(" Output file is written to ")
                    logs.add(" ${outPath.path}")
                } else {
                    logs.add(" Write patched boot.img failed")
                }
            }
            logs.add("****************************")
            patchdone = true
            patching = false
        }
    }
}
