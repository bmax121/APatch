package me.bmax.apatch.util

import android.os.Parcelable
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
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
            fun fromLine(line: String): Config? {
                val sp = line.split(',', limit = 6)
                if (sp.size < 6) return null
                val pkg = sp[0].trim().removePrefix("\uFEFF")
                val exclude = sp[1].trim().toIntOrNull()
                val allow = sp[2].trim().toIntOrNull()
                val uid = sp[3].trim().toIntOrNull()
                val toUid = sp[4].trim().toIntOrNull()
                val scontext = sp[5].trim()
                if (pkg.isEmpty() || exclude == null || allow == null ||
                    uid == null || toUid == null || scontext.isEmpty()
                ) return null
                return Config(pkg, exclude, allow, Natives.Profile(uid, toUid, scontext))
            }
        }

        fun isDefault(): Boolean {
            return allow == 0 && exclude == 0
        }

        fun toLine(): String {
            return "${pkg},${exclude},${allow},${profile.uid},${profile.toUid},${profile.scontext}"
        }
    }

    fun readConfigs(): HashMap<Int, Config> {
        val configs = HashMap<Int, Config>()
        val file = File(APApplication.PACKAGE_CONFIG_FILE)
        if (file.exists()) {
            file.readLines().filter { it.isNotBlank() }.forEach {
                Log.d(TAG, it)
                // Skip the CSV header (and a possible UTF-8 BOM) quietly:
                // it is not a malformed row.
                val stripped = it.trimStart().removePrefix("\uFEFF")
                if (stripped == CSV_HEADER || stripped.startsWith("pkg,")) return@forEach
                val p = Config.fromLine(it)
                if (p == null) {
                    Log.w(TAG, "Skip malformed package_config line: $it")
                } else if (!p.isDefault()) {
                    configs[p.profile.uid] = p
                }
            }
        }
        return configs
    }

    private fun writeConfigs(configs: HashMap<Int, Config>) {
        val file = File(APApplication.PACKAGE_CONFIG_FILE)
        if (!file.parentFile?.exists()!!) file.parentFile?.mkdirs()
        // Write to a sibling temp file then rename into place: a concurrent
        // reader (apd uid-listener / kernel reload) must never observe a
        // half-written config, or it would treat every grant as gone. The temp
        // name is app-specific to avoid clashing with apd's own .tmp writer.
        val tmp = File(file.parentFile, "package_config.app.tmp")
        try {
            FileOutputStream(tmp, false).use { fos ->
                OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                    writer.write(CSV_HEADER + '\n')
                    configs.values.forEach {
                        if (!it.isDefault()) {
                            writer.write(it.toLine() + '\n')
                        }
                    }
                    writer.flush()
                }
                // Persist content before the atomic rename so a crash can only
                // leave the old file or the complete new file behind.
                fos.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                Log.e(TAG, "Failed to atomically replace ${file.path}")
                // Don't leave a stale tmp behind to confuse the next write or
                // a manual inspection; the original file is still intact.
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write package configs", e)
            tmp.delete()
        }
    }

    fun changeConfig(config: Config) {
        thread {
            synchronized(PkgConfig.javaClass) {
                Natives.su()
                val configs = readConfigs()
                val uid = config.profile.uid
                // Root App should not be excluded
                if (config.allow == 1) {
                    config.exclude = 0
                }
                if (config.allow == 0 && configs[uid] != null && config.exclude != 0) {
                    configs.remove(uid)
                } else {
                    Log.d(TAG, "change config: $config")
                    configs[uid] = config
                }
                writeConfigs(configs)
            }
        }
    }
}
