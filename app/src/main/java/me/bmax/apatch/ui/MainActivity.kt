package me.bmax.apatch.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
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
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.rememberNavHostEngine
import com.ramcosta.composedestinations.utils.isRouteOnBackStackAsState
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.screen.BottomBarDestination
import me.bmax.apatch.ui.theme.APatchTheme
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import me.bmax.apatch.util.ui.LocalSnackbarHost
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer

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

        setContent {
            APatchTheme {
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }
                val configuration = LocalConfiguration.current
                val bottomBarRoutes = remember {
                    BottomBarDestination.entries.map { it.direction.route }.toSet()
                }
                val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
                val kPatchReady = state != APApplication.State.UNKNOWN_STATE
                val aPatchReady = state == APApplication.State.ANDROIDPATCH_INSTALLED
                val visibleDestinations = remember(state) {
                    BottomBarDestination.entries.filter { destination ->
                        !(destination.kPatchRequired && !kPatchReady) && !(destination.aPatchRequired && !aPatchReady)
                    }.toSet()
                }

                val defaultTransitions = object : NavHostAnimatedDestinationStyle() {
                    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                        {
                            // If the target is a detail page (not a bottom navigation page), slide in from the right
                            if (targetState.destination.route !in bottomBarRoutes) {
                                slideInHorizontally(initialOffsetX = { it })
                            } else {
                                // Otherwise (switching between bottom navigation pages), use fade in
                                fadeIn(animationSpec = tween(340))
                            }
                        }

                    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                        {
                            // If navigating from the home page (bottom navigation page) to a detail page, slide out to the left
                            if (initialState.destination.route in bottomBarRoutes && targetState.destination.route !in bottomBarRoutes) {
                                slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut()
                            } else {
                                // Otherwise (switching between bottom navigation pages), use fade out
                                fadeOut(animationSpec = tween(340))
                            }
                        }

                    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                        {
                            // If returning to the home page (bottom navigation page), slide in from the left
                            if (targetState.destination.route in bottomBarRoutes) {
                                slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
                            } else {
                                // Otherwise (e.g., returning between multiple detail pages), use default fade in
                                fadeIn(animationSpec = tween(340))
                            }
                        }

                    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                        {
                            // If returning from a detail page (not a bottom navigation page), scale down and fade out
                            if (initialState.destination.route !in bottomBarRoutes) {
                                scaleOut(targetScale = 0.9f) + fadeOut()
                            } else {
                                // Otherwise, use default fade out
                                fadeOut(animationSpec = tween(340))
                            }
                        }
                }

                LaunchedEffect(Unit) {
                    if (SuperUserViewModel.apps.isEmpty()) {
                        SuperUserViewModel().fetchAppList()
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                            BottomBar(navController, visibleDestinations)
                        }
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    CompositionLocalProvider(
                        LocalSnackbarHost provides snackBarHostState,
                    ) {
                        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            Row(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))) {
                                SideBar(navController = navController, modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)), visibleDestinations = visibleDestinations)
                                DestinationsNavHost(
                                    modifier = Modifier.weight(1f),
                                    navGraph = NavGraphs.root,
                                    navController = navController,
                                    engine = rememberNavHostEngine(navHostContentAlignment = Alignment.TopCenter),
                                    defaultTransitions = defaultTransitions
                                )
                            }
                        } else {
                            DestinationsNavHost(
                                modifier = Modifier.padding(innerPadding),
                                navGraph = NavGraphs.root,
                                navController = navController,
                                engine = rememberNavHostEngine(navHostContentAlignment = Alignment.TopCenter),
                                defaultTransitions = defaultTransitions
                            )
                        }
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
private fun BottomBar(navController: NavHostController, visibleDestinations: Set<BottomBarDestination>) {
    val navigator = navController.rememberDestinationsNavigator()

    Crossfade(
        targetState = visibleDestinations,
        label = "BottomBarStateCrossfade"
    ) { visibleDestinations ->
        NavigationBar(tonalElevation = 8.dp) {
            visibleDestinations.forEach { destination ->
                val isCurrentDestOnBackStack by navController.isRouteOnBackStackAsState(destination.direction)

                NavigationBarItem(
                    selected = isCurrentDestOnBackStack,
                    onClick = {
                        if (isCurrentDestOnBackStack) {
                            navigator.popBackStack(destination.direction, false)
                        }
                        navigator.navigate(destination.direction) {
                            popUpTo(NavGraphs.root) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        if (isCurrentDestOnBackStack) {
                            Icon(destination.iconSelected, stringResource(destination.label))
                        } else {
                            Icon(destination.iconNotSelected, stringResource(destination.label))
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(destination.label),
                            overflow = TextOverflow.Visible,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    alwaysShowLabel = false
                )
            }
        }
    }
}

@Composable
private fun SideBar(navController: NavHostController, modifier: Modifier = Modifier, visibleDestinations: Set<BottomBarDestination>) {
    val navigator = navController.rememberDestinationsNavigator()
    val bottomBarRoutes = remember {
        BottomBarDestination.entries.map { it.direction.route }.toSet()
    }
    val currentRoute = navController.currentBackStackEntry?.destination?.route

    Crossfade(
        targetState = visibleDestinations,
        label = "SideBarStateCrossfade"
    ) { visibleDestinations ->
        NavigationRail(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                visibleDestinations.forEach { destination ->
                    val isCurrentDestOnBackStack by navController.isRouteOnBackStackAsState(destination.direction)
                    NavigationRailItem(
                        selected = isCurrentDestOnBackStack,
                        onClick = {
                            if (isCurrentDestOnBackStack) {
                                navigator.popBackStack(destination.direction, false)
                            }
                            navigator.navigate(destination.direction) {
                                popUpTo(NavGraphs.root) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (isCurrentDestOnBackStack) {
                                Icon(destination.iconSelected, stringResource(destination.label))
                            } else {
                                Icon(destination.iconNotSelected, stringResource(destination.label))
                            }
                        },
                        label = { Text(stringResource(destination.label)) },
                        alwaysShowLabel = false,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
