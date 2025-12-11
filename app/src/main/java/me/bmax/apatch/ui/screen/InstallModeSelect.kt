package me.bmax.apatch.ui.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.PatchesDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.viewmodel.PatchesViewModel
import me.bmax.apatch.util.isABDevice
import me.bmax.apatch.util.rootAvailable
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back

var selectedBootImage: Uri? = null

@Destination<RootGraph>
@Composable
fun InstallModeSelectScreen(navigator: DestinationsNavigator) {
    var installMethod by remember {
        mutableStateOf<InstallMethod?>(null)
    }

    val showDialog = remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopBar(
            onBack = dropUnlessResumed { navigator.popBackStack() },
        )
    }, popupHost = {
        InstallSlotWarningDialog(showDialog)
    }) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(start = 10.dp, end = 10.dp),
        ) {
            SelectInstallMethod(
                onSelected = { method ->
                    installMethod = method
                },
                navigator = navigator,
                showDialog = showDialog
            )

        }
    }
}

sealed class InstallMethod {
    data class SelectFile(
        val uri: Uri? = null,
        @param:StringRes override val label: Int = R.string.mode_select_page_select_file,
    ) : InstallMethod()

    data object DirectInstall : InstallMethod() {
        override val label: Int
            get() = R.string.mode_select_page_patch_and_install
    }

    data object DirectInstallToInactiveSlot : InstallMethod() {
        override val label: Int
            get() = R.string.mode_select_page_install_inactive_slot
    }

    abstract val label: Int
    open val summary: String? = null
}

@Composable
private fun SelectInstallMethod(
    onSelected: (InstallMethod) -> Unit = {},
    navigator: DestinationsNavigator,
    showDialog: MutableState<Boolean>
) {
    val rootAvailable = rootAvailable()
    val isAbDevice = isABDevice()

    val radioOptions =
        mutableListOf<InstallMethod>(InstallMethod.SelectFile())
    if (rootAvailable) {
        radioOptions.add(InstallMethod.DirectInstall)
        if (isAbDevice) {
            radioOptions.add(InstallMethod.DirectInstallToInactiveSlot)
        }
    }

    var selectedOption by remember { mutableStateOf<InstallMethod?>(null) }
    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val option = InstallMethod.SelectFile(uri)
                selectedOption = option
                onSelected(option)
                selectedBootImage = option.uri
                navigator.navigate(PatchesDestination(PatchesViewModel.PatchMode.PATCH_ONLY))
            }
        }
    }

    val confirmDialog = rememberConfirmDialog(onConfirm = {
        selectedOption = InstallMethod.DirectInstallToInactiveSlot
        onSelected(InstallMethod.DirectInstallToInactiveSlot)
        navigator.navigate(PatchesDestination(PatchesViewModel.PatchMode.INSTALL_TO_NEXT_SLOT))
    }, onDismiss = null)

    val onClick = { option: InstallMethod ->
        when (option) {
            is InstallMethod.SelectFile -> {
                // Reset before selecting
                selectedBootImage = null
                selectImageLauncher.launch(
                    Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "application/octet-stream"
                    }
                )
            }

            is InstallMethod.DirectInstall -> {
                selectedOption = option
                onSelected(option)
                navigator.navigate(PatchesDestination(PatchesViewModel.PatchMode.PATCH_AND_INSTALL))
            }

            is InstallMethod.DirectInstallToInactiveSlot -> {
                showDialog.value = true
            }
        }
    }

    Column {
        radioOptions.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth())
            {
                SuperArrow(
                    title = stringResource(id = option.label),
                    onClick = {
                        onClick(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit = {}) {
    TopAppBar(
        title = stringResource(R.string.mode_select_page_title),
        navigationIcon = {
            IconButton(
                onClick = onBack,
                Modifier.padding(start = 16.dp)
            ) { Icon(MiuixIcons.Useful.Back, contentDescription = null) }
        },
    )
}

@Composable
fun InstallSlotWarningDialog(
    showDialog: MutableState<Boolean>
) {
    SuperDialog(
        show = showDialog,
        title = stringResource(android.R.string.dialog_alert_title),
        summary = stringResource(R.string.mode_select_page_install_inactive_slot_warning),
        onDismissRequest = { showDialog.value = false }
    ) {

        Box(contentAlignment = Alignment.CenterEnd) {

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {

                TextButton(
                    stringResource(id = android.R.string.cancel),
                    onClick = { showDialog.value = false },
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(20.dp))

                val enable = true

                TextButton(
                    stringResource(id = android.R.string.ok),
                    onClick = {

                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = enable
                )
            }
        }
    }
}