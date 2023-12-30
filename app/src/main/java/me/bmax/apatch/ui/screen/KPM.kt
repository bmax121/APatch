package me.bmax.apatch.ui.screen

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.util.DownloadListener
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.ConfirmDialog
import me.bmax.apatch.ui.component.ConfirmResult
import me.bmax.apatch.ui.component.DialogHostState
import me.bmax.apatch.ui.component.LoadingDialog
import me.bmax.apatch.util.LocalDialogHost
import me.bmax.apatch.util.LocalSnackbarHost
import me.bmax.apatch.util.reboot
import me.bmax.apatch.util.toggleModule
import me.bmax.apatch.ui.screen.destinations.InstallScreenDestination
import me.bmax.apatch.ui.viewmodel.KPModuleViewModel
import me.bmax.apatch.util.uninstallModule
import okhttp3.OkHttpClient
import java.io.IOException


private val TAG = "KernelPatchModule"
@Destination
@Composable
fun KPModuleScreen(navigator: DestinationsNavigator) {
    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    if(state == APApplication.State.UNKNOWN_STATE) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Text(
                    text = "Kernel Patch Not Installed",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        return
    }

    val viewModel = viewModel<KPModuleViewModel>()

    LaunchedEffect(Unit) {
        if (viewModel.moduleList.isEmpty() || viewModel.isNeedRefresh) {
            viewModel.fetchModuleList()
        }
    }

    Scaffold(topBar = {
        TopBar()
    }, floatingActionButton = run {
        {
            val dialogHost = LocalDialogHost.current
            val scope = rememberCoroutineScope()
            val context = LocalContext.current

            val moduleInstall = stringResource(id = R.string.kpm_load)
            val selectKpmLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                if (it.resultCode != RESULT_OK) {
                    return@rememberLauncherForActivityResult
                }
                val data = it.data ?: return@rememberLauncherForActivityResult
                val uri = data.data ?: return@rememberLauncherForActivityResult

                // todo: args
                scope.launch {
                    val succ = loadModule(dialogHost, uri, "") == 0
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "install ${if (succ) "succeed" else "failed"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    if(succ) {
                        viewModel.markNeedRefresh()
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "*/*"
                    selectKpmLauncher.launch(intent)
                },
                icon = { Icon(Icons.Filled.Add, moduleInstall) },
                text = { Text(text = moduleInstall) },
            )
        }
    }) { innerPadding ->
        ConfirmDialog()
        LoadingDialog()

        KPModuleList(
            viewModel = viewModel, modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        )
    }
}

suspend fun loadModule(dialogHost: DialogHostState, uri: Uri, args: String): Int {
    val rc = dialogHost.withLoading {
        withContext(Dispatchers.IO) {run {
            var kpmDir: ExtendedFile = FileSystemManager.getLocal().getFile(apApp.filesDir.parent, "kpm")
            kpmDir.deleteRecursively()
            kpmDir.mkdirs()
            val rand = (1..4).map { ('a'..'z').random() }.joinToString("")
            val kpm = kpmDir.getChildFile("${rand}.kpm")
            Log.d(TAG, "save tmp kpm: ${kpm.path}")
            var rc = -1
            try {
                uri.inputStream().buffered().writeTo(kpm)
                rc = Natives.loadKernelPatchModule(kpm.path, args).toInt()
            } catch (e: IOException) {
                Log.e(TAG, "Copy kpm error: " + e)
            }
            Log.d(TAG, "load ${kpm.path} rc: ${rc}")
            rc
        }
        }
    }
    return rc
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun KPModuleList(
    viewModel: KPModuleViewModel, modifier: Modifier = Modifier
) {
    val moduleStr = stringResource(id = R.string.kpm)
    val moduleUninstallConfirm = stringResource(id = R.string.module_uninstall_confirm)
    val uninstall = stringResource(id = R.string.uninstall)
    val cancel = stringResource(id = android.R.string.cancel)

    val dialogHost = LocalDialogHost.current
    val context = LocalContext.current

    suspend fun onModuleUninstall(module: Natives.KPMInfo) {
        val confirmResult = dialogHost.showConfirm(
            moduleStr,
            content = moduleUninstallConfirm.format(module.name),
            confirm = uninstall,
            dismiss = cancel
        )
        if (confirmResult != ConfirmResult.Confirmed) {
            return
        }

        val success = dialogHost.withLoading {
            withContext(Dispatchers.IO) {
                Natives.unloadKernelPatchModule(module.name) == 0L
            }
        }

        if (success) {
            viewModel.fetchModuleList()
        }
    }

    val refreshState = rememberPullRefreshState(refreshing = viewModel.isRefreshing,
        onRefresh = { viewModel.fetchModuleList() })
    Box(modifier.pullRefresh(refreshState)) {
        val context = LocalContext.current

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = remember {
                PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + 16.dp + 56.dp /*  Scaffold Fab Spacing + Fab container height */
                )
            },
        ) {
            when {
                viewModel.moduleList.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.module_empty),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    items(viewModel.moduleList) { module ->
                        val scope = rememberCoroutineScope()
                        KPModuleItem(module, onUninstall = {
                            scope.launch { onModuleUninstall(module) }
                        }, )

                        // fix last item shadow incomplete in LazyColumn
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = viewModel.isRefreshing, state = refreshState, modifier = Modifier.align(
                Alignment.TopCenter
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar() {
    TopAppBar(title = { Text(stringResource(R.string.kpm)) })
}

@Composable
private fun KPModuleItem(
    module: Natives.KPMInfo,
    onUninstall: (Natives.KPMInfo) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        val textDecoration = TextDecoration.None

        Column(modifier = Modifier.padding(24.dp, 16.dp, 24.dp, 0.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val moduleVersion = stringResource(id = R.string.module_version)
                val moduleLicense = stringResource(id = R.string.module_license)
                val moduleAuthor = stringResource(id = R.string.module_author)
                val moduleDesc = stringResource(id = R.string.module_desc)
                val moduleArgs = stringResource(id = R.string.module_args)

                Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                    Text(
                        text = module.name,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                        textDecoration = textDecoration,
                    )

                    Text(
                        text = "$moduleVersion: ${module.version}",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                        textDecoration = textDecoration
                    )

                    Text(
                        text = "$moduleLicense: ${module.license}",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                        textDecoration = textDecoration
                    )

                    Text(
                        text = "$moduleAuthor: ${module.author}",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                        textDecoration = textDecoration
                    )

                    Text(
                        text = "$moduleArgs: ${module.args}",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                        textDecoration = textDecoration
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = module.description,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                fontWeight = MaterialTheme.typography.bodySmall.fontWeight,
                overflow = TextOverflow.Ellipsis,
                maxLines = 4,
                textDecoration = textDecoration
            )

            Spacer(modifier = Modifier.height(8.dp))

            Divider(thickness = Dp.Hairline)

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f, true))

                TextButton(
                    enabled = true,
                    onClick = { onUninstall(module) },
                ) {
                    Text(
                        fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        text = stringResource(R.string.uninstall),
                    )
                }
            }
        }
    }
}