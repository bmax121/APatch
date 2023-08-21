package me.bmax.akpatch.ui.util

fun forceStopApp(packageName: String) {
    val cmd = "am force-stop $packageName"
}


fun reboot(reason: String = "") {
    val cmd = "/system/bin/svc power reboot $reason || /system/bin/reboot $reason"
}

fun launchApp(packageName: String) {
    val cmd = "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
}

fun restartApp(packageName: String) {
    forceStopApp(packageName)
    launchApp(packageName)
}