package me.bmax.apatch.ui.screen

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.system.Os
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.screen.destinations.InstallModeSelectScreenDestination
import me.bmax.apatch.ui.screen.destinations.PatchesDestination
import me.bmax.apatch.ui.screen.destinations.SettingScreenDestination
import me.bmax.apatch.ui.viewmodel.PatchesViewModel
import me.bmax.apatch.util.APDialogBlurBehindUtils
import me.bmax.apatch.util.DeviceUtils
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.Version.getManagerVersion
import me.bmax.apatch.util.checkNewVersion
import me.bmax.apatch.util.getSELinuxStatus
import me.bmax.apatch.util.reboot

@RootNavGraph(start = true)
@Destination
@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
    var showPatchFloatAction by remember { mutableStateOf(true) }

    val kpState by APApplication.kpStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val apState by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)

    if (kpState != APApplication.State.UNKNOWN_STATE) {
        showPatchFloatAction = false
    }

    Scaffold(topBar = {
        TopBar(onSettingsClick = {
            navigator.navigate(SettingScreenDestination, true)
        }, onInstallClick = {
            navigator.navigate(InstallModeSelectScreenDestination, true)
        })
    }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val checkUpdate =
                LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getBoolean("check_update", true)
            if (checkUpdate) {
                UpdateCard()
            }
            WarningCard()
            KStatusCard(kpState, navigator)
            AStatusCard(apState)
            InfoCard()
            LearnMoreCard()
            Spacer(Modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthSuperKey(showDialog: MutableState<Boolean>) {
    var key by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var enable by remember { mutableStateOf(false) }
    BasicAlertDialog(
        onDismissRequest = { showDialog.value = false },
        properties = DialogProperties(
            decorFitsSystemWindows = true,
            usePlatformDefaultWidth = false,
            securePolicy = SecureFlagPolicy.SecureOff
        )
    ) {
        Surface(
            modifier = Modifier
                .width(310.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(30.dp),
            tonalElevation = AlertDialogDefaults.TonalElevation,
            color = AlertDialogDefaults.containerColor,
        ) {
            Column(modifier = Modifier.padding(PaddingValues(all = 24.dp))) {
                // Title
                Box(
                    Modifier
                        .padding(PaddingValues(bottom = 16.dp))
                        .align(Alignment.Start)
                ) {
                    Text(
                        text = stringResource(id = R.string.home_auth_key_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                // Content
                Box(
                    Modifier
                        .weight(weight = 1f, fill = false)
                        .align(Alignment.Start)
                ) {
                    Text(
                        text = stringResource(id = R.string.home_auth_key_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Content2
                Box(
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    OutlinedTextField(
                        value = key,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        onValueChange = {
                            key = it
                            enable = key.isNotEmpty()
                        },
                        shape = RoundedCornerShape(50.0f),
                        label = { Text(stringResource(id = R.string.super_key)) },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    IconButton(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(top = 15.dp, end = 5.dp),
                        onClick = { keyVisible = !keyVisible }
                    ) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showDialog.value = false }) {
                        Text(stringResource(id = android.R.string.cancel))
                    }

                    Button(onClick = {
                        showDialog.value = false
                        APApplication.superKey = key
                    }) {
                        Text(stringResource(id = android.R.string.ok))
                    }
                }
            }
        }
        val dialogWindowProvider = LocalView.current.parent as DialogWindowProvider
        APDialogBlurBehindUtils.setupWindowBlurListener(dialogWindowProvider.window)
    }
}

@Composable
fun RebootDropdownItem(@StringRes id: Int, reason: String = "") {
    DropdownMenuItem(text = {
        Text(stringResource(id))
    }, onClick = {
        reboot(reason)
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onSettingsClick: () -> Unit, onInstallClick: () -> Unit) {
    TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = {
        IconButton(onClick = onInstallClick) {
            Icon(
                imageVector = Icons.Filled.InstallMobile,
                contentDescription = stringResource(id = R.string.mode_select_page_title)
            )
        }

        var showDropdown by remember { mutableStateOf(false) }
        IconButton(onClick = {
            showDropdown = true
        }) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(id = R.string.reboot)
            )

            DropdownMenu(expanded = showDropdown, onDismissRequest = {
                showDropdown = false
            }) {
                RebootDropdownItem(id = R.string.reboot)

                val pm =
                    LocalContext.current.getSystemService(Context.POWER_SERVICE) as PowerManager?
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm?.isRebootingUserspaceSupported == true) {
                    RebootDropdownItem(id = R.string.reboot_userspace, reason = "userspace")
                }
                RebootDropdownItem(id = R.string.reboot_recovery, reason = "recovery")
                RebootDropdownItem(id = R.string.reboot_bootloader, reason = "bootloader")
                RebootDropdownItem(id = R.string.reboot_download, reason = "download")
                RebootDropdownItem(id = R.string.reboot_edl, reason = "edl")
            }
        }

        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(id = R.string.settings)
            )
        }
    })
}

@Composable
private fun KStatusCard(kpState: APApplication.State, navigator: DestinationsNavigator) {
    val showAuthKeyDialog = remember { mutableStateOf(false) }
    if (showAuthKeyDialog.value) {
        AuthSuperKey(showDialog = showAuthKeyDialog)
    }

    ElevatedCard(
        onClick = {
            navigator.navigate(InstallModeSelectScreenDestination)
        },
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                Text(
                    text = stringResource(R.string.kernel_patch),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                when (kpState) {
                    APApplication.State.KERNELPATCH_INSTALLED -> {
                        Icon(Icons.Outlined.CheckCircle, stringResource(R.string.home_working))
                    }

                    APApplication.State.KERNELPATCH_NEED_UPDATE, APApplication.State.KERNELPATCH_NEED_REBOOT -> {
                        Icon(Icons.Outlined.SystemUpdate, stringResource(R.string.home_need_update))
                    }

                    else -> {
                        Icon(Icons.Outlined.Block, stringResource(R.string.home_ap_cando_uninstall))
                    }
                }
                Column(
                    Modifier
                        .weight(2f)
                        .padding(start = 16.dp)
                ) {
                    when (kpState) {
                        APApplication.State.KERNELPATCH_INSTALLED -> {
                            Text(
                                text = stringResource(R.string.home_working),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            // Ensure it doesn't crash on non-updated translations
                            var tmp = stringResource(R.string.kpatch_version)
                            tmp = tmp.replace("%d.%d.%d", "%s")
                            Text(
                                text = tmp.format(Version.installedKPVString()),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        APApplication.State.KERNELPATCH_NEED_UPDATE, APApplication.State.KERNELPATCH_NEED_REBOOT -> {
                            Text(
                                text = stringResource(R.string.home_need_update),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.kpatch_version_update,
                                    Version.installedKPVString(),
                                    Version.buildKPVString()
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        else -> {
                            Text(
                                text = stringResource(R.string.home_install_unknown),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    if (kpState != APApplication.State.UNKNOWN_STATE) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_su_path, Natives.suPath()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                ) {
                    Button(
                        onClick = {
                            when (kpState) {
                                APApplication.State.UNKNOWN_STATE -> {
                                    showAuthKeyDialog.value = true
                                }

                                APApplication.State.KERNELPATCH_NEED_UPDATE -> {
                                    // todo: remove legacy compact for kp < 0.9.0
                                    if (Version.installedKPVUInt() < 0x900u) {
                                        navigator.navigate(PatchesDestination(PatchesViewModel.PatchMode.PATCH_ONLY))
                                    } else {
                                        navigator.navigate(InstallModeSelectScreenDestination)
                                    }
                                }

                                APApplication.State.KERNELPATCH_NEED_REBOOT -> {
                                    reboot()
                                }

                                APApplication.State.KERNELPATCH_UNINSTALLING -> {
                                    // Do nothing
                                }

                                else -> {
                                    //showPatchDialog.value = true
                                    APApplication.uninstallApatch()
                                    navigator.navigate(PatchesDestination(PatchesViewModel.PatchMode.UNPATCH))
                                }
                            }
                        },
                        content = {
                            when (kpState) {
                                APApplication.State.UNKNOWN_STATE -> {
                                    Text(text = stringResource(id = R.string.super_key))
                                }

                                APApplication.State.KERNELPATCH_NEED_UPDATE -> {
                                    Text(text = stringResource(id = R.string.home_ap_cando_update))
                                }

                                APApplication.State.KERNELPATCH_NEED_REBOOT -> {
                                    Text(text = stringResource(id = R.string.home_ap_cando_reboot))
                                }

                                APApplication.State.KERNELPATCH_UNINSTALLING -> {
                                    Icon(Icons.Outlined.Cached, contentDescription = "busy")
                                }

                                else -> {
                                    Text(text = stringResource(id = R.string.home_ap_cando_uninstall))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AStatusCard(apState: APApplication.State) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                Text(
                    text = stringResource(R.string.android_patch),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                when (apState) {
                    APApplication.State.ANDROIDPATCH_NOT_INSTALLED -> {
                        Icon(Icons.Outlined.Block, stringResource(R.string.home_not_installed))
                    }

                    APApplication.State.ANDROIDPATCH_INSTALLING -> {
                        Icon(Icons.Outlined.InstallMobile, stringResource(R.string.home_installing))
                    }

                    APApplication.State.ANDROIDPATCH_INSTALLED -> {
                        Icon(Icons.Outlined.CheckCircle, stringResource(R.string.home_working))
                    }

                    APApplication.State.ANDROIDPATCH_NEED_UPDATE -> {
                        Icon(Icons.Outlined.SystemUpdate, stringResource(R.string.home_need_update))
                    }

                    else -> {
                        Icon(Icons.Outlined.Block, stringResource(R.string.home_install_unknown))
                    }
                }
                Column(
                    Modifier
                        .weight(2f)
                        .padding(start = 16.dp)
                ) {
                    val managerVersion = getManagerVersion()
                    when (apState) {
                        APApplication.State.ANDROIDPATCH_NOT_INSTALLED -> {
                            Text(
                                text = stringResource(R.string.home_not_installed),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        APApplication.State.ANDROIDPATCH_INSTALLING -> {
                            Text(
                                text = stringResource(R.string.home_installing),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        APApplication.State.ANDROIDPATCH_INSTALLED -> {
                            Text(
                                text = stringResource(R.string.home_working),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.apatch_version,
                                    managerVersion.second
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        APApplication.State.ANDROIDPATCH_NEED_UPDATE -> {
                            Text(
                                text = stringResource(R.string.home_need_update),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.apatch_version_update,
                                    Version.installedApdVString,
                                    managerVersion.second
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        else -> {
                            Text(
                                text = stringResource(R.string.home_install_unknown),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                if (apState != APApplication.State.UNKNOWN_STATE) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                    ) {
                        Button(
                            onClick = {
                                when (apState) {
                                    APApplication.State.ANDROIDPATCH_NOT_INSTALLED, APApplication.State.ANDROIDPATCH_NEED_UPDATE -> {
                                        APApplication.installApatch()
                                    }

                                    APApplication.State.ANDROIDPATCH_UNINSTALLING -> {
                                        // Do nothing
                                    }

                                    else -> {
                                        APApplication.uninstallApatch()
                                    }
                                }
                            },
                            content = {
                                when (apState) {
                                    APApplication.State.ANDROIDPATCH_NOT_INSTALLED -> {
                                        Text(text = stringResource(id = R.string.home_ap_cando_install))
                                    }

                                    APApplication.State.ANDROIDPATCH_NEED_UPDATE -> {
                                        Text(text = stringResource(id = R.string.home_ap_cando_update))
                                    }

                                    APApplication.State.ANDROIDPATCH_UNINSTALLING -> {
                                        Icon(Icons.Outlined.Cached, contentDescription = "busy")
                                    }

                                    else -> {
                                        Text(text = stringResource(id = R.string.home_ap_cando_uninstall))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun WarningCard() {
    var show by rememberSaveable { mutableStateOf(apApp.getBackupWarningState()) }
    if (show) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = run {
                MaterialTheme.colorScheme.error
            })
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = "warning")
                }
                Column(
                    modifier = Modifier
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(id = R.string.patch_warnning),
                        )

                        Spacer(Modifier.width(12.dp))

                        Icon(
                            Icons.Outlined.Clear,
                            contentDescription = "",
                            modifier = Modifier
                                .clickable {
                                    show = false
                                    apApp.updateBackupWarningState(false)
                                },
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun InfoCard() {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
        ) {
            val contents = StringBuilder()
            val uname = Os.uname()

            @Composable
            fun InfoCardItem(label: String, content: String) {
                contents.appendLine(label).appendLine(content).appendLine()
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                Text(text = content, style = MaterialTheme.typography.bodyMedium)
            }

            InfoCardItem(stringResource(R.string.home_kernel), uname.release)

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_fingerprint), Build.FINGERPRINT)

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_selinux_status), getSELinuxStatus())
        }
    }
}

@Composable
fun WarningCard(
    message: String, color: Color = MaterialTheme.colorScheme.error, onClick: (() -> Unit)? = null
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = color
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(onClick?.let { Modifier.clickable { it() } } ?: Modifier)
                .padding(24.dp)
        ) {
            Text(
                text = message, style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun UpdateCard() {
    val newVersion by produceState(initialValue = Triple(0, "", "")) {
        value = withContext(Dispatchers.IO) { checkNewVersion() }
    }
    val currentVersionCode = getManagerVersion().second
    val newVersionCode = newVersion.first
    val newVersionUrl = newVersion.second
    val changelog = newVersion.third

    val uriHandler = LocalUriHandler.current
    val title = stringResource(id = R.string.apm_changelog)
    val updateText = stringResource(id = R.string.apm_update)

    AnimatedVisibility(
        visible = newVersionCode >= currentVersionCode,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val updateDialog = rememberConfirmDialog(onConfirm = { uriHandler.openUri(newVersionUrl) })
        WarningCard(
            message = stringResource(id = R.string.home_new_apatch_found).format(newVersionCode),
            MaterialTheme.colorScheme.outlineVariant
        ) {
            if (changelog.isNotEmpty()) {
                updateDialog.showConfirm(
                    title = title,
                    content = changelog,
                    markdown = true,
                    confirm = updateText
                )
            }
        }
    }
}

@Composable
fun LearnMoreCard() {
    val uriHandler = LocalUriHandler.current
    val url = stringResource(R.string.home_learn_android_patch_url)

    ElevatedCard {
        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable {
                uriHandler.openUri(url)
            }
            .padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    text = stringResource(R.string.home_learn_apatch),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_click_to_learn_apatch),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}