package me.bmax.apatch.util

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import kotlin.concurrent.thread

object SuConfig {
    private val mutex = Mutex()
    private val TAG = "SUAllowConfig"


    suspend fun removePackage(packageName: String) {
        mutex.withLock {
            thread {
                val allows = HashMap<String, Natives.Profile>()
                Natives.su()
                val file = File(APApplication.SU_PATH_FILE)
                if(file.exists()) {
                    file.readLines().filter { !it.isEmpty() }.forEach {
                        val p = Natives.Profile.from(it)
                        allows[p.pkg] = p
                    }
                } else {
                    if(!file.parentFile.exists()) file.parentFile.mkdirs()
                }
                allows.remove(packageName)
                BufferedWriter(FileWriter(file, false)).use { writer ->
                    allows.values.forEach {
                        writer.write(it.toLine())
                        writer.write("\n")
                    }
                }
            }.join()
        }
    }

    suspend fun addProfile(profile: Natives.Profile) {
        mutex.withLock {
            thread {
                val allows = HashMap<String, Natives.Profile>()
                Natives.su()
                val file = File(APApplication.SU_PATH_FILE)
                if(file.exists()) {
                    file.readLines().filter { !it.isEmpty() }.forEach {
                        val p = Natives.Profile.from(it)
                        allows[p.pkg] = p
                    }
                } else {
                    if(!file.parentFile.exists()) file.parentFile.mkdirs()
                }
                allows[profile.pkg] = profile
                Log.d(TAG, allows.toString())
                BufferedWriter(FileWriter(file, false)).use { writer ->
                    allows.values.forEach {
                        writer.write(it.toLine())
                        writer.write("\n")
                    }
                }
            }.join()
        }
    }
}