package me.bmax.apatch.util

import android.content.Context
import android.os.Build
import android.system.Os
import com.topjohnwu.superuser.ShellUtils
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun getBugreportFile(context: Context): File {

    val bugreportDir = File(context.cacheDir, "bugreport")
    bugreportDir.mkdirs()

    val dmesgFile = File(bugreportDir, "dmesg.txt")
    val logcatFile = File(bugreportDir, "logcat.txt")
    val tombstonesFile = File(bugreportDir, "tombstones.tar.gz")
    val dropboxFile = File(bugreportDir, "dropbox.tar.gz")
    val pstoreFile = File(bugreportDir, "pstore.tar.gz")
    val diagFile = File(bugreportDir, "diag.tar.gz")
    val bootlogFile = File(bugreportDir, "bootlog.tar.gz")
    val mountsFile = File(bugreportDir, "mounts.txt")
    val fileSystemsFile = File(bugreportDir, "filesystems.txt")
    val apFileTree = File(bugreportDir, "ap_tree.txt")
    val appListFile = File(bugreportDir, "packages.txt")
    val propFile = File(bugreportDir, "props.txt")
    val packageConfigFile = File(bugreportDir, "package_config")

    val shell = tryGetRootShell()

    shell.newJob().add("dmesg > ${dmesgFile.absolutePath}").exec()
    shell.newJob().add("logcat -d > ${logcatFile.absolutePath}").exec()
    shell.newJob().add("tar -czf ${tombstonesFile.absolutePath} -C /data/tombstones .").exec()
    shell.newJob().add("tar -czf ${dropboxFile.absolutePath} -C /data/system/dropbox .").exec()
    shell.newJob().add("tar -czf ${pstoreFile.absolutePath} -C /sys/fs/pstore .").exec()
    shell.newJob().add("tar -czf ${diagFile.absolutePath} -C /data/vendor/diag .").exec()
    shell.newJob().add("tar -czf ${bootlogFile.absolutePath} -C /data/adb/ap/log .").exec()

    shell.newJob().add("cat /proc/1/mountinfo > ${mountsFile.absolutePath}").exec()
    shell.newJob().add("cat /proc/filesystems > ${fileSystemsFile.absolutePath}").exec()
    shell.newJob().add("ls -alRZ /data/adb > ${apFileTree.absolutePath}").exec()
    shell.newJob().add("cp /data/system/packages.list ${appListFile.absolutePath}").exec()
    shell.newJob().add("getprop > ${propFile.absolutePath}").exec()
    shell.newJob().add("cp /data/adb/ap/package_config ${packageConfigFile.absolutePath}").exec()

    val selinux = ShellUtils.fastCmd(shell, "getenforce")

    // basic information
    val buildInfo = File(bugreportDir, "basic.txt")
    PrintWriter(FileWriter(buildInfo)).use { pw ->
        pw.println("Kernel: ${System.getProperty("os.version")}")
        pw.println("BRAND: " + Build.BRAND)
        pw.println("MODEL: " + Build.MODEL)
        pw.println("PRODUCT: " + Build.PRODUCT)
        pw.println("MANUFACTURER: " + Build.MANUFACTURER)
        pw.println("SDK: " + Build.VERSION.SDK_INT)
        pw.println("PREVIEW_SDK: " + Build.VERSION.PREVIEW_SDK_INT)
        pw.println("FINGERPRINT: " + Build.FINGERPRINT)
        pw.println("DEVICE: " + Build.DEVICE)
        pw.println("Manager: " + Version.getManagerVersion())
        pw.println("SELinux: $selinux")

        val uname = Os.uname()
        pw.println("KernelRelease: ${uname.release}")
        pw.println("KernelVersion: ${uname.version}")
        pw.println("Mahcine: ${uname.machine}")
        pw.println("Nodename: ${uname.nodename}")
        pw.println("Sysname: ${uname.sysname}")

        pw.println("KPatch: ${Version.installedKPVString()}")
        pw.println("APatch: ${Version.installedApdVString}")
        val safeMode = false
        pw.println("SafeMode: $safeMode")
    }

    // modules
    val modulesFile = File(bugreportDir, "modules.json")
    modulesFile.writeText(listModules())

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm")
    val current = LocalDateTime.now().format(formatter)

    val targetFile = File(context.cacheDir, "APatch_bugreport_${current}.tar.gz")

    shell.newJob().add("tar czf ${targetFile.absolutePath} -C ${bugreportDir.absolutePath} .")
        .exec()
    shell.newJob().add("rm -rf ${bugreportDir.absolutePath}").exec()
    shell.newJob().add("chmod 0644 ${targetFile.absolutePath}").exec()

    return targetFile
}
