package me.bmax.apatch.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.system.Os
import android.util.Log
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.util.checkNewVersion
import me.bmax.apatch.util.getSELinuxStatus
import me.bmax.apatch.*
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.ConfirmDialog
import me.bmax.apatch.ui.component.ConfirmResult
import me.bmax.apatch.ui.screen.destinations.PatchScreenDestination
import me.bmax.apatch.util.LocalDialogHost
import me.bmax.apatch.util.reboot
import me.bmax.apatch.ui.screen.destinations.SettingScreenDestination

@RootNavGraph(start = true)
@Destination
@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
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
            val isManager = true
            SideEffect {
                // todo:
//                if (isManager) install()
            }
            KStatusCard()
            UpdateCard()
            InfoCard()
//            DonateCard()
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
        title = { Text("Enter SuperKey") },
        text = {
            Column {
                Text("Please enter the superkey set when patching")
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TextField(
                        value = key,
                        onValueChange = {
                            key = it
                            enable = !key.isEmpty() },
                        label = { Text("SuperKey") },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    IconButton(
                        modifier = Modifier.size(24.dp),
                        onClick = { keyVisible = !keyVisible }
                    ) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = if (keyVisible) Color.Gray else Color.Black
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
                Text("Cancel")
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
                Text("Confirm")
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
        title = { Text(stringResource(id = R.string.set_key_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.set_key_desc))
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
                Text(stringResource(id = R.string.next_step))
            }
        },
    )
}

@Composable
fun FloatButton(navigator: DestinationsNavigator) {
    val ready by APApplication.readyLiveData.observeAsState(initial = false)

    var patchOff by remember { mutableStateOf(IntOffset.Zero) }
    var skeyOff by remember { mutableStateOf(IntOffset.Zero) }

    var showAuthKeyDialog = remember { mutableStateOf(false)  }
    var showSetKeyDialog = remember { mutableStateOf(false)  }

    if (showAuthKeyDialog.value) {
        AuthSuperKey(showDialog = showAuthKeyDialog)
    }

    if(showSetKeyDialog.value) {
        StartPatch(showDialog = showSetKeyDialog, navigator)
    }

    Column() {
        Box(Modifier
            .offset { patchOff }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    patchOff += dragAmount.round()
                }
            }
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    showSetKeyDialog.value = true
                },
                icon = { Icon(Icons.Filled.InstallMobile, "install") },
                text = { Text(text = "Patch") },
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier
            .offset { skeyOff }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    skeyOff += dragAmount.round()
                }
            }
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    if(ready) {
                        apApp.updateSuperKey("")
                    } else {
                        showAuthKeyDialog.value = true
                    }
                },
                icon = { Icon(Icons.Filled.Key, "key") },
                text = { Text(text = if(ready) "clear" else "sky" )},
            )
        }
    }
}

@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val newVersion by produceState(initialValue = Triple(0, "", "")) {
        value = withContext(Dispatchers.IO) { checkNewVersion() }
    }
    val currentVersionCode = getManagerVersion(context).second
    val newVersionCode = newVersion.first
    val newVersionUrl = newVersion.second
    val changelog = newVersion.third
    if (newVersionCode <= currentVersionCode) {
        return
    }

    val uriHandler = LocalUriHandler.current
    val dialogHost = LocalDialogHost.current
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)
    val scope = rememberCoroutineScope()
    WarningCard(
        message = stringResource(id = R.string.new_version_available).format(newVersionCode),
        MaterialTheme.colorScheme.outlineVariant
    ) {
        scope.launch {
            if (changelog.isEmpty() || dialogHost.showConfirm(
                    title = title,
                    content = changelog,
                    markdown = true,
                    confirm = updateText,
                ) == ConfirmResult.Confirmed
            ) {
                uriHandler.openUri(newVersionUrl)
            }
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

@Composable
private fun KStatusCard() {
    val ready by APApplication.readyLiveData.observeAsState(initial = false)
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.kernel_patch),
                style = MaterialTheme.typography.titleMedium
            )
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    ready -> {
                        val appendText = ""
                        val kernelPatchVersion = Natives.kerenlPatchVersion()
                        Icon(Icons.Outlined.CheckCircle, stringResource(R.string.working))
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(
                                text = stringResource(R.string.working) + appendText,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.home_working_version, kernelPatchVersion.and(0xff0000).shr(16),
                                    kernelPatchVersion.and(0xff00).shr(8), kernelPatchVersion.and(0xff)),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.home_su_path, Natives.suPath()),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    else -> {
                        Icon(Icons.Outlined.Warning, "")
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(
                                text = stringResource(R.string.home_install_unknown),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
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
fun LearnMoreCard() {
    val uriHandler = LocalUriHandler.current
    val url = stringResource(R.string.home_learn_kernelsu_url)

    ElevatedCard {

        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable {
                uriHandler.openUri(url)
            }
            .padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column() {
                Text(
                    text = stringResource(R.string.home_learn_kernelsu),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_click_to_learn_kernelsu),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun DonateCard() {
    val uriHandler = LocalUriHandler.current

    ElevatedCard {

        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable {
                uriHandler.openUri("https://xxx")
            }
            .padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column() {
                Text(
                    text = stringResource(R.string.home_support_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_support_content),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun InfoCard() {
    val context = LocalContext.current

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
            val managerVersion = getManagerVersion(context)
            InfoCardItem(
                stringResource(R.string.home_manager_version),
                "${managerVersion.first} (${managerVersion.second})"
            )

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_fingerprint), Build.FINGERPRINT)

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_selinux_status), getSELinuxStatus())
        }
    }
}

fun getManagerVersion(context: Context): Pair<String, Int> {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return Pair(packageInfo.versionName, packageInfo.versionCode)
}


@Preview
@Composable
private fun WarningCardPreview() {
    Column {
        WarningCard(message = "Warning message")
        WarningCard(
            message = "Warning message ",
            MaterialTheme.colorScheme.outlineVariant,
            onClick = {})
    }
}