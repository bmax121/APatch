package me.bmax.apatch.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.launch
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.DropdownItem
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import me.bmax.apatch.util.PkgConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopup
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Destination<RootGraph>
@Composable
fun SuperUserScreen() {
    val viewModel = viewModel<SuperUserViewModel>()
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    var expanded by remember { mutableStateOf(false) }
    val appListItemsCount = 2

    LaunchedEffect(Unit) {
        if (viewModel.appList.isEmpty()) {
            viewModel.fetchAppList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.su_title),
                actions = {
                    val showDropdown = remember { mutableStateOf(false) }

                    IconButton(onClick = { showDropdown.value = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(id = R.string.settings)
                        )

                        ListPopup(
                            show = showDropdown,
                            onDismissRequest = { showDropdown.value = false }
                        ) {
                            ListPopupColumn {
                                DropdownItem(
                                    text = stringResource(R.string.su_refresh),
                                    optionSize = appListItemsCount,
                                    index = 0,
                                    onSelectedIndexChange = {
                                        scope.launch { viewModel.fetchAppList() }
                                        showDropdown.value = false
                                    }
                                )

                                DropdownItem(
                                    text = if (viewModel.showSystemApps) {
                                        stringResource(R.string.su_hide_system_apps)
                                    } else {
                                        stringResource(R.string.su_show_system_apps)
                                    },
                                    optionSize = appListItemsCount,
                                    index = 1,
                                    onSelectedIndexChange = {
                                        viewModel.showSystemApps = !viewModel.showSystemApps
                                        showDropdown.value = false
                                    }
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PullToRefresh(
                onRefresh = { scope.launch { viewModel.fetchAppList() } },
                isRefreshing = viewModel.isRefreshing
            ) {
                SearchBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    inputField = {
                        InputField(
                            query = viewModel.search,
                            onQueryChange = { viewModel.search = it },
                            onSearch = {
                                expanded = false
                            },
                            expanded = expanded,
                            onExpandedChange = {
                                expanded = it
                                if (!it) viewModel.search = ""
                            }
                        )
                    },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    content = {}
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = 16.dp)
                ) {
                    items(viewModel.appList, key = { it.packageName + it.uid }) { app ->
                        AppItem(app)
                    }
                }
            }
        }
    }
}


@Composable
private fun AppItem(app: SuperUserViewModel.AppInfo) {
    val config = app.config
    var showEditProfile by remember { mutableStateOf(false) }
    var rootGranted by remember { mutableStateOf(config.allow != 0) }
    var excludeApp by remember { mutableIntStateOf(config.exclude) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!rootGranted) {
                        showEditProfile = !showEditProfile
                    } else {
                        rootGranted = false
                        config.allow = 0
                        Natives.revokeSu(app.uid)
                        PkgConfig.changeConfig(config)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(app.packageInfo)
                        .crossfade(true)
                        .build(),
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(end = 12.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, fontWeight = FontWeight.Bold)
                    Text(app.packageName, style = MiuixTheme.textStyles.body2)
                    FlowRow {
                        if (excludeApp == 1) {
                            LabelText(label = stringResource(R.string.su_pkg_excluded_label))
                        }
                        if (rootGranted) {
                            LabelText(label = config.profile.uid.toString())
                            LabelText(label = config.profile.toUid.toString())
                            LabelText(
                                label = config.profile.scontext.ifEmpty { stringResource(R.string.su_selinux_via_hook) }
                            )
                        }
                    }
                }

                Switch(
                    checked = rootGranted,
                    onCheckedChange = {
                        rootGranted = !rootGranted
                        if (rootGranted) {
                            excludeApp = 0
                            config.allow = 1
                            config.exclude = 0
                            config.profile.scontext = APApplication.MAGISK_SCONTEXT
                        } else {
                            config.allow = 0
                        }
                        config.profile.uid = app.uid
                        PkgConfig.changeConfig(config)
                        if (config.allow == 1) {
                            Natives.grantSu(app.uid, 0, config.profile.scontext)
                            Natives.setUidExclude(app.uid, 0)
                        } else {
                            Natives.revokeSu(app.uid)
                        }
                    }
                )
            }

            AnimatedVisibility(
                visible = showEditProfile && !rootGranted,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                SuperSwitch(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.su_pkg_excluded_setting_title),
                    summary = stringResource(R.string.su_pkg_excluded_setting_summary),
                    checked = excludeApp == 1,
                    onCheckedChange = {
                        if (it) {
                            excludeApp = 1
                            config.allow = 0
                            config.profile.scontext = APApplication.DEFAULT_SCONTEXT
                            Natives.revokeSu(app.uid)
                        } else {
                            excludeApp = 0
                        }
                        config.exclude = excludeApp
                        config.profile.uid = app.uid
                        PkgConfig.changeConfig(config)
                        Natives.setUidExclude(app.uid, excludeApp)
                    }
                )
            }
        }
    }
}

@Composable
fun LabelText(label: String) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp, end = 4.dp)
            .background(
                Color.Black, shape = RoundedCornerShape(4.dp)
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