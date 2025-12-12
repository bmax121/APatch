package me.bmax.apatch.ui

import android.annotation.SuppressLint
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import coil.Coil
import coil.ImageLoader
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.rememberNavHostEngine
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.rememberConfirmCallback
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.ui.screen.BottomBarDestination
import me.bmax.apatch.ui.screen.MODULE_TYPE
import me.bmax.apatch.ui.theme.APatchTheme
import me.bmax.apatch.ui.viewmodel.APModuleViewModel
import me.bmax.apatch.util.ModuleParser
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold

class MainActivity : AppCompatActivity() {

    private var isLoading = true

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen().setKeepOnScreenCondition { isLoading }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        // Disables automatic window adjustment when the soft keyboard appears
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        val uri: Uri? = intent.data ?: run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra("uris", Uri::class.java)?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>("uris")?.firstOrNull()
            }
        }

        setContent {
            APatchTheme {
                val navController = rememberNavController()
                val navigator = navController.rememberDestinationsNavigator()

                val bottomBarRoutes = remember {
                    BottomBarDestination.entries.map { it.direction.route }.toSet()
                }

                val loadingDialog = rememberLoadingDialog()
                val viewModel = viewModel<APModuleViewModel>()
                val context = LocalContext.current
                val currentUri by rememberUpdatedState(uri)

                val confirmDialog = rememberConfirmDialog(
                    callback = rememberConfirmCallback(
                        onConfirm = {
                            currentUri?.let { uri ->
                                navigator.navigate(InstallScreenDestination(uri, MODULE_TYPE.APM))
                            }
                        }, onDismiss = { }
                    )
                )

                var moduleInstallDesc by remember { mutableStateOf("") }

                LaunchedEffect(currentUri) {
                    currentUri?.let { uri ->
                        viewModel.fetchModuleList()
                        val desc = loadingDialog.withLoading {
                            withContext(Dispatchers.IO) {
                                ModuleParser.getModuleInstallDesc(context, uri, viewModel.moduleList)
                            }
                        }
                        moduleInstallDesc = desc

                        confirmDialog.showConfirm(
                            title = context.getString(R.string.apm),
                            content = moduleInstallDesc
                        )
                    }
                }

                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route
                val showBottomBar = currentRoute != InstallScreenDestination.route

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            BottomBar(navController)
                        }
                    }
                ) {
                    CompositionLocalProvider {
                        DestinationsNavHost(
                            modifier = Modifier
                                .padding(bottom = if (showBottomBar) 65.dp else 0.dp),
                            navGraph = NavGraphs.root,
                            navController = navController,
                            engine = rememberNavHostEngine(navHostContentAlignment = Alignment.TopCenter),
                            defaultTransitions = object : NavHostAnimatedDestinationStyle() {
                                override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                                    {
                                        val isBottomBarTransition =
                                            initialState.destination.route in bottomBarRoutes &&
                                                    targetState.destination.route in bottomBarRoutes

                                        if (isBottomBarTransition) {
                                            val initialIndex =
                                                BottomBarDestination.entries.indexOfFirst {
                                                    it.direction.route == initialState.destination.route
                                                }
                                            val targetIndex =
                                                BottomBarDestination.entries.indexOfFirst {
                                                    it.direction.route == targetState.destination.route
                                                }

                                            if (targetIndex > initialIndex) {
                                                slideInHorizontally(
                                                    initialOffsetX = { it },
                                                    animationSpec = tween(300)
                                                ) + fadeIn(animationSpec = tween(300))
                                            } else {
                                                slideInHorizontally(
                                                    initialOffsetX = { -it },
                                                    animationSpec = tween(300)
                                                ) + fadeIn(animationSpec = tween(300))
                                            }
                                        } else if (targetState.destination.route !in bottomBarRoutes) {
                                            slideInHorizontally(
                                                initialOffsetX = { it },
                                                animationSpec = tween(300)
                                            )
                                        } else {
                                            fadeIn(animationSpec = tween(300))
                                        }
                                    }

                                override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                                    {
                                        val isBottomBarTransition =
                                            initialState.destination.route in bottomBarRoutes &&
                                                    targetState.destination.route in bottomBarRoutes

                                        if (isBottomBarTransition) {
                                            val initialIndex =
                                                BottomBarDestination.entries.indexOfFirst {
                                                    it.direction.route == initialState.destination.route
                                                }
                                            val targetIndex =
                                                BottomBarDestination.entries.indexOfFirst {
                                                    it.direction.route == targetState.destination.route
                                                }

                                            if (targetIndex > initialIndex) {
                                                slideOutHorizontally(
                                                    targetOffsetX = { -it },
                                                    animationSpec = tween(300)
                                                ) + fadeOut(animationSpec = tween(300))
                                            } else {
                                                slideOutHorizontally(
                                                    targetOffsetX = { it },
                                                    animationSpec = tween(300)
                                                ) + fadeOut(animationSpec = tween(300))
                                            }
                                        } else if (initialState.destination.route in bottomBarRoutes &&
                                            targetState.destination.route !in bottomBarRoutes
                                        ) {
                                            slideOutHorizontally(
                                                targetOffsetX = { -it / 4 },
                                                animationSpec = tween(300)
                                            ) + fadeOut(animationSpec = tween(300))
                                        } else {
                                            fadeOut(animationSpec = tween(300))
                                        }
                                    }

                                override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                                    {
                                        if (targetState.destination.route in bottomBarRoutes) {
                                            slideInHorizontally(
                                                initialOffsetX = { -it / 4 },
                                                animationSpec = tween(300)
                                            ) + fadeIn(animationSpec = tween(300))
                                        } else {
                                            fadeIn(animationSpec = tween(300))
                                        }
                                    }

                                override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                                    {
                                        if (initialState.destination.route !in bottomBarRoutes) {
                                            slideOutHorizontally(
                                                targetOffsetX = { it },
                                                animationSpec = tween(300)
                                            ) + fadeOut(animationSpec = tween(300))
                                        } else {
                                            fadeOut(animationSpec = tween(300))
                                        }
                                    }
                            }
                        )
                    }
                }
            }
        }

        // Initialize Coil
        val iconSize = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(iconSize, false, this@MainActivity))
                }
                .build()
        )

        isLoading = false
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val navigator = navController.rememberDestinationsNavigator()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    Crossfade(
        targetState = state,
        label = "BottomBarStateCrossfade"
    ) { state ->
        val kPatchReady = state != APApplication.State.UNKNOWN_STATE
        val aPatchReady = state == APApplication.State.ANDROIDPATCH_INSTALLED


        val navItems = BottomBarDestination.entries
            .filter { d ->
                !(d.kPatchRequired && !kPatchReady) &&
                        !(d.aPatchRequired && !aPatchReady)
            }
            .map { d ->
                d to NavigationItem(
                    label = stringResource(d.label),
                    icon = if (currentRoute == d.direction.route) d.iconSelected else d.iconNotSelected
                )
            }

        val selectedIndex =
            navItems.indexOfFirst { it.first.direction.route == currentRoute }.coerceAtLeast(0)

        NavigationBar(
            items = navItems.map { it.second },
            selected = selectedIndex,
            onClick = { index ->
                val dest = navItems[index].first
                if (currentRoute == dest.direction.route) {
                    navigator.popBackStack(dest.direction, false)
                }

                navigator.navigate(dest.direction) {
                    popUpTo(NavGraphs.root) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
    }
}
