package me.bmax.akpatch.ui.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.akpatch.Natives
import me.bmax.akpatch.R
import me.bmax.akpatch.ui.component.InputDialog
import me.bmax.akpatch.ui.screen.destinations.InstallScreenDestination
import me.bmax.akpatch.ui.util.extractKpatch
import java.io.File

@Composable
fun ExtractExeCard() {
    var showDialog by remember { mutableStateOf(false) }
    var spath = remember { mutableStateOf("") }
    var showResultDialog by remember { mutableStateOf(false)}
    var succ by remember { mutableStateOf(false) }

    if(showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = {
                if(succ) Text(stringResource(id = R.string.succeed))
                else  Text(stringResource(id = R.string.falied))
            },
            text = {
                if(succ) Text(stringResource(id = R.string.extract_succeed_text))
                else Text(stringResource(id = R.string.extract_succeed_text)) },
            confirmButton = {
                TextButton(onClick = { showResultDialog = false}) {
                    Text("OK")
                }
            }
        )
    }

    if(showDialog) {
        InputDialog(
            onDismissRequest = { showDialog = false },
            icon = { Icon(imageVector = Icons.Filled.ContentPasteGo, contentDescription = null) },
            title = { Text("") },
            text = { Text("") },
            input = spath,
            label = "",
            confirmButton = {
                TextButton(
                    onClick = {
                        if (spath.value.isEmpty()) return@TextButton
                        val file = File(spath.value)
                        if(!file.isDirectory) return@TextButton

                        showDialog = false
                        Thread(Runnable {

                            if(Natives.makeMeSu() != 0L) {
                                succ = false
                                showResultDialog = true
                                return@Runnable
                            }
                            if(extractKpatch(file.absolutePath) != 0) {
                                succ = false
                                showResultDialog = true
                                return@Runnable
                            }
                            succ = true
                            showResultDialog = true
                        }).start()
                    }
                ) { Text(text = stringResource(id = R.string.dialog_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                    }
                ) { Text(text = stringResource(id = R.string.dialog_cancel)) }
            }
        )
    }
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .wrapContentWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .clickable {
                        showDialog = true
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(id = R.string.extract_text))
                IconButton(
                    onClick = {  },
                ) {
                    Icon(Icons.Filled.KeyboardDoubleArrowRight, "extract")
                }
            }
        }
    }
}

@Composable
fun PatchCard(navigator: DestinationsNavigator) {
    var showDialog by remember { mutableStateOf(false) }
    var superKey = remember { mutableStateOf("") }

    val selectBootimgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode != Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val data = it.data ?: return@rememberLauncherForActivityResult
        val uri = data.data ?: return@rememberLauncherForActivityResult
        navigator.navigate(InstallScreenDestination(uri, superKey.value))
    }
    if(showDialog) {
        InputDialog(
            onDismissRequest = { showDialog = false },
            icon = { Icon(imageVector = Icons.Filled.Key, contentDescription = null) },
            title = { Text(stringResource(id = R.string.super_key_dialog_title)) },
            text = { Text(stringResource(id = R.string.super_key_dialog_text)) },
            input = superKey,
            label = stringResource(id = R.string.super_key_dialog_label),
            confirmButton = {
                TextButton(
                    onClick = {
                        if(superKey.value.isEmpty()) return@TextButton
                        showDialog = false
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                        intent.type = "*/*"
                        selectBootimgLauncher.launch(intent)
                    }
                ) { Text(text = stringResource(id = R.string.dialog_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                    }
                ) { Text(text = stringResource(id = R.string.dialog_cancel)) }
            }
        )
    }
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .wrapContentWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .clickable { showDialog = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(id = R.string.patch))
                IconButton(
                    onClick = { showDialog = true },
                ) {
                    Icon(Icons.Filled.Add, "")
                }
            }
        }
    }
}



@Composable
fun GrantPidSuCard() {
    var showPidDialog by remember { mutableStateOf(false) }
    var succ by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var tidStr = remember { mutableStateOf("") }

    if(showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = { Text(stringResource(id = R.string.auth_succeed)) },
            text = { Text(stringResource(id = R.string.auth_succeed_text)) },
            confirmButton = {
                TextButton(onClick = { showResultDialog = false}) {
                    Text("OK")
                }
            }
        )
    }

    if(showPidDialog) {
        InputDialog(
            onDismissRequest = { showPidDialog = false },
            icon = { Icon(imageVector = Icons.Filled.Security, contentDescription = null) },
            title = { Text(stringResource(id = R.string.grant_su_title)) },
            text = { Text(stringResource(id = R.string.grant_su_text)) },
            input = tidStr,
            label = "Thread Id",
            confirmButton = {
                TextButton(
                    onClick = {
                        showPidDialog = false
                        val tid: Int = tidStr.value.toIntOrNull() ?: return@TextButton
                        val ret = Natives.threadSu(tid)
                        succ = ret == 0L
                        showResultDialog = true
                    }
                ) {
                    Text(text = stringResource(id = R.string.dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPidDialog = false
                    }
                ) { Text(text = stringResource(id = R.string.dialog_cancel)) }
            }
        )
    }
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.secondaryContainer
        })
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .wrapContentWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .clickable { showPidDialog = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(id = R.string.grant_su))
                IconButton(
                    onClick = { showPidDialog = true },
                ) {
                    Icon(Icons.Filled.Add, "")
                }
            }
        }
    }
}

