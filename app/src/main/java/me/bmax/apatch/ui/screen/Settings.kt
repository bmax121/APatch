package me.bmax.apatch.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Masks
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.utils.app.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.BuildConfig.APPLICATION_ID
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.AboutDialog
import me.bmax.apatch.ui.component.LoadingDialog
import me.bmax.apatch.ui.component.SwitchItem
import me.bmax.apatch.util.HideAPK
import me.bmax.apatch.util.LocalDialogHost
import me.bmax.apatch.util.getBugreportFile
import me.bmax.apatch.util.isGlobalNamespaceEnabled
import me.bmax.apatch.util.isSkipStoreSuperKeyEnabled
import me.bmax.apatch.util.setSkipStoreSuperKeyEnabled
import me.bmax.apatch.util.rootShellForResult
import me.bmax.apatch.util.setGlobalNamespaceEnabled
import java.util.Locale

@Destination
@Composable
fun SettingScreen(navigator: DestinationsNavigator) {
    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val kPatchReady = state != APApplication.State.UNKNOWN_STATE
    val aPatchReady = (state == APApplication.State.ANDROIDPATCH_INSTALLING ||
                       state == APApplication.State.ANDROIDPATCH_INSTALLED ||
                       state == APApplication.State.ANDROIDPATCH_NEED_UPDATE)
    val bIsManagerHide = AppUtils.getPackageName() != APPLICATION_ID
    var isGlobalNamespaceEnabled by rememberSaveable {
        mutableStateOf(false)
    }
    var bSkipStoreSuperKey by rememberSaveable {
        mutableStateOf(false)
    }
    if (kPatchReady && aPatchReady) {
        isGlobalNamespaceEnabled = isGlobalNamespaceEnabled()
        bSkipStoreSuperKey = isSkipStoreSuperKeyEnabled()
    }
    Scaffold(
        topBar = {
            TopBar(onBack = {
                navigator.popBackStack()
            })
        }
    ) { paddingValues ->
        LoadingDialog()

        val showClearSuperKeyDialog = remember { mutableStateOf(false) }
        ClearSuperKeyDialog(showClearSuperKeyDialog)

        val showAboutDialog = remember { mutableStateOf(false) }
        AboutDialog(showAboutDialog)

        val showLanguageDialog = rememberSaveable { mutableStateOf(false) }
        LanguageDialog(showLanguageDialog)

        val showRandomizePkgNameDialog = rememberSaveable { mutableStateOf(false) }
        if (showRandomizePkgNameDialog.value) {
            RandomizePkgNameDialog(showDialog = showRandomizePkgNameDialog)
        }

        val showResetSuPathDialog = remember { mutableStateOf(false) }
        if(showResetSuPathDialog.value) {
            ResetSUPathDialog(showResetSuPathDialog)
        }

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()
        ) {

            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val dialogHost = LocalDialogHost.current
            
            if (kPatchReady) {
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Filled.Key,
                            stringResource(id = R.string.super_key)
                        )
                    },
                    headlineContent = { Text(stringResource(id = R.string.clear_super_key)) },
                    modifier = Modifier.clickable {
                        showClearSuperKeyDialog.value = true
                    }
                )
                SwitchItem(
                    icon = Icons.Filled.Key,
                    title = stringResource(id = R.string.settings_donot_store_superkey),
                    summary = stringResource(id = R.string.settings_donot_store_superkey_summary),
                    checked = bSkipStoreSuperKey,
                    onCheckedChange = {
                        setSkipStoreSuperKeyEnabled(
                            if (bSkipStoreSuperKey) {
                                1
                            } else {
                                0
                            }
                        )
                        bSkipStoreSuperKey = it
                    }
                )
            }

            if (kPatchReady && aPatchReady) {
                SwitchItem(
                    icon = Icons.Filled.Engineering,
                    title = stringResource(id = R.string.settings_global_namespace_mode),
                    summary = stringResource(id = R.string.settings_global_namespace_mode_summary),
                    checked = isGlobalNamespaceEnabled,
                    onCheckedChange = {
                        setGlobalNamespaceEnabled(
                            if (isGlobalNamespaceEnabled) {
                                "0"
                            } else {
                                "1"
                            }
                        )
                        isGlobalNamespaceEnabled = it
                    }
                )
            }

            if (kPatchReady && !bIsManagerHide) {
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Filled.Masks,
                            stringResource(id = R.string.hide_apatch_manager)
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.hide_apatch_manager_summary))
                    },
                    headlineContent = { Text(stringResource(id = R.string.hide_apatch_manager)) },
                    modifier = Modifier.clickable {
                        showRandomizePkgNameDialog.value = true
                    }
                )
            }

            if (kPatchReady) {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Filled.Commit,
                            stringResource(id = R.string.setting_reset_su_path)
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(id = R.string.setting_reset_su_path_summary)
                        )
                    },
                    headlineContent = { Text(stringResource(id = R.string.setting_reset_su_path)) },
                    modifier = Modifier.clickable {
                        showResetSuPathDialog.value = true
                    }
                )
            }

            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_app_language))
                },
                modifier = Modifier.clickable {
                    showLanguageDialog.value = true
                },
                supportingContent = {
                    Text(
                        text = AppCompatDelegate.getApplicationLocales()[0]?.displayLanguage?.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(
                                Locale.getDefault()
                            ) else it.toString()
                        } ?: stringResource(id = R.string.system_default)
                    )
                },
                leadingContent = { Icon(Icons.Filled.Translate, null) }
            )

            ListItem(
                leadingContent = {
                    Icon(
                        Icons.Filled.BugReport,
                        stringResource(id = R.string.send_log)
                    )
                },
                headlineContent = { Text(stringResource(id = R.string.send_log)) },
                modifier = Modifier.clickable {
                    scope.launch {
                        val bugreport = dialogHost.withLoading {
                            withContext(Dispatchers.IO) {
                                getBugreportFile(context)
                            }
                        }
                        val myPkgName = AppUtils.getPackageName()
                        val uri: Uri =
                            FileProvider.getUriForFile(
                                context,
                                "${myPkgName}.fileprovider",
                                bugreport
                            )
                        val shareIntent = Intent(Intent.ACTION_SEND)
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                        shareIntent.setDataAndType(uri, "application/zip")
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                        context.startActivity(
                            Intent.createChooser(
                                shareIntent,
                                context.getString(R.string.send_log)
                            )
                        )
                    }
                }
            )

            val about = stringResource(id = R.string.about)
            ListItem(
                leadingContent = {
                    Icon(
                        Icons.Filled.ContactPage,
                        stringResource(id = R.string.about)
                    )
                },
                headlineContent = { Text(about) },
                modifier = Modifier.clickable {
                    showAboutDialog.value = true
                }
            )
        }
    }
}

val suPathChecked: (path: String)-> Boolean = {
    it.startsWith("/") && it.trim().length > 1
}
@Composable
fun ResetSUPathDialog(showDialog: MutableState<Boolean>) {
    val context = LocalContext.current
    var suPath by remember { mutableStateOf(Natives.suPath()) }
    AlertDialog(
        onDismissRequest = { showDialog.value = false },
        title = { Text(stringResource(id = R.string.setting_reset_su_path)) },
        text = {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TextField(
                        value = suPath,
                        onValueChange = {
                            suPath = it
                        },
                        label = { Text(stringResource(id = R.string.setting_reset_su_new_path)) },
                        visualTransformation = VisualTransformation.None,
                    )
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
                enabled = suPathChecked(suPath),
                onClick = {
                    showDialog.value = false
                    val succ = Natives.resetSuPath(suPath)
                    Toast.makeText(context, if(succ) R.string.success else R.string.failure, Toast.LENGTH_SHORT).show()
                    rootShellForResult("echo $suPath > ${APApplication.SU_PATH_FILE}")
                }
            ) {
                Text(stringResource(id = android.R.string.ok))
            }
        },
    )
}


@Composable
fun RandomizePkgNameDialog(showDialog: MutableState<Boolean>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var newPackageName by remember { mutableStateOf("") }
    var enable by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { showDialog.value = false },
        title = { Text(stringResource(id = R.string.hide_apatch_manager)) },
        text = {
            Column {
                Text(stringResource(id = R.string.hide_apatch_dialog_summary))
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TextField(
                        value = newPackageName,
                        onValueChange = {
                            newPackageName = it
                            enable = newPackageName.isNotEmpty()
                        },
                        label = { Text(stringResource(id = R.string.hide_apatch_dialog_new_manager_name)) },
                        visualTransformation = VisualTransformation.None,
                    )
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
                    scope.launch { HideAPK.hide(context, newPackageName) }

                }
            ) {
                Text(stringResource(id = android.R.string.ok))
            }
        },
    )
}


@Composable
fun LanguageDialog(showLanguageDialog: MutableState<Boolean>) {

    val languages = stringArrayResource(id = R.array.languages)
    val languagesValues = stringArrayResource(id = R.array.languages_values)

    if (showLanguageDialog.value) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog.value = false },
            text = {
                LazyColumn {
                    itemsIndexed(languages) { index, item ->
                        ListItem(
                            headlineContent = { Text(item) },
                            modifier = Modifier.clickable {
                                showLanguageDialog.value = false
                                if (index == 0) {
                                    AppCompatDelegate.setApplicationLocales(
                                        LocaleListCompat.getEmptyLocaleList()
                                    )
                                } else {
                                    AppCompatDelegate.setApplicationLocales(
                                        LocaleListCompat.forLanguageTags(
                                            languagesValues[index]
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onBack: () -> Unit = {}) {
    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
            IconButton(
                onClick = onBack
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
        },
    )
}

@Composable
private fun ClearSuperKeyDialog(showClearSuperKeyDialog: MutableState<Boolean>) {
    if (showClearSuperKeyDialog.value) {
        AlertDialog(
            onDismissRequest = { showClearSuperKeyDialog.value = false },
            title = { Text(stringResource(id = R.string.clear_super_key)) },
            text = { Text(stringResource(id = R.string.settings_clear_super_key_dialog)) },
            dismissButton = {
                TextButton(
                    onClick = { showClearSuperKeyDialog.value = false }
                ) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearSuperKeyDialog.value = false
                        apApp.clearKey()
                    }
                ) {
                    Text(stringResource(id = android.R.string.ok))
                }
            }
        )
    }
}