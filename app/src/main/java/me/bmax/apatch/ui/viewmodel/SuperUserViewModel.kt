package me.bmax.apatch.ui.viewmodel

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.parcelize.Parcelize
import me.bmax.apatch.Natives
import me.bmax.apatch.apApp
import me.bmax.apatch.util.HanziToPinyin
import me.bmax.apatch.util.PkgConfig
import me.bmax.apatch.util.ServicesUtil
import java.text.Collator
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.thread


class SuperUserViewModel : ViewModel() {
    companion object {
        private const val TAG = "SuperUserViewModel"
        private var apps by mutableStateOf<List<AppInfo>>(emptyList())
    }

    @Parcelize
    data class AppInfo(
        val label: String,
        val packageInfo: PackageInfo,
        val config: PkgConfig.Config
    ) : Parcelable {
        val packageName: String
            get() = packageInfo.packageName
        val uid: Int
            get() = packageInfo.applicationInfo.uid
    }

    var search by mutableStateOf("")
    var showSystemApps by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
        private set

    private val sortedList by derivedStateOf {
        val comparator = compareBy<AppInfo> {
            when {
                it.config.allow != 0 -> 0
                it.config.exclude == 0 -> 1
                else -> 2
            }
        }.then(compareBy(Collator.getInstance(Locale.getDefault()), AppInfo::label))
        apps.sortedWith(comparator).also {
            isRefreshing = false
        }
    }

    val appList by derivedStateOf {
        sortedList.filter {
            it.label.contains(search) || it.packageName.contains(search) || HanziToPinyin.getInstance()
                .toPinyinString(it.label).contains(search)
        }.filter {
            it.uid == 2000 // Always show shell
                    || showSystemApps || it.packageInfo.applicationInfo.flags.and(ApplicationInfo.FLAG_SYSTEM) == 0
        }
    }


    fun fetchAppList() {
        isRefreshing = true

        thread {
            Natives.su(0, "")
            val uids = Natives.suUids().toList()
            Log.d(TAG, "all allows: ${uids}")
            val configs: HashMap<String, PkgConfig.Config> = PkgConfig.configs
            Log.d(TAG, "all configs: ${configs}")

            val allPackages = ServicesUtil.getInstalledPackagesAll(apApp, 0)

            apps = allPackages.map { it ->
                val appInfo = it.applicationInfo
                val uid = appInfo.uid
                val actProfile = if(uids.contains(uid)) Natives.suProfile(uid) else null
                val config = configs.getOrDefault(appInfo.packageName,
                    PkgConfig.Config(appInfo.packageName, 1, 0, Natives.Profile(uid)))
                config.allow = 0
                // profile from kernel
                if(actProfile != null) {
                    config.allow = 1
                    config.profile = actProfile
                }
                AppInfo(
                    label = appInfo.loadLabel(apApp.packageManager).toString(),
                    packageInfo = it,
                    config = config
                )
            }.filter { it.packageName != apApp.packageName }
        }
    }
}
