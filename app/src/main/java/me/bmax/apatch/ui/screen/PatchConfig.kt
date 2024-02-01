package me.bmax.apatch.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.utils.app.permission.PermissionUtils
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.ConfirmDialog
import me.bmax.apatch.ui.component.ConfirmResult
import me.bmax.apatch.ui.component.LoadingDialog
import me.bmax.apatch.ui.screen.destinations.PatchScreenDestination
import me.bmax.apatch.ui.viewmodel.KPModel
import me.bmax.apatch.ui.viewmodel.KPModuleViewModel
import me.bmax.apatch.ui.viewmodel.PatchViewModel
import me.bmax.apatch.util.LocalDialogHost
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.getSELinuxStatus
import java.net.URI

private val TAG = "PatchSetting"

@OptIn(ExperimentalMaterialApi::class)
@Destination
@Composable
fun PatchSetting(navigator: DestinationsNavigator, mode: PatchViewModel.PatchMode) {
    var permissionRequest = remember { mutableStateOf(false)  }
    val scrollState = rememberScrollState()

    val viewModel = viewModel<PatchViewModel>()
    SideEffect {
        viewModel.prepareAndParseKpimg()
    }

    Scaffold(topBar = {
        TopBar()
    }, floatingActionButton = {

    }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val current = LocalContext.current

            // request premission
            PermissionUtils.permission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ).callback(object : PermissionUtils.PermissionCallback {
                override fun onGranted() {
                    permissionRequest.value = false
                }
                override fun onDenied(
                    grantedList: MutableList<String>?,
                    deniedList: MutableList<String>?,
                    notFoundList: MutableList<String>?
                ) {
                    permissionRequest.value = false
                }
            }).request(current as Activity)

            PatchMode(mode)
            ErrorView(viewModel.error)
            KernelPatchImageView(viewModel.imgPatchInfo.kpimgInfo)
            KernelImageView(viewModel.imgPatchInfo.kimgInfo)

            // select boot.img
            if(viewModel.imgPatchInfo.kimgInfo.banner.isEmpty()) {
                SelectFileButton(
                    text = stringResource(id = R.string.patch_select_bootimg_btn),
                    onSelected = { data, uri ->
                        Log.d(TAG, "select boot.img, data: ${data}, uri: ${uri}")
                        viewModel.copyAndParseBootimg(uri)
                    }
                )
            }

            // set superkey
            if(viewModel.imgPatchInfo.kimgInfo.banner.isNotEmpty()) {
                SetSuperKeyView(viewModel)
            }

            // add new KPM
            if(viewModel.superkey.isNotEmpty() && !viewModel.patching && !viewModel.patchdone) {
                SelectFileButton(
                    text = stringResource(id = R.string.patch_embed_kpm_btn),
                    onSelected = {data, uri ->
                        Log.d(TAG, "select kpm, data: ${data}, uri: ${uri}")
                        viewModel.embedKPM(uri)
                    }
                )
            }

            AddedKPMView(viewModel.imgPatchInfo.addedExtras)

            // start patch
            if(viewModel.superkey.isNotEmpty() && !viewModel.patching && !viewModel.patchdone) {
                StartPatch(viewModel, mode)
            }

            // patch log
            if(viewModel.patching || viewModel.patchdone) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = viewModel.patchLog,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                )
                LaunchedEffect(viewModel.patchLog) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }

            // loading progress
            if(viewModel.running) {
                Box(modifier = Modifier
                    .padding(innerPadding)
                    .align(Alignment.CenterHorizontally)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(50.dp)
                            .padding(16.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}


@Composable
private fun StartPatch(viewModel: PatchViewModel, mode: PatchViewModel.PatchMode) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Button(
            onClick = {
                viewModel.doPatch(mode)
            } ,
            content = { Text(text = stringResource(id = R.string.patch_start_patch_btn)) }
        )
    }
}

@Composable
private fun AddedKPMView(addedExtras: MutableList<KPModel.IExtraInfo>) {
    if(addedExtras.isEmpty()) return
    addedExtras.forEach( action = {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = run {
                MaterialTheme.colorScheme.secondaryContainer
            })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = stringResource(id = R.string.patch_item_add_extra_kpm) +
                            " " + it.type.toString().lowercase(),
                        style = MaterialTheme.typography.bodyLarge)
                }
                if(it.type == KPModel.ExtraType.KPM) {
                    val kpmInfo: KPModel.KPMInfo = it as KPModel.KPMInfo
                    var args by remember {
                        mutableStateOf(kpmInfo.args)
                    }
                    Text(text = stringResource(id = R.string.patch_item_extra_name) + kpmInfo.name, style = MaterialTheme.typography.bodyMedium)
                    Text(text = stringResource(id = R.string.patch_item_extra_version) + kpmInfo.version, style = MaterialTheme.typography.bodyMedium)
                    Text(text = stringResource(id = R.string.patch_item_extra_author) + kpmInfo.license, style = MaterialTheme.typography.bodyMedium)
                    Text(text = stringResource(id = R.string.patch_item_extra_kpm_license) + kpmInfo.author, style = MaterialTheme.typography.bodyMedium)
                    Text(text = stringResource(id = R.string.patch_item_extra_kpm_desciption) + kpmInfo.description, style = MaterialTheme.typography.bodyMedium)
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.LightGray)
                    ) {
                        Text(text = stringResource(id = R.string.patch_item_extra_kpm_args), style = MaterialTheme.typography.bodyMedium)
                        BasicTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = args,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            onValueChange = {
                                args = it
                                kpmInfo.args = it
                            },
                        )
                    }
                }
            }
        }
    })
}

private fun PatchLogView() {

}

@Composable
private fun SetSuperKeyView(viewModel: PatchViewModel) {
    var skey by remember { mutableStateOf(viewModel.imgPatchInfo.kpimgInfo.superKey) }
    var showWarn by remember { mutableStateOf(true) }
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(id = R.string.patch_item_skey),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if(showWarn) {
                Text(color = Color.Red,
                    text = stringResource(id = R.string.patch_item_set_skey_label),
                    style = MaterialTheme.typography.bodyMedium)
            }
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                value = skey,
                onValueChange = {
                    skey = it
                    viewModel.superkey = ""
                    if(skey.length >= 8 && skey.any { it.isDigit() } && skey.any{it.isLetter()}) {
                        showWarn = false
                        viewModel.superkey = it
                    }
                },
            )
        }
    }
}

@Composable
private fun KernelPatchImageView(kpImgInfo: KPModel.KPImgInfo) {
    if(kpImgInfo.version.isEmpty()) return
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(id = R.string.patch_item_kpimg), style = MaterialTheme.typography.bodyLarge)
            }
            Text(text = stringResource(id = R.string.patch_item_kpimg_version) + Version.uInt2String(kpImgInfo.version.substring(2).toUInt(16)), style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(id = R.string.patch_item_kpimg_comile_time) + kpImgInfo.compileTime, style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(id = R.string.patch_item_kpimg_config) + kpImgInfo.config, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun KernelImageView(kImgInfo: KPModel.KImgInfo) {
    if(kImgInfo.banner.isEmpty()) return

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(id = R.string.patch_item_kernel), style = MaterialTheme.typography.bodyLarge)
            }
            Text(text = kImgInfo.banner, style = MaterialTheme.typography.bodyMedium)
        }
    }
}


@Composable
private fun SelectFileButton(text: String, onSelected: (data: Intent, uri: Uri)-> Unit,) {
    val selectFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode != Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val data = it.data ?: return@rememberLauncherForActivityResult
        val uri = data.data ?: return@rememberLauncherForActivityResult
        onSelected(data, uri)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "*/*"
                selectFileLauncher.launch(intent)
            } ,
            content = { Text(text = text) }
        )
    }
}

@Composable
private fun ErrorView(error: String) {
    if(error.isEmpty()) return
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.error
        })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(id = R.string.patch_item_error), style = MaterialTheme.typography.bodyLarge)
            Text(text = error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PatchMode(mode: PatchViewModel.PatchMode) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(id = mode.sId), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar() {
    TopAppBar(title = { Text(stringResource(R.string.patch_config_title)) })
}