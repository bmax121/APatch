package me.bmax.akpatch.ui.util

import android.net.Uri
import android.os.Environment
import me.bmax.akpatch.pApp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "PatchBooting"

fun extractKpatch(file: String): Int {
    return Shell.SH.run(
        "cp ${pApp.applicationInfo.nativeLibraryDir}/kpatch.exe.so ${file};" +
                "chown shell:shell ${file};" +
                "chmod +x ${file};"
    )
        .exitCode
}

private fun isUpdated(fileName: String): Boolean {
    val file = File(pApp.codeCacheDir, fileName)
    try {
        val assetsInputStream = pApp.assets.open(fileName)
        val assetsHash = calculateMD5(assetsInputStream)
        assetsInputStream.close()
        return file.exists() && assetsHash == getHashFromCodeCache(fileName)
    } catch (e: IOException) {
        return false
    }
}

private fun calculateMD5(inputStream: InputStream): String {
    val md = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(8192)
    var bytesRead: Int
    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        md.update(buffer, 0, bytesRead)
    }
    val md5Bytes = md.digest()
    return md5Bytes.joinToString("") { "%02x".format(it) }
}

private fun getHashFromCodeCache(fileName: String): String? {
    val file = File(pApp.codeCacheDir, fileName)
    if (file.exists()) {
        FileInputStream(file).use { inputStream ->
            return calculateMD5(inputStream)
        }
    }
    return null
}

fun copyFileFromAssets(fileName: String, destinationDir: File): Boolean {
    return try {
        val destinationFile = File(destinationDir, fileName)
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        val inputStream = pApp.assets.open(fileName)
        val outputStream = FileOutputStream(destinationFile)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
        inputStream.close()
        outputStream.close()
        true
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }
}


fun patchBootimg(uri: Uri, superKey: String, onMessage: (String) -> Unit): Int {
    // work dir
    val dateFormat = SimpleDateFormat("yyyyMMdd_HH_mm_ss", Locale.getDefault())
    val time = Date()
    val currentTime = dateFormat.format(time)
    val nativeDir = File(pApp.applicationInfo.nativeLibraryDir)
    onMessage("exe_dir: ${nativeDir.absolutePath}")
    val workDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "akpatch_${currentTime}")
    workDir.mkdirs()
    onMessage("work_dir: ${workDir.absolutePath}")

    // todo, version
    val version = 200
    val patchedImgFile = "${workDir.absolutePath}/akp_${version}_${superKey}_${time.time/1000}_boot.img"

    // copy boot.img to workDir
    val bootimgIs = pApp.contentResolver.openInputStream(uri)
    if(bootimgIs == null) return -1
    val outputStream = FileOutputStream(File(workDir, "boot.img"))
    val buffer = ByteArray(8192)
    var bytesRead: Int
    while (bootimgIs.read(buffer).also { bytesRead = it } != -1) {
        outputStream.write(buffer, 0, bytesRead)
    }
    bootimgIs.close()
    outputStream.close()

    // copy kpimg to work dir
    copyFileFromAssets("kpimg", workDir)

    // log file
    val logFile = File(workDir, "$currentTime.log")
    logFile.createNewFile()
    onMessage("log_file: ${logFile.absolutePath}")

    var retCode = 0

    val config = Shell.Command.Config.Builder().apply {
        timeout = Shell.Timeout(30, TimeUnit.MINUTES)
        redirectErrorStream = false
        onCancelled = {}
        onStdErr = { line: String ->
            onMessage(line)
            logFile.writeText(line)
        }
        onStdOut = { line: String ->
            onMessage(line)
            logFile.writeText(line)
        }
        notify = false
    }.create()

    onMessage("===== start patching =====")

    // do patch
    val cmds = arrayOf(
        "cd ${nativeDir}",
        "./bootimgtools.exe.so unpack ${workDir}",
        "cp ${workDir}/kernel ${workDir}/kernel.ori",
        "./kptools.exe.so -p ${workDir}/kernel.ori --kpimg ${workDir}/kpimg --skey ${superKey} --out ${workDir}/kernel",
        "./bootimgtools.exe.so repack ${workDir}",
        "mv ${workDir}/new-boot.img ${patchedImgFile}",
    )
    run breaking@{
        cmds.forEach {
            var result = Shell.SH.run(it, config = config)
            retCode = result.exitCode
            if(retCode != 0) return@breaking
        }
    }

    if(retCode == 0) {
        onMessage("""
            |
            |===== patch succeed  ====
            |The patched boot image is located at: 
            |${patchedImgFile}
            |Now, you can flash it to your phone.
        """.trimMargin()
        )
    } else {
        onMessage("""
            |
            |----- patch failed  -----
        """.trimMargin()
        )
    }

    return retCode
}

