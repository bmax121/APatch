package me.bmax.apatch.ui.viewmodel

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.IBinder
import android.os.Parcelable
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.bmax.apatch.IKsuInterface
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.APatchService
import me.bmax.apatch.util.HanziToPinyin
import me.bmax.apatch.util.APatchCli
import java.text.Collator
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SuperUserViewModel : ViewModel() {

    companion object {
        private const val TAG = "SuperUserViewModel"
        private var apps by mutableStateOf<List<AppInfo>>(emptyList())
    }

    @Parcelize
    data class AppInfo(
        val label: String,
        val packageInfo: PackageInfo,
//        val profile: Natives.Profile?,
    ) : Parcelable {
        val packageName: String
            get() = packageInfo.packageName
        val uid: Int
            get() = packageInfo.applicationInfo.uid

        val allowSu: Boolean
            get() {
                return false
            }

        val hasCustomProfile: Boolean
            get() {
                return false
            }
    }

    var search by mutableStateOf("")
    var showSystemApps by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
        private set

    private val sortedList by derivedStateOf {
        val comparator = compareBy<AppInfo> {
            when {
                it.allowSu -> 0
                it.hasCustomProfile -> 1
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

    private suspend inline fun connectAPatchService(
        crossinline onDisconnect: () -> Unit = {}
    ): Pair<IBinder, ServiceConnection> = suspendCoroutine {
        val connection = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
                onDisconnect()
            }

            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                it.resume(binder as IBinder to this)
            }
        }

        val intent = Intent(apApp, APatchService::class.java);

        val task = APatchService.bindOrTask(
            intent,
            Shell.EXECUTOR,
            connection,
        )
        val shell = APatchCli.SHELL
        task?.let { it1 -> shell.execTask(it1) }
    }

    private fun stopAPatchService() {
        val intent = Intent(apApp, APatchService::class.java);
        APatchService.stop(intent)
    }

    suspend fun fetchAppList() {

        isRefreshing = true

        val result = connectAPatchService {
            Log.w(TAG, "APatchService disconnected")
        }

        withContext(Dispatchers.IO) {
            val pm = apApp.packageManager
            val start = SystemClock.elapsedRealtime()

            val binder = result.first
            val allPackages = IKsuInterface.Stub.asInterface(binder).getPackages(0)

            withContext(Dispatchers.Main) {
                stopAPatchService()
            }

            val packages = allPackages.list

            apps = packages.map {
                val appInfo = it.applicationInfo
                val uid = appInfo.uid
//                val profile = Natives.getAppProfile(it.packageName, uid)
                AppInfo(
                    label = appInfo.loadLabel(pm).toString(),
                    packageInfo = it,
//                    profile = profile,
                )
            }.filter { it.packageName != apApp.packageName }
            Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}")
        }
    }
}
