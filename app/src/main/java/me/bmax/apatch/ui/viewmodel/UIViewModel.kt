package me.bmax.apatch.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class UIViewModel: ViewModel() {
    var patchMode = PatchesViewModel.PatchMode.PATCH_ONLY
    lateinit var apModuleUri: Uri

    var darkTheme by mutableStateOf(false)
    var nightFollowSystem by mutableStateOf(true)
    var dynamicColor by mutableStateOf(true)
    var customColorScheme: String by mutableStateOf("blue")
}
