package me.bmax.akpatch.viewmodel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bmax.akpatch.KPNatives
import me.bmax.akpatch.pApp

class ConfigViewModel() : ViewModel() {
    private val sharedPreferences = pApp.getSharedPreferences("config", Context.MODE_PRIVATE)

    fun load(key: String): String {
        return sharedPreferences.getString(key, "") ?: ""
    }

    fun put(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    val configData = MutableLiveData<String>()

    fun putValue(dataStore: DataStore<Preferences>, content: String, key: Preferences.Key<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { settings ->
                settings[key] = content
            }
        }
    }

    fun getValue(dataStore: DataStore<Preferences>,key: Preferences.Key<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { settings ->
                val text = settings[key]
                configData.postValue(text)
            }
        }
    }

    fun clearPreferences(dataStore: DataStore<Preferences>){
        viewModelScope.launch {
            dataStore.edit {
                it.clear()
            }
        }
    }
}
