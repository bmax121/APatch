package me.bmax.apatch

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import coil.Coil
import coil.ImageLoader
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer

lateinit var apApp: APApplication

val TAG = "APatch"

class APApplication : Application() {
    companion object {
        // todo: should we store super_key in SharedPreferences
        private const val SUPER_KEY = "super_key"
        private lateinit var sharedPreferences: SharedPreferences
        private val _readyLiveData = MutableLiveData<Boolean>()
        val readyLiveData: LiveData<Boolean> = _readyLiveData

        var superKey: String = ""
            get
            private set(value) {
                field = value
                _readyLiveData.value = Natives.nativeReady(value)
                Log.d(TAG, "ready " + _readyLiveData.value)
                sharedPreferences.edit().putString(SUPER_KEY, value).apply()
            }
    }

    fun updateSuperKey(password: String) {
        superKey = password
    }

    override fun onCreate() {
        super.onCreate()
        apApp = this

        // todo:
        sharedPreferences = getSharedPreferences("config", Context.MODE_PRIVATE)
        superKey = sharedPreferences.getString(SUPER_KEY, "") ?: ""

        val context = this
        val iconSize = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(iconSize, false, context))
                }
                .build()
        )
    }
}