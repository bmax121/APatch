package me.bmax.akpatch.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.akpatch.R
import me.bmax.akpatch.ui.component.AboutDialog

@Destination
@Composable
fun SettingScreen(navigator: DestinationsNavigator) {
    Scaffold(
        topBar = {
            TopBar(onBack = {
                navigator.popBackStack()
            })
        }
    ) { paddingValues ->
        val showAboutDialog = remember { mutableStateOf(false) }

        AboutDialog(showAboutDialog)

        Column(modifier = Modifier.padding(paddingValues)) {
            val about = stringResource(id = R.string.about)
            ListItem(
                leadingContent = { Icon(Icons.Filled.ContactPage, stringResource(id = R.string.about)) },
                headlineContent = { Text(about) },
                modifier = Modifier.clickable {
                    showAboutDialog.value = true
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onBack: () -> Unit = {}) {
    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
            IconButton(
                onClick = onBack
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
        },
    )
}
