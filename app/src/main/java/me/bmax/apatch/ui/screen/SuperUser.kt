package me.bmax.apatch.ui.screen

import android.util.Log
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.TAG
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.ConfirmDialog
import me.bmax.apatch.ui.component.SearchAppBar
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel


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
                title = { Text(stringResource(R.string.superuser)) },
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
                                Text(stringResource(R.string.refresh))
                            }, onClick = {
                                scope.launch {
                                    viewModel.fetchAppList()
                                }
                                showDropdown = false
                            })

                            DropdownMenuItem(text = {
                                Text(
                                    if (viewModel.showSystemApps) {
                                        stringResource(R.string.hide_system_apps)
                                    } else {
                                        stringResource(R.string.show_system_apps)
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
                    var edit by remember { mutableStateOf(false) }
                    AppItem(app, edit) {
                        edit = !edit
                    }
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppItem(
    app: SuperUserViewModel.AppInfo,
    edit: Boolean,
    onClickListener: () -> Unit,
) {
    var checked by remember {
        mutableStateOf(app.profile != null)
    }
    var profile = app.profile
    ListItem(
        modifier = Modifier.clickable(onClick = onClickListener),
        headlineContent = { Text(app.label) },
        leadingContent = {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(app.packageInfo)
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
                    if (profile != null) {
                        Row {
                            LabelText(label = profile.uid.toString())
                            LabelText(label = profile.toUid.toString())
                            LabelText(label = when {
                                profile.scontext.isNotEmpty() -> profile.scontext
                                else -> "bypass via hook"
                            })
                        }
                    }
                }
            }
        },
        trailingContent = {
            Row {
                Switch(checked = checked
                    , onCheckedChange = {
                        checked = !checked
                        if(checked) {
                            Natives.grantSu(app.uid, 0, APApplication.MAGISK_SCONTEXT)
                        } else {
                            Natives.revokeSu(app.uid)
                        }
                    })
            }
        },
    )
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