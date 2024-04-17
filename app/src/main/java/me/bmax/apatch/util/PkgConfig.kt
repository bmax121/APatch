package me.bmax.apatch.util

import android.os.Parcelable
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import java.io.File
import java.io.FileWriter
import kotlin.concurrent.thread

object PkgConfig {
    private const val TAG = "PkgConfig"

    private const val CSV_HEADER = "pkg,exclude,allow,uid,to_uid,sctx"

    @Immutable
    @Parcelize
    @Keep
    data class Config(
        var pkg: String = "", var exclude: Int = 0, var allow: Int = 0, var profile: Natives.Profile
    ) : Parcelable {
        companion object {
            fun fromLine(line: String): Config {
                val sp = line.split(",")
                val profile = Natives.Profile(sp[3].toInt(), sp[4].toInt(), sp[5])
                return Config(sp[0], sp[1].toInt(), sp[2].toInt(), profile)
            }
        }

        fun isDefault(): Boolean {
            return allow == 0 && exclude == 0
        }

        fun toLine(): String {
            return "${pkg},${exclude},${allow},${profile.uid},${profile.toUid},${profile.scontext}"
        }
    }

    fun readConfigs(): HashMap<String, Config> {
        val configs = HashMap<String, Config>()
        val file = File(APApplication.PACKAGE_CONFIG_FILE)
        if (file.exists()) {
            file.readLines().drop(1).filter { it.isNotEmpty() }.forEach {
                Log.d(TAG, it)
                val p = Config.fromLine(it)
                if (!p.isDefault()) {
                    configs[p.pkg] = p
                }
            }
        }
        return configs
    }

    private fun writeConfigs(configs: HashMap<String, Config>) {
        val file = File(APApplication.PACKAGE_CONFIG_FILE)
        if (!file.parentFile?.exists()!!) file.parentFile?.mkdirs()
        val writer = FileWriter(file, false)
        writer.write(CSV_HEADER + '\n')
        configs.values.forEach {
            if (!it.isDefault()) {
                writer.write(it.toLine() + '\n')
            }
        }
        writer.flush()
        writer.close()
    }

    fun changeConfig(config: Config) {
        thread {
            synchronized(PkgConfig.javaClass) {
                Natives.su()
                val configs = readConfigs()
                val pkg = config.pkg
                val uid = config.profile.uid
                // Root App should not be excluded
                if (config.allow == 1) {
                    config.exclude = 0
                }
                if (config.allow == 0 && configs[pkg] != null && config.exclude != 0) {
                    // revoke all uid
                    val toRemove = configs.filter { it.key == pkg || it.value.profile.uid == uid }
                    toRemove.forEach {
                        Log.d(TAG, "remove config: $it")
                        configs.remove(it.key)
                    }
                } else {
                    Log.d(TAG, "change config: $config")
                    configs[config.pkg] = config
                }
                writeConfigs(configs)
            }
        }
    }
}