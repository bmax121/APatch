package me.bmax.akpatch.ui.screen

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.system.Os
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.akpatch.Natives
import me.bmax.akpatch.R
import me.bmax.akpatch.ui.screen.destinations.SettingScreenDestination
import me.bmax.akpatch.ui.util.*
import me.bmax.akpatch.TAG
import me.bmax.akpatch.viewmodel.ConfigViewModel

@RootNavGraph(start = true)
@Destination
@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
    Scaffold(topBar = {
        TopBar(onSettingsClick = {
            navigator.navigate(SettingScreenDestination)
        })
    }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val superKey = remember { mutableStateOf("") }
            NoticeCard()
            StatusCard(superKey.value)
            SuperKeyCard(superKey = superKey)
            PatchCard(navigator = navigator)
            InfoCard()
        }
    }
}

@Composable
private fun NoticeCard() {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.primaryContainer
        })
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Warning, "")
            Column (Modifier.padding(start = 20.dp)) {
                Text(
                    text = stringResource(R.string.home_security_notice),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun StatusCard(superKey: String) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                Natives.installed(superKey) -> {
                    val appendText = ""
                    Icon(Icons.Outlined.CheckCircle, stringResource(R.string.home_working))
                    Column(Modifier.padding(start = 20.dp)) {
                        Text(
                            text = stringResource(R.string.home_working) + appendText,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_working_version, Natives.kernelPatchVersion()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                else -> {
                    Icon(Icons.Outlined.Warning, "")
                    Column(Modifier.padding(start = 20.dp)) {
                        Text(
                            text = stringResource(R.string.home_installed_unknow),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_need_skey_or_install),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SuperKeyCard(superKey: MutableState<String>) {
    var keyVisible by remember { mutableStateOf(false) }
    var authShowDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val configViewModel: ConfigViewModel = viewModel()
    val defSkey = configViewModel.load("skey")
    superKey.value = defSkey
    Natives.setSuperKey(defSkey)

    val context = LocalContext.current

    if(authShowDialog) {
        AlertDialog(
            icon = { Icon(imageVector = Icons.Filled.Celebration, contentDescription = "")},
            onDismissRequest = { authShowDialog = false },
            title = { Text(stringResource(id = R.string.auth_succeed)) },
            text = { Text(stringResource(id = R.string.auth_succeed_text)) },
            confirmButton = {
                TextButton(onClick = { authShowDialog = false}) {
                    Text("OK")
                }
            }
        )
    }
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(id = R.string.super_key), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.width(0.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = superKey.value,
                        onValueChange = {
                                newText ->
                            run {
                                superKey.value = newText
                                if(Natives.installed(newText)) {
                                    Log.d(TAG, "Android Kernel Patch Installed!")
                                    authShowDialog = true
                                    superKey.value = newText
                                    focusManager.clearFocus()
                                    Natives.setSuperKey(newText)
                                    configViewModel.put("skey", newText)
//                                    context.startService(Intent(context, LogService::class.java))
                                }
                            }
                        },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        ),
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    if (superKey.value.isBlank()) {
                        Text(stringResource(id = R.string.super_key_hint),
                            modifier = Modifier.alpha(0.4f))
                    }
                }
                IconButton(
                    onClick = { keyVisible = !keyVisible },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (keyVisible) Color.Gray else Color.Black
                    )
                }
            }
        }
        Column {
            Row(modifier = Modifier
                .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp,)
                        .alpha(0.6f),
                    text = stringResource(id = R.string.super_key_warnning), style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun InfoCard() {
    val context = LocalContext.current
    ElevatedCard (
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ){
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
        }
    }
}

fun getManagerVersion(context: Context): Pair<String, Int> {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return Pair(packageInfo.versionName, packageInfo.versionCode)
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
