package me.bmax.apatch.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.utils.app.AppUtils.getPackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.ConfirmDialog
import me.bmax.apatch.ui.component.SearchAppBar
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import me.bmax.apatch.util.PkgConfig

private val pm = getPackageManager()

@OptIn(ExperimentalMaterialApi::class)
@Destination
@Composable
fun SuperUserScreen(navigator: DestinationsNavigator) {
    val viewModel = viewModel<SuperUserViewModel>()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (viewModel.appList.isEmpty()) {
            viewModel.fetchAppList()
        }
    }

    Scaffold(
        topBar = {
            SearchAppBar(
                title = { Text(stringResource(R.string.su_title)) },
                searchText = viewModel.search,
                onSearchTextChange = { viewModel.search = it },
                onClearClick = { viewModel.search = "" },
                dropdownContent = {
                    var showDropdown by remember { mutableStateOf(false) }

                    IconButton(
                        onClick = { showDropdown = true },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(id = R.string.settings)
                        )

                        DropdownMenu(expanded = showDropdown, onDismissRequest = {
                            showDropdown = false
                        }) {
                            DropdownMenuItem(text = {
                                Text(stringResource(R.string.su_refresh))
                            }, onClick = {
                                scope.launch {
                                    viewModel.fetchAppList()
                                }
                                showDropdown = false
                            })

                            DropdownMenuItem(text = {
                                Text(
                                    if (viewModel.showSystemApps) {
                                        stringResource(R.string.su_hide_system_apps)
                                    } else {
                                        stringResource(R.string.su_show_system_apps)
                                    }
                                )
                            }, onClick = {
                                viewModel.showSystemApps = !viewModel.showSystemApps
                                showDropdown = false
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
        }
    ) { innerPadding ->
        ConfirmDialog()
        val refreshState = rememberPullRefreshState(
            refreshing = viewModel.isRefreshing,
            onRefresh = { scope.launch { viewModel.fetchAppList() } },
        )

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .pullRefresh(refreshState)
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(viewModel.appList, key = { it.packageName + it.uid }) { app ->
                    AppItem(app)
                }
            }
            PullRefreshIndicator(
                refreshing = viewModel.isRefreshing,
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppItem(
    app: SuperUserViewModel.AppInfo,
) {
    val config = app.config
    var edit by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(config.allow != 0) }

    ListItem(
        modifier = Modifier.clickable(onClick = {
            edit = !edit
        }),
        headlineContent = { Text(app.label) },
        leadingContent = {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(app.applicationInfo.loadIcon(pm))
                    .crossfade(true)
                    .build(),
                contentDescription = app.label,
                modifier = Modifier
                    .padding(4.dp)
                    .width(48.dp)
                    .height(48.dp)
            )
        },
        supportingContent = {
            Column {
                Text(app.packageName)
                FlowRow {
//                    if (config.exclude != 0) {
//                        LabelText(label = stringResource(id = R.string.su_pkg_excluded_label))
//                    }
                    if (config.allow != 0) {
                        LabelText(label = config.profile.uid.toString())
                        LabelText(label = config.profile.toUid.toString())
                        LabelText(label = when {
                            // todo: valid scontext ?
                            config.profile.scontext.isNotEmpty() -> config.profile.scontext
                            else -> stringResource(id = R.string.su_selinux_via_hook)
                        })
                    }
                }
            }
        },
        trailingContent = {
            Switch(checked = checked
                , onCheckedChange = {
                    checked = !checked
                    config.allow = if (checked) 1 else 0
                    if (checked) {
                        Natives.grantSu(app.uid, 0, config.profile.scontext)
                    } else {
                        Natives.revokeSu(app.uid)
                    }
                    runBlocking {
                        launch(Dispatchers.IO) {
                            PkgConfig.changeConfig(config)
                        }
                    }
                })
        },
    )
    if (edit) {
        EditUser(app)
    }
}

@Composable
fun EditUser(app: SuperUserViewModel.AppInfo) {
//    var _viahook = app.config.profile?.scontext.isNullOrEmpty()
//    var viahook by remember { mutableStateOf(_viahook) }
//    var exclude by remember { mutableStateOf(app.config.exclude) }

    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp)) {
//        SwitchItem(
//            icon = Icons.Filled.Security,
//            title = "SU Thread",
//            summary = "bypass selinux via hooks",
//            checked = viahook,
//            onCheckedChange = {
//                viahook = !viahook
//                if (viahook) app.config.profile.scontext = ""
//                else app.config.profile.scontext = APApplication.MAGISK_SCONTEXT
//                runBlocking {
//                    launch(Dispatchers.IO) {
//                        PkgConfig.changeConfig(app.config)
//                    }
//                }
//            },
//        )
//        SwitchItem(
//            icon = Icons.Filled.Security,
//            title = stringResource(id = R.string.su_pkg_excluded_setting_title),
//            summary = stringResource(id = R.string.su_pkg_excluded_setting_summary),
//            checked = exclude != 0,
//            onCheckedChange = {
//                exclude = if (it) 1 else 0
//                runBlocking {
//                    launch(Dispatchers.IO) {
//                        app.config.exclude = exclude
//                        PkgConfig.changeConfig(app.config)
//                    }
//                }
//            },
//        )
    }
}


@Composable
fun LabelText(label: String) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp, end = 4.dp)
            .background(
                Color.Black,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 2.dp, horizontal = 5.dp),
            style = TextStyle(
                fontSize = 8.sp,
                color = Color.White,
            )
        )
    }
}