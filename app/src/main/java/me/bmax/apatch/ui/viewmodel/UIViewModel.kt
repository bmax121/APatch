package me.bmax.apatch.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel

class UIViewModel: ViewModel() {
    var patchMode = PatchesViewModel.PatchMode.PATCH_ONLY

    lateinit var apModuleUri: Uri
}
