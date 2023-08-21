package me.bmax.akpatch.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.akpatch.R
import me.bmax.akpatch.sckey
import me.bmax.akpatch.viewmodel.ConfigViewModel

@Destination
@Composable
fun LogScreen(navigator: DestinationsNavigator) {
    var showKernel by remember { mutableStateOf(true) }
    var showUser by remember { mutableStateOf(true) }
    val viewModel: ConfigViewModel = viewModel<ConfigViewModel>()

    val scrollState = rememberScrollState()

    Scaffold(topBar = {
        TopBar()
    }, floatingActionButton =
        {
    }) { innerPadding ->
        Column (
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(1f)
                    .padding(innerPadding)
                    .verticalScroll(scrollState),
            ) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = sckey.current,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                )
                LaunchedEffect(sckey.current) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }


            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.padding(8.dp),
                    onClick = {
                        showKernel = !showKernel
                    },
                    icon = {
                        if (showKernel) {
                            Icon(Icons.Outlined.Article, contentDescription = "log_kernel")
                        } else {
                            Icon(Icons.Filled.Article, contentDescription = "log_kernel")
                        }
                    },
                    text = { Text(text = stringResource(id = R.string.log_kernel)) },
                )
                ExtendedFloatingActionButton(
                    modifier = Modifier.padding(8.dp),
                    onClick = {
                        showUser= !showUser
                    },
                    icon = {
                        if (showUser) {
                            Icon(Icons.Outlined.Article, contentDescription = "log_user")
                        } else {
                            Icon(Icons.Filled.Article, contentDescription = "log_user")
                        }
                    },
                    text = { Text(text = stringResource(id = R.string.log_user)) },
                )
                Spacer(modifier = Modifier.weight(1f))
                ExtendedFloatingActionButton(
                    modifier = Modifier.padding(8.dp),
                    onClick = {
                        //
                    },
                    icon = { Icon(Icons.Filled.Clear, contentDescription = "clear") },
                    text = { Text(text = stringResource(id = R.string.clear)) },
                )
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar() {
    TopAppBar(title = { Text(stringResource(R.string.log)) })
}