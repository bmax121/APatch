package me.bmax.akpatch.ui.screen

import android.app.Activity.RESULT_OK
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.akpatch.R

@Destination
@Composable
fun ModuleScreen(navigator: DestinationsNavigator) {
    val hideInstallButton = false
    Scaffold(topBar = {
        TopBar()
    }, floatingActionButton = if (hideInstallButton) {
        { /* Empty */ }
    } else {
        {
            val moduleInstall = stringResource(id = R.string.kp_install)
            val selectZipLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                if (it.resultCode != RESULT_OK) {
                    return@rememberLauncherForActivityResult
                }
                val data = it.data ?: return@rememberLauncherForActivityResult
                val uri = data.data ?: return@rememberLauncherForActivityResult
//                navigator.navigate(InstallModuleDestination(uri))
            }
            ExtendedFloatingActionButton(
                onClick = {
                    // select the zip file to install
//                    val intent = Intent(Intent.ACTION_GET_CONTENT)
//                    intent.type = "application/zip"
//                    selectZipLauncher.launch(intent)
                },
                icon = { Icon(Icons.Filled.Add, moduleInstall) },
                text = { Text(text = moduleInstall) },
            )
        }
    }) { innerPadding ->
        Column (
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = stringResource(id = R.string.module_support))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar() {
    TopAppBar(title = { Text(stringResource(R.string.module)) })
}