package me.bmax.apatch.ui.screen

import android.os.Build
import android.system.Os
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.SystemUpdate
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AboutScreenDestination
import com.ramcosta.composedestinations.generated.destinations.InstallModeSelectScreenDestination
import com.ramcosta.composedestinations.generated.destinations.PatchesDestination
import com.ramcosta.composedestinations.generated.destinations.UninstallModeSelectScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.DropdownItem
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.viewmodel.PatchesViewModel
import me.bmax.apatch.util.LatestVersionInfo
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.Version.getManagerVersion
import me.bmax.apatch.util.checkNewVersion
import me.bmax.apatch.util.getSELinuxStatus
import me.bmax.apatch.util.reboot
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopup
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.utils.overScrollVertical

private val managerVersion = getManagerVersion()

@Destination<RootGraph>(start = true)
@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = MiuixScrollBehavior()

    val kpState by APApplication.kpStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val apState by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)

    Scaffold(
        topBar = {
            TopBar(
                onInstallClick = dropUnlessResumed {
                    navigator.navigate(InstallModeSelectScreenDestination)
                },
                navigator,
                kpState,
                scrollBehavior = scrollBehavior
            )
        }) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BackupWarningCard()
                    KStatusCard(
                        kpState = kpState,
                        apState = apState,
                        navigator = navigator
                    )
                    if (kpState != APApplication.State.UNKNOWN_STATE && apState != APApplication.State.ANDROIDPATCH_INSTALLED) {
                        AStatusCard(apState)
                    }
                    UnofficialVersionCard()
                    val checkUpdate =
                        APApplication.sharedPreferences.getBoolean("check_update", true)
                    if (checkUpdate) {
                        UpdateCard()
                    }
                    InfoCard(kpState, apState)
                    LearnMoreCard()
                    Spacer(Modifier)
                }
            }
        }
    }
}

@Composable
fun AuthFailedTipDialog(showDialog: MutableState<Boolean>) {
    SuperDialog(
        title = stringResource(R.string.home_dialog_auth_fail_title),
        summary = stringResource(R.string.home_dialog_auth_fail_content),
        show = showDialog,
        onDismissRequest = { showDialog.value = false },
    ) {
        Spacer(Modifier.height(12.dp))

        Row {
            TextButton(
                stringResource(android.R.string.ok),
                onClick = { showDialog.value = false },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

val checkSuperKeyValidation: (superKey: String) -> Boolean = { superKey ->
    superKey.length in 8..63 && superKey.any { it.isDigit() } && superKey.any { it.isLetter() }
}

@Composable
fun AuthSuperKey(showDialog: MutableState<Boolean>, showFailedDialog: MutableState<Boolean>) {
    var key by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var enable by remember { mutableStateOf(false) }

    SuperDialog(
        show = showDialog,
        title = stringResource(R.string.home_auth_key_title),
        summary = stringResource(R.string.home_auth_key_desc),
        onDismissRequest = { showDialog.value = false }
    ) {

        Box(contentAlignment = Alignment.CenterEnd) {

            TextField(
                value = key,
                onValueChange = {
                    key = it
                    enable = checkSuperKeyValidation(key)
                },
                label = stringResource(R.string.super_key),
                visualTransformation =
                    if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            IconButton(
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 8.dp),
                onClick = { keyVisible = !keyVisible }
            ) {
                Icon(
                    imageVector = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
        }

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

            TextButton(
                stringResource(id = android.R.string.ok),
                onClick = {
                    showDialog.value = false
                    val ok = Natives.nativeReady(key)
                    if (ok) APApplication.superKey = key
                    else showFailedDialog.value = true
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                enabled = enable
            )
        }
    }
}

@Composable
private fun TopBar(
    onInstallClick: () -> Unit,
    navigator: DestinationsNavigator,
    kpState: APApplication.State,
    scrollBehavior: ScrollBehavior
) {
    val uriHandler = LocalUriHandler.current
    val showDropdownMoreOptions = remember { mutableStateOf(false) }
    val howDropdownReboot = remember { mutableStateOf(false) }

    val rebootItems = listOf(
        stringResource(R.string.reboot),
        stringResource(R.string.reboot_recovery),
        stringResource(R.string.reboot_bootloader),
        stringResource(R.string.reboot_download),
        stringResource(R.string.reboot_edl),
    )

    val moreItems = listOf(
        stringResource(R.string.home_more_menu_feedback_or_suggestion),
        stringResource(R.string.home_more_menu_about)
    )

    TopAppBar(
        title = stringResource(R.string.app_name),
        actions = {
            IconButton(onClick = onInstallClick) {
                Icon(
                    imageVector = Icons.Filled.InstallMobile,
                    contentDescription = stringResource(id = R.string.mode_select_page_title)
                )
            }

            if (kpState != APApplication.State.UNKNOWN_STATE) {
                IconButton(onClick = {
                    howDropdownReboot.value = true
                }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(id = R.string.reboot)
                    )

                    ListPopup(
                        show = howDropdownReboot,
                        alignment = PopupPositionProvider.Align.Left,
                        onDismissRequest = { howDropdownReboot.value = false }
                    ) {
                        ListPopupColumn {
                            rebootItems.forEachIndexed { index, string ->
                                DropdownItem(
                                    text = string,
                                    optionSize = rebootItems.size,
                                    onSelectedIndexChange = {
                                        when (index) {
                                            0 -> reboot()
                                            1 -> reboot("recovery")
                                            2 -> reboot("bootloader")
                                            3 -> reboot("download")
                                            4 -> reboot("edl")
                                        }
                                        howDropdownReboot.value = false
                                    },
                                    index = index
                                )
                            }
                        }
                    }
                }
            }

            Box {
                IconButton(onClick = { showDropdownMoreOptions.value = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(id = R.string.settings)
                    )

                    ListPopup(
                        show = showDropdownMoreOptions,
                        alignment = PopupPositionProvider.Align.Left,
                        onDismissRequest = { showDropdownMoreOptions.value = false }
                    ) {
                        ListPopupColumn {
                            moreItems.forEachIndexed { index, string ->
                                DropdownItem(
                                    text = string,
                                    optionSize = moreItems.size,
                                    onSelectedIndexChange = {
                                        when (index) {
                                            0 -> uriHandler.openUri("https://github.com/bmax121/APatch/issues/new/choose")
                                            1 -> navigator.navigate(AboutScreenDestination)
                                        }
                                        showDropdownMoreOptions.value = false
                                    },
                                    index = index
                                )
                            }
                        }
                    }
                }
            }
        }, scrollBehavior = scrollBehavior
    )
}


@Composable
private fun KStatusCard(
    kpState: APApplication.State,
    apState: APApplication.State,
    navigator: DestinationsNavigator
) {

    val showAuthFailedTipDialog = remember { mutableStateOf(false) }
    AuthFailedTipDialog(showAuthFailedTipDialog)

    val showAuthKeyDialog = remember { mutableStateOf(false) }
    AuthSuperKey(showAuthKeyDialog, showAuthFailedTipDialog)

    Card(
        onClick = {
            if (kpState != APApplication.State.KERNELPATCH_INSTALLED) {
                navigator.navigate(InstallModeSelectScreenDestination)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (kpState == APApplication.State.KERNELPATCH_NEED_UPDATE) {
                Row {
                    Text(
                        text = stringResource(R.string.kernel_patch),
                        style = MiuixTheme.textStyles.body2
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (kpState) {
                    APApplication.State.KERNELPATCH_INSTALLED -> {
                        Icon(Icons.Filled.CheckCircle, stringResource(R.string.home_working))
                    }

                    APApplication.State.KERNELPATCH_NEED_UPDATE, APApplication.State.KERNELPATCH_NEED_REBOOT -> {
                        Icon(Icons.Outlined.SystemUpdate, stringResource(R.string.home_need_update))
                    }

                    else -> {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, "Unknown")
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
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        APApplication.State.KERNELPATCH_NEED_UPDATE, APApplication.State.KERNELPATCH_NEED_REBOOT -> {
                            Text(
                                text = stringResource(R.string.home_need_update),
                                style = MiuixTheme.textStyles.body2,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.kpatch_version_update,
                                    Version.installedKPVString(),
                                    Version.buildKPVString()
                                ), style = MiuixTheme.textStyles.body2
                            )
                        }

                        else -> {
                            Text(
                                text = stringResource(R.string.home_install_unknown),
                                style = MiuixTheme.textStyles.body2
                            )
                            Text(
                                text = stringResource(R.string.home_install_unknown_summary),
                                style = MiuixTheme.textStyles.body1
                            )
                        }
                    }
                    if (kpState != APApplication.State.UNKNOWN_STATE && kpState != APApplication.State.KERNELPATCH_NEED_UPDATE && kpState != APApplication.State.KERNELPATCH_NEED_REBOOT) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${Version.installedKPVString()} (${managerVersion.second}) - " + if (apState != APApplication.State.ANDROIDPATCH_NOT_INSTALLED) "Full" else "KernelPatch",
                            style = MiuixTheme.textStyles.body2
                        )
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Button(onClick = {
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
                                if (apState == APApplication.State.ANDROIDPATCH_INSTALLED || apState == APApplication.State.ANDROIDPATCH_NEED_UPDATE) {
                                    navigator.navigate(UninstallModeSelectScreenDestination)
                                } else {
                                    navigator.navigate(PatchesDestination(PatchesViewModel.PatchMode.UNPATCH))
                                }
                            }
                        }
                    }, content = {
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
                    })
                }
            }
        }
    }
}

@Composable
private fun AStatusCard(apState: APApplication.State) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                Text(
                    text = stringResource(R.string.android_patch),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
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
                        Icon(
                            Icons.AutoMirrored.Outlined.HelpOutline,
                            stringResource(R.string.home_install_unknown)
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(2f)
                        .padding(start = 16.dp)
                ) {

                    when (apState) {
                        APApplication.State.ANDROIDPATCH_NOT_INSTALLED -> {
                            Text(
                                text = stringResource(R.string.home_not_installed),
                                style = MiuixTheme.textStyles.body2
                            )
                        }

                        APApplication.State.ANDROIDPATCH_INSTALLING -> {
                            Text(
                                text = stringResource(R.string.home_installing),
                                style = MiuixTheme.textStyles.body2
                            )
                        }

                        APApplication.State.ANDROIDPATCH_INSTALLED -> {
                            Text(
                                text = stringResource(R.string.home_working),
                                style = MiuixTheme.textStyles.body2
                            )
                        }

                        APApplication.State.ANDROIDPATCH_NEED_UPDATE -> {
                            Text(
                                text = stringResource(R.string.home_need_update),
                                style = MiuixTheme.textStyles.body2
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.apatch_version_update,
                                    Version.installedApdVString,
                                    managerVersion.second
                                ), style = MiuixTheme.textStyles.body1
                            )
                        }

                        else -> {
                            Text(
                                text = stringResource(R.string.home_install_unknown),
                                style = MiuixTheme.textStyles.body2
                            )
                        }
                    }
                }
                if (apState != APApplication.State.UNKNOWN_STATE) {
                    Column(
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Button(
                            colors = ButtonDefaults.buttonColorsPrimary(),
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
                            }, content = {
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
                            })
                    }
                }
            }
        }
    }
}


@Composable
fun BackupWarningCard() {
    val show = rememberSaveable { mutableStateOf(apApp.getBackupWarningState()) }
    if (show.value) {
        Card(
            colors = CardDefaults.defaultColors(run {
                MiuixTheme.colorScheme.error
            })
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = "warning")
                }
                Column(
                    modifier = Modifier.padding(12.dp),
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
                            modifier = Modifier.clickable {
                                apApp.updateBackupWarningState(false)
                                show.value = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UnofficialVersionCard() {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = (stringResource(R.string.unofficial_warning_title)),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = (stringResource(R.string.unofficial_warning_summary)),
                    style = MiuixTheme.textStyles.body2
                )
            }
        }
    }
}

private fun getSystemVersion(): String {
    return "${Build.VERSION.RELEASE} ${if (Build.VERSION.PREVIEW_SDK_INT != 0) "Preview" else ""} (API ${Build.VERSION.SDK_INT})"
}

private fun getDeviceInfo(): String {
    var manufacturer =
        Build.MANUFACTURER[0].uppercaseChar().toString() + Build.MANUFACTURER.substring(1)
    if (!Build.BRAND.equals(Build.MANUFACTURER, ignoreCase = true)) {
        manufacturer += " " + Build.BRAND[0].uppercaseChar() + Build.BRAND.substring(1)
    }
    manufacturer += " " + Build.MODEL + " "
    return manufacturer
}

@Composable
private fun InfoCard(kpState: APApplication.State, apState: APApplication.State) {
    Card {
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
                Text(text = label, style = MiuixTheme.textStyles.body1)
                Text(text = content, style = MiuixTheme.textStyles.body2)
            }

            if (kpState != APApplication.State.UNKNOWN_STATE) {
                InfoCardItem(
                    stringResource(R.string.home_kpatch_version), Version.installedKPVString()
                )

                Spacer(Modifier.height(16.dp))
                InfoCardItem(stringResource(R.string.home_su_path), Natives.suPath())

                Spacer(Modifier.height(16.dp))
            }

            if (apState != APApplication.State.UNKNOWN_STATE && apState != APApplication.State.ANDROIDPATCH_NOT_INSTALLED) {
                InfoCardItem(
                    stringResource(R.string.home_apatch_version), managerVersion.second.toString()
                )
                Spacer(Modifier.height(16.dp))
            }

            InfoCardItem(stringResource(R.string.home_device_info), getDeviceInfo())

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_kernel), uname.release)

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_system_version), getSystemVersion())

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_fingerprint), Build.FINGERPRINT)

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_selinux_status), getSELinuxStatus())

        }
    }
}

@Composable
fun WarningCard(
    message: String, color: Color = MiuixTheme.colorScheme.error, onClick: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.defaultColors(color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(onClick?.let { Modifier.clickable { it() } } ?: Modifier)
                .padding(24.dp)) {
            Text(
                text = message, style = MiuixTheme.textStyles.body1
            )
        }
    }
}

@Composable
fun UpdateCard() {
    val latestVersionInfo = LatestVersionInfo()
    val newVersion by produceState(initialValue = latestVersionInfo) {
        value = withContext(Dispatchers.IO) {
            checkNewVersion()
        }
    }
    val currentVersionCode = managerVersion.second
    val newVersionCode = newVersion.versionCode
    val newVersionUrl = newVersion.downloadUrl
    val changelog = newVersion.changelog

    val uriHandler = LocalUriHandler.current
    val title = stringResource(id = R.string.apm_changelog)
    val updateText = stringResource(id = R.string.apm_update)

    AnimatedVisibility(
        visible = newVersionCode > currentVersionCode,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val updateDialog = rememberConfirmDialog(onConfirm = { uriHandler.openUri(newVersionUrl) })
        WarningCard(
            message = stringResource(id = R.string.home_new_apatch_found).format(newVersionCode),
            MiuixTheme.colorScheme.outline
        ) {
            if (changelog.isEmpty()) {
                uriHandler.openUri(newVersionUrl)
            } else {
                updateDialog.showConfirm(
                    title = title, content = changelog, markdown = true, confirm = updateText
                )
            }
        }
    }
}

@Composable
fun LearnMoreCard() {
    val uriHandler = LocalUriHandler.current

    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    uriHandler.openUri("https://apatch.dev")
                }
                .padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    text = stringResource(R.string.home_learn_apatch),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_click_to_learn_apatch),
                    style = MiuixTheme.textStyles.body2
                )
            }
        }
    }
}