package me.bmax.apatch.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.PatchesDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.viewmodel.PatchesViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back

@Destination<RootGraph>
@Composable
fun UninstallModeSelectScreen(navigator: DestinationsNavigator) {

    Scaffold(
        modifier = Modifier.padding(16.dp),
        topBar = {
            TopBar(onBack = dropUnlessResumed { navigator.popBackStack() },
        )
    }) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            Card {
                SuperArrow(
                    title = stringResource(R.string.home_dialog_uninstall_all),
                    onClick = {
                        APApplication.uninstallApatch()
                        navigator.navigate(PatchesDestination(PatchesViewModel.PatchMode.UNPATCH))
                    }
                )
                SuperArrow(
                    title = stringResource(R.string.home_dialog_restore_image),
                    onClick = {
                        navigator.navigate(PatchesDestination(PatchesViewModel.PatchMode.UNPATCH))
                    }
                )
                SuperArrow(
                    title = stringResource(R.string.home_dialog_uninstall_ap_only),
                    onClick = {
                        APApplication.uninstallApatch()
                    }
                )
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit = {}) {
    SmallTopAppBar(
        title = stringResource(R.string.home_dialog_uninstall_title),
        navigationIcon = {
            IconButton(
                onClick = onBack
            ) { Icon(MiuixIcons.Useful.Back, contentDescription = null) }
        },
    )
}