package me.bmax.apatch.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.ConfirmResult
import me.bmax.apatch.ui.component.ModuleDownloadProgressDialog
import me.bmax.apatch.ui.component.SearchAppBar
import me.bmax.apatch.ui.component.pinnedScrollBehavior
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.viewmodel.OnlineCategory
import me.bmax.apatch.ui.viewmodel.OnlineModule
import me.bmax.apatch.ui.viewmodel.OnlineModuleViewModel
import me.bmax.apatch.util.DownloadCancelHandle
import me.bmax.apatch.util.ModuleRepo
import me.bmax.apatch.util.reboot
import me.bmax.apatch.util.ui.LocalSnackbarHost

/** A download in flight, shown as a modal progress dialog. */
private data class DownloadTask(val title: String, val progress: Int)

/** A star reads as a star in any theme; no dynamic colour improves on amber. */
private val StarColor = Color(0xFFFFB300)

/**
 * The community module repository: every module registered at repo.apatch.dev,
 * with a button that downloads and installs it. System modules go through the
 * normal install flow; KPMs are written into the KPM store and take effect
 * after a reboot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun OnlineModuleScreen(navigator: DestinationsNavigator, kpmOnly: Boolean) {
    val viewModel = viewModel<OnlineModuleViewModel>()
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val confirmDialog = rememberConfirmDialog()

    val installText = stringResource(R.string.online_install)
    val updateText = stringResource(R.string.online_update)
    val cancelText = stringResource(android.R.string.cancel)
    val rebootText = stringResource(R.string.reboot)
    val downloadFailedText = stringResource(R.string.online_download_failed)
    val downloadCancelledText = stringResource(R.string.online_download_cancelled)
    val installFailedText = stringResource(R.string.online_install_failed)
    val updateSuccessText = stringResource(R.string.kpm_update_succ)
    val updateConfirmText = stringResource(R.string.online_update_confirm)
    val installConfirmText = stringResource(R.string.online_install_confirm)
    val kpmUnavailableText = stringResource(R.string.kpm_kp_not_installed)

    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val kpatchReady = state != APApplication.State.UNKNOWN_STATE

    LaunchedEffect(Unit) { viewModel.onEnter(kpmOnly) }

    var downloadTask by remember { mutableStateOf<DownloadTask?>(null) }
    var downloadHandle by remember { mutableStateOf<DownloadCancelHandle?>(null) }

    suspend fun install(module: OnlineModule) {
        if (!module.canInstall) return
        // Nothing is written before the user has seen what is about to happen,
        // whether that is a first install or a replacement of an older version.
        val confirmResult = confirmDialog.awaitConfirm(
            title = if (module.installed) updateText else installText,
            content = if (module.installed) {
                updateConfirmText.format(module.title, module.installedVersion, module.version)
            } else {
                installConfirmText.format(
                    listOf(module.title, module.version).filter { it.isNotBlank() }.joinToString(" ")
                )
            },
            confirm = if (module.installed) updateText else installText,
            dismiss = cancelText,
        )
        if (confirmResult != ConfirmResult.Confirmed) return

        val fileName = "${module.id}-${module.version}.${if (module.kpm) "kpm" else "zip"}"
        val handle = DownloadCancelHandle()
        downloadHandle = handle
        downloadTask = DownloadTask(module.title, 0)
        val file = try {
            ModuleRepo.downloadModuleFile(
                id = module.id,
                kpm = module.kpm,
                fileName = fileName,
                authorUrl = module.downloadUrl,
                cancel = handle,
            ) { percent -> downloadTask = DownloadTask(module.title, percent) }
        } finally {
            downloadTask = null
            downloadHandle = null
        }
        if (handle.isCancelled) {
            Toast.makeText(context, downloadCancelledText, Toast.LENGTH_SHORT).show()
            return
        }
        if (file == null) {
            Toast.makeText(context, downloadFailedText, Toast.LENGTH_SHORT).show()
            return
        }

        if (module.kpm) {
            // The installer replaces the stored file of an already installed
            // KPM, so an update is the same operation as a fresh install.
            val rc = kpmInstallMutex.withLock { installKpm(Uri.fromFile(file)) }
            file.delete()
            if (rc == 0) {
                viewModel.refresh()
                val result = snackBarHost.showSnackbar(
                    message = updateSuccessText.format(module.version),
                    actionLabel = rebootText,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    withContext(Dispatchers.IO) { reboot() }
                }
            } else {
                Toast.makeText(context, installFailedText.format(rc), Toast.LENGTH_SHORT).show()
            }
        } else {
            // The install screen runs the apd install, shows the log and offers
            // the reboot, so nothing is installed here.
            navigator.navigate(InstallScreenDestination(Uri.fromFile(file), MODULE_TYPE.APM))
            viewModel.refresh()
        }
    }

    Scaffold(topBar = {
        Column {
            SearchAppBar(
                searchText = viewModel.search,
                onSearchTextChange = { viewModel.onSearchChange(it) },
                onBackClick = { navigator.popBackStack() },
                searchBarPlaceHolderText = stringResource(R.string.search_modules)
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OnlineCategory.entries.forEach { category ->
                    FilterChip(
                        selected = viewModel.category == category,
                        onClick = { viewModel.onCategoryChange(category) },
                        label = { Text(stringResource(category.labelRes)) },
                    )
                }
            }
        }
    }, snackbarHost = { SnackbarHost(snackBarHost) }) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            onRefresh = { viewModel.refresh() },
            isRefreshing = viewModel.isRefreshing,
        ) {
            OnlineModuleList(
                modules = viewModel.modules,
                hasMore = viewModel.hasMore,
                isLoadingMore = viewModel.isLoadingMore,
                errorMessage = viewModel.errorMessage,
                kpatchReady = kpatchReady,
                kpmUnavailableText = kpmUnavailableText,
                installLabel = installText,
                updateLabel = updateText,
                listState = listState,
                scrollBehavior = scrollBehavior,
                onInstall = { module -> scope.launch { install(module) } },
                onLoadMore = { viewModel.loadMore() },
            )
        }
    }

    downloadTask?.let { task ->
        ModuleDownloadProgressDialog(
            title = task.title,
            progress = task.progress,
            onCancel = { downloadHandle?.cancel() },
        )
    }
}

private val OnlineCategory.labelRes: Int
    get() = when (this) {
        OnlineCategory.ALL -> R.string.online_filter_all
        OnlineCategory.SYSTEM -> R.string.online_filter_system
        OnlineCategory.KPM -> R.string.online_filter_kpm
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineModuleList(
    modules: List<OnlineModule>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    errorMessage: String,
    kpatchReady: Boolean,
    kpmUnavailableText: String,
    installLabel: String,
    updateLabel: String,
    listState: LazyListState,
    scrollBehavior: SearchBarScrollBehavior,
    onInstall: (OnlineModule) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 11.dp, end = 16.dp, bottom = 24.dp),
    ) {
        when {
            modules.isEmpty() -> item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (errorMessage.isNotBlank()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                stringResource(R.string.online_load_failed),
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Text(stringResource(R.string.online_empty), textAlign = TextAlign.Center)
                    }
                }
            }

            else -> items(modules, key = { "${it.kpm}:${it.id}" }) { module ->
                OnlineModuleItem(
                    module = module,
                    installLabel = installLabel,
                    updateLabel = updateLabel,
                    enabled = !module.kpm || kpatchReady,
                    disabledHint = kpmUnavailableText,
                    onInstall = { onInstall(module) },
                )
                Spacer(Modifier.height(1.dp))
            }
        }

        if (hasMore) {
            item {
                LaunchedEffect(modules.size, hasMore) { onLoadMore() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoadingMore) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineModuleItem(
    module: OnlineModule,
    installLabel: String,
    updateLabel: String,
    enabled: Boolean,
    disabledHint: String,
    onInstall: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(all = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = module.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (module.kpm) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiary,
                            ) {
                                Text(
                                    text = stringResource(R.string.online_filter_kpm),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    val meta = listOfNotNull(
                        module.version.takeIf { it.isNotBlank() },
                        module.author.takeIf { it.isNotBlank() },
                    ).joinToString(", ")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        if (module.stars > 0) {
                            val stars = ModuleRepo.formatStars(module.stars)
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = stringResource(R.string.online_stars, stars),
                                modifier = Modifier.size(13.dp),
                                tint = StarColor,
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = stars,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    when {
                        module.installed && module.updateAvailable -> Text(
                            text = stringResource(R.string.online_update_available, module.version),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        module.installed -> Text(
                            text = if (module.installedVersion.isBlank()) {
                                stringResource(R.string.online_installed)
                            } else {
                                stringResource(R.string.online_installed_version, module.installedVersion)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (module.kpm && !enabled) {
                        Text(
                            text = disabledHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (module.summary.isNotBlank()) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = module.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(
                thickness = 1.5.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(top = 8.dp),
            )

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))

                if (module.canInstall && !(module.installed && !module.updateAvailable)) {
                    FilledTonalButton(
                        onClick = onInstall,
                        enabled = enabled,
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            painter = painterResource(
                                if (module.updateAvailable) R.drawable.device_mobile_down
                                else R.drawable.package_import
                            ),
                            contentDescription = if (module.updateAvailable) updateLabel else installLabel,
                        )
                    }
                }
            }
        }
    }
}
