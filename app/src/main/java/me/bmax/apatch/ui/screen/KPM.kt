package me.bmax.apatch.ui.screen

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.util.DownloadListener
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.ConfirmDialog
import me.bmax.apatch.ui.component.ConfirmResult
import me.bmax.apatch.ui.component.LoadingDialog
import me.bmax.apatch.util.LocalDialogHost
import me.bmax.apatch.util.LocalSnackbarHost
import me.bmax.apatch.util.reboot
import me.bmax.apatch.util.toggleModule
import me.bmax.apatch.ui.screen.destinations.InstallScreenDestination
import me.bmax.apatch.ui.viewmodel.KPModuleViewModel


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
            val moduleInstall = stringResource(id = R.string.kpm_load)
            val selectKpmLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                if (it.resultCode != RESULT_OK) {
                    return@rememberLauncherForActivityResult
                }
                val data = it.data ?: return@rememberLauncherForActivityResult
                val uri = data.data ?: return@rememberLauncherForActivityResult

                Log.i(TAG, "select kpm result: ${it.data}")

                viewModel.loadModule(uri)

                viewModel.markNeedRefresh()

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

        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Text(
                    text = "Please use the kpatch CLI now, this page is under development.",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

//        ModuleList(
//            viewModel = viewModel, modifier = Modifier
//                .padding(innerPadding)
//                .fillMaxSize()
//        ) {
//            navigator.navigate(InstallScreenDestination(it))
//        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ModuleList(
    viewModel: KPModuleViewModel, modifier: Modifier = Modifier, onInstallModule: (Uri) -> Unit
) {
    val failedEnable = stringResource(R.string.module_failed_to_enable)
    val failedDisable = stringResource(R.string.module_failed_to_disable)
    val failedUninstall = stringResource(R.string.module_uninstall_failed)
    val successUninstall = stringResource(R.string.module_uninstall_success)
    val reboot = stringResource(id = R.string.reboot)
    val rebootToApply = stringResource(id = R.string.reboot_to_apply)
    val moduleStr = stringResource(id = R.string.kpm)
    val uninstall = stringResource(id = R.string.uninstall)
    val cancel = stringResource(id = android.R.string.cancel)
    val moduleUninstallConfirm = stringResource(id = R.string.module_uninstall_confirm)

    val dialogHost = LocalDialogHost.current
    val snackBarHost = LocalSnackbarHost.current
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
        val message = if (success) {
            successUninstall.format(module.name)
        } else {
            failedUninstall.format(module.name)
        }
        val actionLabel = if (success) {
            reboot
        } else {
            null
        }
        val result = snackBarHost.showSnackbar(message, actionLabel = actionLabel)
        if (result == SnackbarResult.ActionPerformed) {
            reboot()
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
                        var isChecked by rememberSaveable(module) { mutableStateOf(true) }
                        val scope = rememberCoroutineScope()

                        KPModuleItem(module, isChecked, onUninstall = {
                            scope.launch { onModuleUninstall(module) }
                        }, onCheckChanged = {
                            scope.launch {
                                val success = dialogHost.withLoading {
                                    withContext(Dispatchers.IO) {
                                        toggleModule(module.name, !isChecked)
                                    }
                                }
                                if (success) {
                                    isChecked = it
                                    viewModel.fetchModuleList()

                                    val result = snackBarHost.showSnackbar(
                                        rebootToApply, actionLabel = reboot
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        reboot()
                                    }
                                } else {
                                    val message = if (isChecked) failedDisable else failedEnable
                                    snackBarHost.showSnackbar(message.format(module.name))
                                }
                            }
                        }, onUpdate = {
                            scope.launch {
                            }
                        })

                        // fix last item shadow incomplete in LazyColumn
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }

        DownloadListener(context, onInstallModule)

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
    isChecked: Boolean,
    onUninstall: (Natives.KPMInfo) -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onUpdate: (Natives.KPMInfo) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {

        val textDecoration = TextDecoration.LineThrough

        Column(modifier = Modifier.padding(24.dp, 16.dp, 24.dp, 0.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val moduleVersion = stringResource(id = R.string.module_version)
                val moduleAuthor = stringResource(id = R.string.module_author)

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
                        text = "$moduleAuthor: ${module.author}",
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

            Spacer(modifier = Modifier.height(16.dp))

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