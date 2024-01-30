package me.bmax.apatch.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.system.Os
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.utils.app.permission.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.ConfirmDialog
import me.bmax.apatch.ui.component.ConfirmResult
import me.bmax.apatch.ui.component.LoadingDialog
import me.bmax.apatch.ui.screen.destinations.PatchScreenDestination
import me.bmax.apatch.ui.viewmodel.KPModuleViewModel
import me.bmax.apatch.ui.viewmodel.PatchViewModel
import me.bmax.apatch.util.LocalDialogHost
import me.bmax.apatch.util.getSELinuxStatus
import java.net.URI

private val TAG = "PatchSetting"

@OptIn(ExperimentalMaterialApi::class)
@Destination
@Composable
fun PatchSetting(navigator: DestinationsNavigator, newPatch: Boolean) {
    val kpState = APApplication.kpStateLiveData.value
    var bootimgExist by remember { mutableStateOf(false) }
    var permissionRequest = remember { mutableStateOf(false)  }

    val viewModel = viewModel<PatchViewModel>()


    Scaffold(topBar = {
        TopBar()
    }, floatingActionButton = {

    }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
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

            PatchMode(newPatch)

            SelectFileButton(
                text = stringResource(id = R.string.patch_select_bootimg),
                show = !bootimgExist,
                onSelected = {data, uri ->
                    Log.d(TAG, "select boot.img, data: ${data}, uri: ${uri}")
                    viewModel.copyAndParseBootimg(uri)
                }
            )


            if(viewModel.running) {
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
private fun BootimgView() {

}

@Composable
private fun SelectFileButton(text: String, show: Boolean, onSelected: (data: Intent, uri: Uri)-> Unit,) {
    if(show) {
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
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
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
}



@Composable
private fun PatchMode(newPatch: Boolean) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if(newPatch) {
                Text(text = stringResource(id = R.string.patch_mode_bootimg_patch), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(text = stringResource(id = R.string.patch_mode_update_patch), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar() {
    TopAppBar(title = { Text(stringResource(R.string.patch_config_title)) })
}