package me.bmax.akpatch.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.akpatch.KPNatives
import me.bmax.akpatch.R
import me.bmax.akpatch.ui.component.SearchAppBar
import me.bmax.akpatch.ui.util.extractKpatch

@Destination
@Composable
fun SuperUserScreen(navigator: DestinationsNavigator) {
    Scaffold(
        topBar = {
            SearchAppBar(
                title = { Text(stringResource(R.string.superuser)) },
                searchText = "",
                onSearchTextChange = {  },
                onClearClick = {  },
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
                                showDropdown = false
                            })
                            DropdownMenuItem(text = {
                                Text(
                                    if (true) {
                                        stringResource(R.string.hide_system_apps)
                                    } else {
                                        stringResource(R.string.show_system_apps)
                                    }
                                )
                            }, onClick = {
                                showDropdown = false
                            })
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SuperuserCard()
            ExtractKpatchCard()
        }
    }
}

@Composable
private fun SuperuserCard() {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            MaterialTheme.colorScheme.primaryContainer
        })
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Warning, "")
            Column (Modifier.padding(start = 20.dp)) {
                Text(
                    text = stringResource(R.string.superuser_note),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ExtractKpatchCard() {
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
                        Thread(Runnable {
                            if (KPNatives.makeMeSu() != 0L) {
                                succ = false
                                showResultDialog = true
                                return@Runnable
                            }
                            if (extractKpatch("/data/local/tmp/kpatch") != 0) {
                                succ = false
                                showResultDialog = true
                                return@Runnable
                            }
                            succ = true
                            showResultDialog = true
                        }).start()
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
