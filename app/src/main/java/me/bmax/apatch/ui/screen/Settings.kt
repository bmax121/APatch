package me.bmax.apatch.ui.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import com.ramcosta.composedestinations.annotation.Destination
import dev.utils.app.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.SwitchItem
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.util.APatchKeyHelper
import me.bmax.apatch.util.getBugreportFile
import me.bmax.apatch.util.hideapk.HideAPK
import me.bmax.apatch.util.isGlobalNamespaceEnabled
import me.bmax.apatch.util.rootShellForResult
import me.bmax.apatch.util.setGlobalNamespaceEnabled
import me.bmax.apatch.util.ui.APDialogBlurBehindUtils
import java.util.Locale

@Destination
@Composable
fun SettingScreen() {
    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val kPatchReady = state != APApplication.State.UNKNOWN_STATE
    val aPatchReady = (state == APApplication.State.ANDROIDPATCH_INSTALLING ||
            state == APApplication.State.ANDROIDPATCH_INSTALLED ||
            state == APApplication.State.ANDROIDPATCH_NEED_UPDATE)
    //val bIsManagerHide = AppUtils.getPackageName() != APPLICATION_ID
    var isGlobalNamespaceEnabled by rememberSaveable {
        mutableStateOf(false)
    }
    var bSkipStoreSuperKey by rememberSaveable {
        mutableStateOf(APatchKeyHelper.shouldSkipStoreSuperKey())
    }
    if (kPatchReady && aPatchReady) {
        isGlobalNamespaceEnabled = isGlobalNamespaceEnabled()
    }
    Scaffold(
        topBar = {
            TopBar()
        }
    ) { paddingValues ->

        val loadingDialog = rememberLoadingDialog()
        val clearKeyDialog = rememberConfirmDialog(onConfirm = {
            APatchKeyHelper.clearConfigKey()
            APApplication.superKey = ""
        })

        val showLanguageDialog = rememberSaveable { mutableStateOf(false) }
        LanguageDialog(showLanguageDialog)

        /*val showRandomizePkgNameDialog = rememberSaveable { mutableStateOf(false) }
        if (showRandomizePkgNameDialog.value) {
            RandomizePkgNameDialog(showDialog = showRandomizePkgNameDialog)
        }*/

        val showResetSuPathDialog = remember { mutableStateOf(false) }
        if (showResetSuPathDialog.value) {
            ResetSUPathDialog(showResetSuPathDialog)
        }

        val showThemeChooseDialog = remember { mutableStateOf(false) }
        if (showThemeChooseDialog.value) {
            ThemeChooseDialog(showThemeChooseDialog)
        }

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {

            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val prefs = APApplication.sharedPreferences
            val activity = LocalContext.current as Activity

            // clear key
            if (kPatchReady) {
                val clearKeyDialogTitle = stringResource(id = R.string.clear_super_key)
                val clearKeyDialogContent =
                    stringResource(id = R.string.settings_clear_super_key_dialog)
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Filled.Key,
                            stringResource(id = R.string.super_key)
                        )
                    },
                    headlineContent = { Text(stringResource(id = R.string.clear_super_key)) },
                    modifier = Modifier.clickable {
                        clearKeyDialog.showConfirm(
                            title = clearKeyDialogTitle,
                            content = clearKeyDialogContent,
                            markdown = false,
                        )

                    }
                )
            }

            // store key local?
            SwitchItem(
                icon = Icons.Filled.Key,
                title = stringResource(id = R.string.settings_donot_store_superkey),
                summary = stringResource(id = R.string.settings_donot_store_superkey_summary),
                checked = bSkipStoreSuperKey,
                onCheckedChange = {
                    bSkipStoreSuperKey = it
                    APatchKeyHelper.setShouldSkipStoreSuperKey(bSkipStoreSuperKey)
                }
            )

            // Global mount
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

            // Webview Debug
            if (aPatchReady) {
                var enableWebDebugging by rememberSaveable {
                    mutableStateOf(
                        prefs.getBoolean("enable_web_debugging", false)
                    )
                }
                SwitchItem(
                    icon = Icons.Filled.DeveloperMode,
                    title = stringResource(id = R.string.enable_web_debugging),
                    summary = stringResource(id = R.string.enable_web_debugging_summary),
                    checked = enableWebDebugging
                ) {
                    APApplication.sharedPreferences.edit().putBoolean("enable_web_debugging", it)
                        .apply()
                    enableWebDebugging = it
                }
            }

            // Check Update
            var checkUpdate by rememberSaveable {
                mutableStateOf(
                    prefs.getBoolean("check_update", true)
                )
            }

            SwitchItem(
                icon = Icons.Filled.Update,
                title = stringResource(id = R.string.settings_check_update),
                summary = stringResource(id = R.string.settings_check_update_summary),
                checked = checkUpdate
            ) {
                prefs.edit().putBoolean("check_update", it).apply()
                checkUpdate = it
            }

            // Night Mode Follow System
            var nightFollowSystem by rememberSaveable {
                mutableStateOf(
                    prefs.getBoolean("night_mode_follow_sys", true)
                )
            }
            SwitchItem(
                icon = Icons.Filled.InvertColors,
                title = stringResource(id = R.string.settings_night_mode_follow_sys),
                summary = stringResource(id = R.string.settings_night_mode_follow_sys_summary),
                checked = nightFollowSystem
            ) {
                prefs.edit().putBoolean("night_mode_follow_sys", it).apply()
                nightFollowSystem = it

                activity.recreate()
            }

            // Custom Night Theme Switch
            if (!nightFollowSystem) {
                var nightThemeEnabled by rememberSaveable {
                    mutableStateOf(
                        prefs.getBoolean("night_mode_enabled", false)
                    )
                }
                SwitchItem(
                    icon = Icons.Filled.DarkMode,
                    title = stringResource(id = R.string.settings_night_theme_enabled),
                    checked = nightThemeEnabled
                ) {
                    prefs.edit().putBoolean("night_mode_enabled", it).apply()
                    nightThemeEnabled = it

                    activity.recreate()
                }
            }

            // System dynamic color theme
            val isDynamicColorSupport = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            if (isDynamicColorSupport) {
                var useSystemDynamicColor by rememberSaveable {
                    mutableStateOf(
                        prefs.getBoolean("use_system_color_theme", true)
                    )
                }
                SwitchItem(
                    icon = Icons.Filled.ColorLens,
                    title = stringResource(id = R.string.settings_use_system_color_theme),
                    summary = stringResource(id = R.string.settings_use_system_color_theme_summary),
                    checked = useSystemDynamicColor
                ) {
                    prefs.edit().putBoolean("use_system_color_theme", it).apply()
                    useSystemDynamicColor = it

                    activity.recreate()
                }

                if (!useSystemDynamicColor) {
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(id = R.string.settings_custom_color_theme))
                        },
                        modifier = Modifier.clickable {
                            showThemeChooseDialog.value = true
                        },
                        supportingContent = {
                            val colorMode = prefs.getString("custom_color", "blue")
                            Text(
                                text = colorNameToString(colorMode.toString()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.FormatColorFill, null) }
                    )

                }
            } else {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.settings_custom_color_theme))
                    },
                    modifier = Modifier.clickable {
                        showThemeChooseDialog.value = true
                    },
                    supportingContent = {
                        val colorMode = prefs.getString("custom_color", "blue")
                        Text(
                            text = colorNameToString(colorMode.toString()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.FormatColorFill, null) }
                )
            }

            /*
            // hide manager
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
                    headlineContent = {
                        Text(
                            stringResource(id = R.string.hide_apatch_manager),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    modifier = Modifier.clickable {
                        showRandomizePkgNameDialog.value = true
                    }
                )
            }*/

            // su path
            if (kPatchReady) {
                ListItem(
                    leadingContent = {
                        Icon(
                            Icons.Filled.Commit,
                            stringResource(id = R.string.setting_reset_su_path)
                        )
                    },
                    supportingContent = {
                    },
                    headlineContent = { Text(stringResource(id = R.string.setting_reset_su_path)) },
                    modifier = Modifier.clickable {
                        showResetSuPathDialog.value = true
                    }
                )
            }

            // language
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
                        } ?: stringResource(id = R.string.system_default),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                },
                leadingContent = { Icon(Icons.Filled.Translate, null) }
            )

            // log
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
                        val bugreport = loadingDialog.withLoading {
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeChooseDialog(showDialog: MutableState<Boolean>) {
    val prefs = APApplication.sharedPreferences
    val activity = LocalContext.current as Activity

    BasicAlertDialog(
        onDismissRequest = { showDialog.value = false },
        properties = DialogProperties(
            decorFitsSystemWindows = true,
            usePlatformDefaultWidth = false,
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
            LazyColumn {
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.amber_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "amber").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.blue_grey_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "blue_grey").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.blue_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "blue").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.brown_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "brown").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.cyan_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "cyan").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.deep_orange_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "deep_orange").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.deep_purple_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "deep_purple").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.green_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "green").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.indigo_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "indigo").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.light_blue_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "light_blue").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.light_green_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "light_green").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.lime_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "lime").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.orange_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "orange").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.pink_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "pink").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.purple_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "purple").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.red_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "red").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.sakura_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "sakura").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.teal_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "teal").apply()
                            activity.recreate()
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.yellow_theme)) },
                        modifier = Modifier.clickable {
                            showDialog.value = false
                            prefs.edit().putString("custom_color", "yellow").apply()
                            activity.recreate()
                        }
                    )
                }

            }

            val dialogWindowProvider = LocalView.current.parent as DialogWindowProvider
            APDialogBlurBehindUtils.setupWindowBlurListener(dialogWindowProvider.window)
        }
    }

}

@Composable
fun colorNameToString(colorName: String): String {
    return when (colorName) {
        "amber" -> stringResource(R.string.amber_theme)
        "blue_grey" -> stringResource(R.string.blue_grey_theme)
        "blue" -> stringResource(R.string.blue_theme)
        "brown" -> stringResource(R.string.brown_theme)
        "cyan" -> stringResource(R.string.cyan_theme)
        "deep_orange" -> stringResource(R.string.deep_orange_theme)
        "deep_purple" -> stringResource(R.string.deep_purple_theme)
        "green" -> stringResource(R.string.green_theme)
        "indigo" -> stringResource(R.string.indigo_theme)
        "light_blue" -> stringResource(R.string.light_blue_theme)
        "light_green" -> stringResource(R.string.light_green_theme)
        "lime" -> stringResource(R.string.lime_theme)
        "orange" -> stringResource(R.string.orange_theme)
        "pink" -> stringResource(R.string.pink_theme)
        "purple" -> stringResource(R.string.purple_theme)
        "red" -> stringResource(R.string.red_theme)
        "sakura" -> stringResource(R.string.sakura_theme)
        "teal" -> stringResource(R.string.teal_theme)
        "yellow" -> stringResource(R.string.yellow_theme)
        else -> stringResource(R.string.blue_theme)
    }
}

val suPathChecked: (path: String) -> Boolean = {
    it.startsWith("/") && it.trim().length > 1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetSUPathDialog(showDialog: MutableState<Boolean>) {
    val context = LocalContext.current
    var suPath by remember { mutableStateOf(Natives.suPath()) }
    BasicAlertDialog(
        onDismissRequest = { showDialog.value = false },
        properties = DialogProperties(
            decorFitsSystemWindows = true,
            usePlatformDefaultWidth = false,
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
                Box(
                    Modifier
                        .padding(PaddingValues(bottom = 16.dp))
                        .align(Alignment.Start)
                ) {
                    Text(
                        text = stringResource(id = R.string.setting_reset_su_path),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Box(
                    Modifier
                        .weight(weight = 1f, fill = false)
                        .padding(PaddingValues(bottom = 12.dp))
                        .align(Alignment.Start)
                )
                {
                    OutlinedTextField(
                        value = suPath,
                        onValueChange = {
                            suPath = it
                        },
                        label = { Text(stringResource(id = R.string.setting_reset_su_new_path)) },
                        visualTransformation = VisualTransformation.None,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showDialog.value = false }) {

                        Text(stringResource(id = android.R.string.cancel))
                    }

                    Button(
                        enabled = suPathChecked(suPath),
                        onClick = {
                            showDialog.value = false
                            val success = Natives.resetSuPath(suPath)
                            Toast.makeText(
                                context,
                                if (success) R.string.success else R.string.failure,
                                Toast.LENGTH_SHORT
                            ).show()
                            rootShellForResult("echo $suPath > ${APApplication.SU_PATH_FILE}")
                        }) {
                        Text(stringResource(id = android.R.string.ok))
                    }
                }
            }
            val dialogWindowProvider = LocalView.current.parent as DialogWindowProvider
            APDialogBlurBehindUtils.setupWindowBlurListener(dialogWindowProvider.window)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomizePkgNameDialog(showDialog: MutableState<Boolean>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var newPackageName by remember { mutableStateOf("") }
    var enable by remember { mutableStateOf(false) }
    BasicAlertDialog(
        onDismissRequest = { showDialog.value = false },
        properties = DialogProperties(
            decorFitsSystemWindows = true,
            usePlatformDefaultWidth = false,

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

                Box(
                    Modifier
                        .padding(PaddingValues(bottom = 16.dp))
                        .align(Alignment.Start)
                ) {
                    Text(
                        text = stringResource(id = R.string.hide_apatch_manager),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Box(
                    Modifier
                        .weight(weight = 1f, fill = false)
                        .padding(PaddingValues(bottom = 12.dp))
                        .align(Alignment.Start)
                ) {
                    Text(
                        text = stringResource(id = R.string.hide_apatch_dialog_summary),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Box(
                    Modifier
                        .weight(weight = 1f, fill = false)
                        .padding(PaddingValues(bottom = 12.dp))
                        .align(Alignment.Start)
                )
                {
                    OutlinedTextField(
                        value = newPackageName,
                        onValueChange = {
                            newPackageName = it
                            enable = newPackageName.isNotEmpty()
                        },
                        label = { Text(stringResource(id = R.string.hide_apatch_dialog_new_manager_name)) },
                        visualTransformation = VisualTransformation.None,
                    )
                }

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
                        scope.launch { HideAPK.hide(context, newPackageName) }
                    }) {
                        Text(stringResource(id = android.R.string.ok))
                    }
                }
            }
            val dialogWindowProvider = LocalView.current.parent as DialogWindowProvider
            APDialogBlurBehindUtils.setupWindowBlurListener(dialogWindowProvider.window)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDialog(showLanguageDialog: MutableState<Boolean>) {

    val languages = stringArrayResource(id = R.array.languages)
    val languagesValues = stringArrayResource(id = R.array.languages_values)

    if (showLanguageDialog.value) {
        BasicAlertDialog(
            onDismissRequest = { showLanguageDialog.value = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .width(150.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = AlertDialogDefaults.TonalElevation,
                color = AlertDialogDefaults.containerColor,
            ) {
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
            }
            val dialogWindowProvider = LocalView.current.parent as DialogWindowProvider
            APDialogBlurBehindUtils.setupWindowBlurListener(dialogWindowProvider.window)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar() {
    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
    )
}
