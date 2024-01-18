package me.bmax.apatch.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.system.Os
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.utils.app.permission.PermissionUtils
import dev.utils.app.permission.PermissionUtils.PermissionCallback
import me.bmax.apatch.util.*
import me.bmax.apatch.*
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.ConfirmDialog
import me.bmax.apatch.ui.screen.destinations.PatchScreenDestination
import me.bmax.apatch.util.reboot
import me.bmax.apatch.ui.screen.destinations.SettingScreenDestination

@RootNavGraph(start = true)
@Destination
@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
    val kpState by APApplication.kpStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val apState by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)

    Scaffold(topBar = {
        TopBar(onSettingsClick = {
            navigator.navigate(SettingScreenDestination)
        })
    }, floatingActionButton = {

        FloatButton(navigator)

    }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WarningCard()
            KStatusCard(kpState, navigator)
            AStatusCard(apState)
            InfoCard()
            LearnMoreCard()
            Spacer(Modifier)
            ConfirmDialog()
        }
    }
}

@Composable
fun AuthSuperKey(showDialog: MutableState<Boolean>) {
    var key by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var enable by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { showDialog.value = false },
        title = { Text(stringResource(id = R.string.home_auth_key_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.home_auth_key_desc))
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TextField(
                        value = key,
                        onValueChange = {
                            key = it
                            enable = !key.isEmpty() },
                        label = { Text(stringResource(id = R.string.super_key)) },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    IconButton(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(end = 12.dp),
                        onClick = { keyVisible = !keyVisible }
                    ) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                }
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    showDialog.value = false
                }
            ) {
                Text(stringResource(id = android.R.string.cancel))
            }
        },
        confirmButton = {
            Button(
                enabled = enable,
                onClick = {
                    showDialog.value = false
                    apApp.updateSuperKey(key)
                }
            ) {
                Text(stringResource(id = android.R.string.ok))
            }
        },
    )
}

@Composable
fun StartPatch(showDialog: MutableState<Boolean>, navigator: DestinationsNavigator) {
    var key by remember { mutableStateOf("") }
    var enable by remember { mutableStateOf(false) }

    val selectBootimgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode != Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val data = it.data ?: return@rememberLauncherForActivityResult
        val uri = data.data ?: return@rememberLauncherForActivityResult
        navigator.navigate(PatchScreenDestination(uri, key))
    }

    AlertDialog(
        onDismissRequest = { showDialog.value = false },
        title = { Text(stringResource(id = R.string.home_patch_set_key_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.home_patch_set_key_desc))
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = key,
                    onValueChange = {
                        key = it;
                        enable = ! it.isEmpty()
                    },
                    label = { Text(stringResource(id = R.string.super_key)) },
                    visualTransformation = VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            Button(
                enabled = enable,
                onClick = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "*/*"
                    selectBootimgLauncher.launch(intent)
                }
            ) {
                Text(stringResource(id = R.string.home_patch_next_step))
            }
        },
    )
}

@Composable
fun FloatButton(navigator: DestinationsNavigator) {
    val apState by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)

    var showAuthKeyDialog = remember { mutableStateOf(false)  }
    var showSetKeyDialog = remember { mutableStateOf(false)  }
    var permissionRequest = remember { mutableStateOf(false)  }

    if (showAuthKeyDialog.value) {
        AuthSuperKey(showDialog = showAuthKeyDialog)
    }

    if (permissionRequest.value) {
        PermissionUtils.permission(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
            .callback( object : PermissionCallback{
                override fun onGranted() {
                    permissionRequest.value = false
                    showSetKeyDialog.value = true
                }
                override fun onDenied(
                    grantedList: MutableList<String>?,
                    deniedList: MutableList<String>?,
                    notFoundList: MutableList<String>?
                ) {
                    permissionRequest.value = false
                }
            })
            .request(LocalContext.current as Activity)
    }

    if (showSetKeyDialog.value) {
        StartPatch(showDialog = showSetKeyDialog, navigator)
    }

    Column(horizontalAlignment = Alignment.End) {
        Box() {
            ExtendedFloatingActionButton(
                onClick = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        permissionRequest.value = true
                    } else {
                        showSetKeyDialog.value = true
                    }
                },
                icon = { Icon(Icons.Filled.InstallMobile, "install") },
                text = { Text(text = stringResource(id = R.string.patch)) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Box() {
            ExtendedFloatingActionButton(
                onClick = {
                    if (apState != APApplication.State.UNKNOWN_STATE) {
                        apApp.updateSuperKey("")
                    } else {
                        showAuthKeyDialog.value = true
                    }
                },
                icon = { Icon(Icons.Filled.Key, "") },
                text = {
                    Text(
                        text = if (apState != APApplication.State.UNKNOWN_STATE) {
                            stringResource(id = R.string.clear_super_key)
                        } else {
                            stringResource(id = R.string.super_key)
                        }
                    )
                },
            )
        }
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
private fun TopBar(onSettingsClick: () -> Unit) {
    TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KStatusCard(kpState: APApplication.State, navigator: DestinationsNavigator) {
    val showInfoDialog = remember { mutableStateOf(false) }
    if (showInfoDialog.value) {
        InfoDialog(showDialog = showInfoDialog)
    }
    ElevatedCard(
        onClick = { showInfoDialog.value = true },
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Text(text = stringResource(R.string.kernel_patch),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    kpState.equals(APApplication.State.KERNELPATCH_INSTALLED) -> {
                        Icon(Icons.Outlined.CheckCircle, stringResource(R.string.home_working))
                    }
                    kpState.equals(APApplication.State.KERNELPATCH_NEED_UPDATE) -> {
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
                    when {
                        kpState.equals(APApplication.State.KERNELPATCH_INSTALLED) -> {
                            Text(text = stringResource(R.string.home_working),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(text = stringResource(R.string.kpatch_version, APApplication.kPatchVersion),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        kpState.equals(APApplication.State.KERNELPATCH_NEED_UPDATE) -> {
                            Text(text = stringResource(R.string.home_need_update),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(text = stringResource(R.string.kpatch_version_update, APApplication.kPatchVersion, getKPatchVersion()),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> {
                            Text(text = stringResource(R.string.home_install_unknown),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    if (!kpState.equals(APApplication.State.UNKNOWN_STATE)) {
                        Spacer(Modifier.height(4.dp))
                        Text(text = stringResource(R.string.home_superuser_count, getSuperuserCount()),
                             style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(text = stringResource(R.string.home_module_count, getModuleCount()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (kpState.equals(APApplication.State.KERNELPATCH_NEED_UPDATE)) {
                    Column (modifier = Modifier
                        .align(Alignment.CenterVertically)
                    ) {
                        Button(
                            onClick = {
                                APApplication.installKpatch()
                                navigator.navigate(PatchScreenDestination(null, apApp.getSuperKey()))
                            },
                            content = {
                                Text(text = stringResource(id = R.string.home_ap_cando_update), color = Color.Black)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoDialog(showDialog: MutableState<Boolean>) {
    val apState by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)

    AlertDialog(
        onDismissRequest = { showDialog.value = false },
        title = { Text(stringResource(id = R.string.home_kpatch_info_title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.home_su_path_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                when {
                    apState.equals(APApplication.State.ANDROIDPATCH_INSTALLED) ||
                    apState.equals(APApplication.State.ANDROIDPATCH_NEED_UPDATE) -> {
                        val suPath = "%s -> %s".format(Natives.suPath(), APApplication.APD_PATH)
                        Text(text = suPath,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(Modifier.height(16.dp))
                        Text(text = stringResource(R.string.kpatch_shadow_path_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        val kpPath = "%s -> %s".format(APApplication.KPATCH_SHADOW_PATH, APApplication.KPATCH_PATH)
                        Text(text = kpPath,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        val suPath = "%s".format(Natives.suPath())
                        Text(text = suPath,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    showDialog.value = false
                }
            ) {
                Text(stringResource(id = android.R.string.ok))
            }
        }
    )
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
                .padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Text(text = stringResource(R.string.android_patch),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    apState.equals(APApplication.State.ANDROIDPATCH_READY) -> {
                        Icon(Icons.Outlined.Block, stringResource(R.string.home_not_installed))
                    }
                    apState.equals(APApplication.State.ANDROIDPATCH_INSTALLING) -> {
                        Icon(Icons.Outlined.InstallMobile, stringResource(R.string.home_installing))
                    }
                    apState.equals(APApplication.State.ANDROIDPATCH_INSTALLED) -> {
                        Icon(Icons.Outlined.CheckCircle, stringResource(R.string.home_working))
                    }
                    apState.equals(APApplication.State.ANDROIDPATCH_NEED_UPDATE) -> {
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
                    val managerVersion = APApplication.getManagerVersion()
                    when {
                        apState.equals(APApplication.State.ANDROIDPATCH_READY) -> {
                            Text(text = stringResource(R.string.home_not_installed),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        apState.equals(APApplication.State.ANDROIDPATCH_INSTALLING) -> {
                            Text(text = stringResource(R.string.home_installing),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        apState.equals(APApplication.State.ANDROIDPATCH_INSTALLED) -> {
                            Text(text = stringResource(R.string.home_working),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(text = stringResource(R.string.apatch_version, managerVersion.second),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        apState.equals(APApplication.State.ANDROIDPATCH_NEED_UPDATE) -> {
                            Text(text = stringResource(R.string.home_need_update),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(text = stringResource(R.string.apatch_version_update, APApplication.aPatchVersion, managerVersion.second),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> {
                            Text(text = stringResource(R.string.home_install_unknown),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                if (!apState.equals(APApplication.State.UNKNOWN_STATE)) {
                    Column (modifier = Modifier
                        .align(Alignment.CenterVertically)
                    ) {
                        Button(
                            onClick = {
                                when {
                                    apState.equals(APApplication.State.ANDROIDPATCH_READY) ||
                                    apState.equals(APApplication.State.ANDROIDPATCH_NEED_UPDATE) -> {
                                        APApplication.installApatch()
                                    }
                                    else -> {
                                        APApplication.uninstallApatch()
                                    }
                                }
                            },
                            content = {
                                when {
                                    apState.equals(APApplication.State.ANDROIDPATCH_READY) -> {
                                        Text(text = stringResource(id = R.string.home_ap_cando_install), color = Color.Black)
                                    }
                                    apState.equals(APApplication.State.ANDROIDPATCH_UNINSTALLING) -> {
                                        Icon(Icons.Outlined.Cached, contentDescription = "busy")
                                    }
                                    apState.equals(APApplication.State.ANDROIDPATCH_NEED_UPDATE) -> {
                                        Text(text = stringResource(id = R.string.home_ap_cando_update), color = Color.Black)
                                    }
                                    else -> {
                                        Text(text = stringResource(id = R.string.home_ap_cando_uninstall), color = Color.Black)
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
            Row (
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column (
                    modifier = Modifier
                        .padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = "warning")
                }
                Column (
                    modifier = Modifier
                        .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row (
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ){
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(id = R.string.patch_warnning),
                        )

                        Spacer(Modifier.width(12.dp))

                        Icon(Icons.Outlined.Clear,
                            contentDescription =  "",
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
            Column() {
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
