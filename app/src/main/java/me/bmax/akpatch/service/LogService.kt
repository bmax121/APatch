package me.bmax.akpatch.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import me.bmax.akpatch.Natives
import me.bmax.akpatch.TAG
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

class LogService(): Service() {
    private var isRoot = false
    private val isRunning = false

    private var kernelReader = null

    override fun onCreate() {
        super.onCreate()
        if(Natives.installed()) {
            if(Natives.makeMeSu() == 0L) {
                isRoot = true
                Log.d(TAG, "LogService start in root mode ...")
                startKernelLog()
            }
        } else {
            Log.e(TAG, "LogService start in non-root mode ...")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {

        return null;
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun startKernelLog() {
        if(! isRoot) return
        val file = File("/proc/kmsg")
        if (!file.exists()) return

        val stringBuilder = StringBuilder()
        try {
            val kernelReader = BufferedReader(FileReader(file))
            var line: String? = kernelReader.readLine()
            while (line != null) {
                stringBuilder.append(line).append("\n")
                line = kernelReader.readLine()
                Log.d(TAG, "kernel log: " + line)
            }
            kernelReader.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}