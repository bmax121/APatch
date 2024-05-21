package me.bmax.apatch.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.screen.APModuleScreen
import me.bmax.apatch.ui.screen.AboutScreen
import me.bmax.apatch.ui.screen.BottomBarDestination
import me.bmax.apatch.ui.screen.HomeScreen
import me.bmax.apatch.ui.screen.InstallModeSelectScreen
import me.bmax.apatch.ui.screen.InstallScreen
import me.bmax.apatch.ui.screen.KPModuleScreen
import me.bmax.apatch.ui.screen.Patches
import me.bmax.apatch.ui.screen.SettingScreen
import me.bmax.apatch.ui.screen.SuperUserScreen
import me.bmax.apatch.ui.theme.APatchTheme
import me.bmax.apatch.ui.viewmodel.UIViewModel
import me.bmax.apatch.util.ui.LocalSnackbarHost
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer

class MainActivity : AppCompatActivity() {
    private var isLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splashScreen.setKeepOnScreenCondition { isLoading }

        setContent {
            val uiViewModel = viewModel<UIViewModel>()
            APatchTheme {
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }

                Scaffold(
                    bottomBar = { BottomBar(navController) },
                    snackbarHost = { SnackbarHost(snackBarHostState) }
                ) { paddingValues ->
                    CompositionLocalProvider(
                        LocalSnackbarHost provides snackBarHostState,
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = "Home",
                            modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()),
                            enterTransition = { fadeIn(animationSpec = tween(300)) },
                            exitTransition = { fadeOut(animationSpec = tween(300)) }
                        ) {
                            composable(route = "Home") { HomeScreen(navController, uiViewModel) }
                            composable(route = "InstallModeSelect") { InstallModeSelectScreen(navController, uiViewModel) }
                            composable(route = "Patches") { Patches(uiViewModel) }
                            composable(route = "About") { AboutScreen(navController) }
                            composable(route = "KPModule") { KPModuleScreen(navController, uiViewModel) }
                            composable(route = "SuperUser") { SuperUserScreen() }
                            composable(route = "APModule") { APModuleScreen(navController, uiViewModel) }
                            composable(route = "Install") { InstallScreen(navController, uiViewModel) }
                            composable(route = "Setting") { SettingScreen() }
                        }
                    }
                }
            }
        }

        // Initialize Coil
        val context = this
        val iconSize = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(iconSize, false, context))
                }
                .build()
        )

        isLoading = false
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val kPatchReady = state != APApplication.State.UNKNOWN_STATE
    val aPatchReady =
        (state == APApplication.State.ANDROIDPATCH_INSTALLING || state == APApplication.State.ANDROIDPATCH_INSTALLED || state == APApplication.State.ANDROIDPATCH_NEED_UPDATE)

    NavigationBar(tonalElevation = 8.dp) {
        BottomBarDestination.entries.forEach { destination ->
            val backStack by navController.currentBackStackEntryAsState()
            val isCurrentDestOnBackStack = backStack?.destination?.route == destination.direction

            val hideDestination =
                (destination.kPatchRequired && !kPatchReady) || (destination.aPatchRequired && !aPatchReady)
            if (hideDestination) return@forEach
            NavigationBarItem(selected = isCurrentDestOnBackStack, onClick = {
                if (isCurrentDestOnBackStack) {
                    navController.popBackStack(destination.direction, false)
                }
                val popSuccess = navController.popBackStack()
                navController.navigate(destination.direction) {
                    popUpTo("Home") {
                        inclusive = popSuccess
                    }
                }
            }, icon = {
                if (isCurrentDestOnBackStack) {
                    Icon(destination.iconSelected, stringResource(destination.label))
                } else {
                    Icon(destination.iconNotSelected, stringResource(destination.label))
                }
            },

                label = {
                    Text(
                        stringResource(destination.label),
                        overflow = TextOverflow.Visible,
                        maxLines = 1,
                        softWrap = false
                    )
                }, alwaysShowLabel = false
            )
        }
    }
}
