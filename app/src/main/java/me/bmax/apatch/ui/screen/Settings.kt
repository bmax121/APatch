package me.bmax.apatch.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.AboutDialog
import me.bmax.apatch.ui.component.LoadingDialog
import me.bmax.apatch.ui.component.SwitchItem
import me.bmax.apatch.util.LocalDialogHost
import me.bmax.apatch.util.getBugreportFile
import me.bmax.apatch.util.isGlobalNamespaceEnabled
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
    var isGlobalNamespaceEnabled by rememberSaveable {
        mutableStateOf(false)
    }
    if (kPatchReady && aPatchReady) {
        isGlobalNamespaceEnabled = isGlobalNamespaceEnabled()
    }
    Scaffold(
        topBar = {
            TopBar(onBack = {
                navigator.popBackStack()
            })
        }
    ) { paddingValues ->
        LoadingDialog()

        val showAboutDialog = remember { mutableStateOf(false) }
        AboutDialog(showAboutDialog)

        val showLanguageDialog = rememberSaveable { mutableStateOf(false) }
        LanguageDialog(showLanguageDialog)

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()
        ) {

            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val dialogHost = LocalDialogHost.current

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
                        val uri: Uri =
                            FileProvider.getUriForFile(
                                context,
                                "${BuildConfig.APPLICATION_ID}.fileprovider",
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
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
        },
    )
}