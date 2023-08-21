package me.bmax.akpatch.ui.screen

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.akpatch.R
import me.bmax.akpatch.ui.util.patchBootimg
import me.bmax.akpatch.ui.util.reboot
import java.lang.StringBuilder
import java.util.*

@Composable
@Destination
fun InstallScreen(navigator: DestinationsNavigator, uri: Uri, superKey: String) {
    var text by rememberSaveable { mutableStateOf("") }
    val logContent = StringBuilder()
    var showFloatAction by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (text.isNotEmpty()) {
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val ret = patchBootimg(uri, superKey, onMessage = {
                logContent.append(it).append("\n")
                text += "$it\n"
            })
//            if(ret == 0) {
//                showFloatAction = true
//            }
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                onBack = {
                    navigator.popBackStack()
                }
            )
        },
        floatingActionButton = {
            if (showFloatAction) {
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize(1f)
                .padding(innerPadding)
                .verticalScroll(scrollState),
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                text = text,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            )
            LaunchedEffect(text) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onBack: () -> Unit = {}) {
    TopAppBar(
        title = { Text(stringResource(R.string.patch)) },
        navigationIcon = {
            IconButton(
                onClick = onBack
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
        }
    )
}
