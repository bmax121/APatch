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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.utils.app.permission.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.R
import me.bmax.apatch.ui.viewmodel.KPModel
import me.bmax.apatch.ui.viewmodel.PatchesViewModel
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.reboot

private const val TAG = "Patches"

@Destination
@Composable
fun Patches(mode: PatchesViewModel.PatchMode) {
    val permissionRequest = remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val viewModel = viewModel<PatchesViewModel>()
    SideEffect {
        viewModel.prepare(mode)
    }

    Scaffold(topBar = {
        TopBar()
    }, floatingActionButton = {
        if (viewModel.needReboot) {
            val reboot = stringResource(id = R.string.reboot)
            ExtendedFloatingActionButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            reboot()
                        }
                    }
                },
                icon = { Icon(Icons.Filled.Refresh, reboot) },
                text = { Text(text = reboot) },
            )
        }
    }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val current = LocalContext.current

            // request permissions
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
            KernelPatchImageView(viewModel.kpimgInfo)

            if (mode == PatchesViewModel.PatchMode.PATCH_ONLY && selectedBootImage != null && viewModel.kimgInfo.banner.isEmpty()) {
                viewModel.copyAndParseBootimg(selectedBootImage!!)
                // Fix endless loop. It's not normal if (parse done && working thread is not working) but banner still null
                // Leave user re-choose
                if (!viewModel.running && viewModel.kimgInfo.banner.isEmpty()) {
                    selectedBootImage = null
                }
            }

            // select boot.img
            if (mode == PatchesViewModel.PatchMode.PATCH_ONLY && viewModel.kimgInfo.banner.isEmpty()) {
                SelectFileButton(
                    text = stringResource(id = R.string.patch_select_bootimg_btn),
                    onSelected = { data, uri ->
                        Log.d(TAG, "select boot.img, data: $data, uri: $uri")
                        viewModel.copyAndParseBootimg(uri)
                    }
                )
            }

            if (viewModel.bootSlot.isNotEmpty() || viewModel.bootDev.isNotEmpty()) {
                BootimgView(slot = viewModel.bootSlot, boot = viewModel.bootDev)
            }

            if (viewModel.kimgInfo.banner.isNotEmpty()) {
                KernelImageView(viewModel.kimgInfo)
            }

            if (mode != PatchesViewModel.PatchMode.UNPATCH && viewModel.kimgInfo.banner.isNotEmpty()) {
                SetSuperKeyView(viewModel)
            }

            // existed extras
            if (mode == PatchesViewModel.PatchMode.PATCH_AND_INSTALL || mode == PatchesViewModel.PatchMode.INSTALL_TO_NEXT_SLOT) {
                viewModel.existedExtras.forEach(action = {
                    ExtraItem(extra = it, true, onDelete = {
                        viewModel.existedExtras.remove(it)
                    })
                })
            }

            // add new extras
            if (mode != PatchesViewModel.PatchMode.UNPATCH) {
                viewModel.newExtras.forEach(action = {
                    ExtraItem(extra = it, false, onDelete = {
                        val idx = viewModel.newExtras.indexOf(it)
                        viewModel.newExtras.remove(it)
                        viewModel.newExtrasFileName.removeAt(idx)
                    })
                })
            }

            // add new KPM
            if (viewModel.superkey.isNotEmpty() && !viewModel.patching && !viewModel.patchdone && mode != PatchesViewModel.PatchMode.UNPATCH) {
                SelectFileButton(
                    text = stringResource(id = R.string.patch_embed_kpm_btn),
                    onSelected = { data, uri ->
                        Log.d(TAG, "select kpm, data: $data, uri: $uri")
                        viewModel.embedKPM(uri)
                    }
                )
            }

            // do patch, update, unpatch
            if (!viewModel.patching && !viewModel.patchdone) {
                // patch start
                if (mode != PatchesViewModel.PatchMode.UNPATCH && viewModel.superkey.isNotEmpty()) {
                    StartButton(stringResource(id = R.string.patch_start_patch_btn)) {
                        viewModel.doPatch(
                            mode
                        )
                    }
                }
                // unpatch
                if (mode == PatchesViewModel.PatchMode.UNPATCH && viewModel.kimgInfo.banner.isNotEmpty()) {
                    StartButton(stringResource(id = R.string.patch_start_unpatch_btn)) { viewModel.doUnpatch() }
                }
            }

            // patch log
            if (viewModel.patching || viewModel.patchdone) {
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

            Spacer(modifier = Modifier.height(12.dp))

            // loading progress
            if (viewModel.running) {
                Box(
                    modifier = Modifier
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
private fun StartButton(text: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Button(
            onClick = onClick,
            content = {
                Text(text = text)
            }
        )
    }
}

@Composable
private fun ExtraItem(extra: KPModel.IExtraInfo, existed: Boolean, onDelete: () -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        }),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    text = stringResource(
                        id =
                        if (existed) R.string.patch_item_existed_extra_kpm else R.string.patch_item_new_extra_kpm
                    ) +
                            " " + extra.type.toString().uppercase(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )
                Icon(imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable { onDelete() })
            }
            if (extra.type == KPModel.ExtraType.KPM) {
                val kpmInfo: KPModel.KPMInfo = extra as KPModel.KPMInfo
                Text(
                    text = "${stringResource(id = R.string.patch_item_extra_name)} ${kpmInfo.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${stringResource(id = R.string.patch_item_extra_version)} ${kpmInfo.version}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${stringResource(id = R.string.patch_item_extra_kpm_license)} ${kpmInfo.license}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${stringResource(id = R.string.patch_item_extra_author)} ${kpmInfo.author}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${stringResource(id = R.string.patch_item_extra_kpm_desciption)} ${kpmInfo.description}",
                    style = MaterialTheme.typography.bodyMedium
                )
                var event by remember { mutableStateOf(kpmInfo.event) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.LightGray)
                ) {
                    Text(
                        text = stringResource(id = R.string.patch_item_extra_event),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    BasicTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = event,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        onValueChange = {
                            event = it
                            kpmInfo.event = it
                        },
                    )
                }
                var args by remember { mutableStateOf(kpmInfo.args) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.LightGray)
                ) {
                    Text(
                        text = stringResource(id = R.string.patch_item_extra_args),
                        style = MaterialTheme.typography.bodyMedium
                    )
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
}


@Composable
private fun SetSuperKeyView(viewModel: PatchesViewModel) {
    var skey by remember { mutableStateOf(viewModel.superkey) }
    var showWarn by remember { mutableStateOf(!viewModel.checkSuperKeyValidation(skey)) }
    var keyVisible by remember { mutableStateOf(false) }
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
                Text(
                    text = stringResource(id = R.string.patch_item_skey),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (showWarn) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    color = Color.Red,
                    text = stringResource(id = R.string.patch_item_set_skey_label),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Column {
                //Spacer(modifier = Modifier.height(8.dp))
                Box(
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        value = skey,
                        label = { Text(stringResource(id = R.string.patch_set_superkey)) },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(50.0f),
                        onValueChange = {
                            skey = it
                            if (viewModel.checkSuperKeyValidation(it)) {
                                viewModel.superkey = it
                                showWarn = false
                            } else {
                                viewModel.superkey = ""
                                showWarn = true
                            }
                        },
                    )
                    IconButton(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(top = 15.dp, end = 5.dp),
                        onClick = { keyVisible = !keyVisible }
                    ) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KernelPatchImageView(kpImgInfo: KPModel.KPImgInfo) {
    if (kpImgInfo.version.isEmpty()) return
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
                Text(
                    text = stringResource(id = R.string.patch_item_kpimg),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Text(
                text = stringResource(id = R.string.patch_item_kpimg_version) + Version.uInt2String(
                    kpImgInfo.version.substring(2).toUInt(16)
                ), style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(id = R.string.patch_item_kpimg_comile_time) + kpImgInfo.compileTime,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(id = R.string.patch_item_kpimg_config) + kpImgInfo.config,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun BootimgView(slot: String, boot: String) {
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
                Text(
                    text = stringResource(id = R.string.patch_item_bootimg),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (slot.isNotEmpty()) {
                Text(
                    text = stringResource(id = R.string.patch_item_bootimg_slot) + " " + slot,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = stringResource(id = R.string.patch_item_bootimg_dev) + " " + boot,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun KernelImageView(kImgInfo: KPModel.KImgInfo) {
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
                Text(
                    text = stringResource(id = R.string.patch_item_kernel),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Text(text = kImgInfo.banner, style = MaterialTheme.typography.bodyMedium)
        }
    }
}


@Composable
private fun SelectFileButton(text: String, onSelected: (data: Intent, uri: Uri) -> Unit) {
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
            },
            content = { Text(text = text) }
        )
    }
}

@Composable
private fun ErrorView(error: String) {
    if (error.isEmpty()) return
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
            Text(
                text = stringResource(id = R.string.patch_item_error),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(text = error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PatchMode(mode: PatchesViewModel.PatchMode) {
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