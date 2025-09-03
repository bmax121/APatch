package me.bmax.apatch.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.rememberNavHostEngine
import com.ramcosta.composedestinations.utils.isRouteOnBackStackAsState
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.screen.BottomBarDestination
import me.bmax.apatch.ui.theme.APatchTheme
import me.bmax.apatch.util.ui.LocalSnackbarHost
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import java.io.File

class MainViewModel : ViewModel() {
    private val _savedImagePath = mutableStateOf<String?>(null)
    val savedImagePath: State<String?> get() = _savedImagePath

    private val _contentAlpha = mutableStateOf(1.0f)
    val contentAlpha: State<Float> get() = _contentAlpha

    fun loadImagePathFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val path = prefs.getString("background_image_path", null)
        _savedImagePath.value = path
        _contentAlpha.value = if (!path.isNullOrEmpty()) 0.4f else 1.0f
    }

    fun updateImagePath(path: String?) {
        _savedImagePath.value = path
    }

    fun updateAlpha(alpha: Float) {
        _contentAlpha.value = alpha
    }
}

class MainActivity : AppCompatActivity() {

    private var isLoading by mutableStateOf(true)

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen().setKeepOnScreenCondition { isLoading }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        setContent {
            val activity = LocalContext.current as ComponentActivity
            val viewModel: MainViewModel = viewModel(viewModelStoreOwner = activity)
            APatchTheme {
                val context = LocalContext.current
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }

                
                val contentAlpha by viewModel.contentAlpha
                val savedImagePath by viewModel.savedImagePath
                LaunchedEffect(Unit) {
                    viewModel.loadImagePathFromPrefs(context)
                }

                Scaffold(
                    bottomBar = {
                        Box(
                        modifier = Modifier
                        .graphicsLayer { alpha = contentAlpha }
                    ) {  BottomBar(navController)   }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier
                        .fillMaxSize()
                        //.padding(innerPadding)
                    ) {
                        if (!savedImagePath.isNullOrEmpty()) {
                            val imageBitmap = remember(savedImagePath) {
                            val file = File(savedImagePath)
                                if (file.exists()) {
                                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                                } else null
                            }

                            imageBitmap?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer { alpha = contentAlpha } // 设置透明度为 10%
                                )
                            }
                        }
                        CompositionLocalProvider(
                            LocalSnackbarHost provides snackBarHostState,
                        ) {
                            DestinationsNavHost(
                                modifier = Modifier
                                    .graphicsLayer { alpha = contentAlpha }
                                    .padding(bottom = 80.dp),
                                navGraph = NavGraphs.root,
                                navController = navController,
                                engine = rememberNavHostEngine(navHostContentAlignment = Alignment.TopCenter),
                                defaultTransitions = object : NavHostAnimatedDestinationStyle() {
                                    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
                                        get() = { fadeIn(animationSpec = tween(150)) }
                                    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition
                                        get() = { fadeOut(animationSpec = tween(150)) }
                                }
                            )
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
    val navigator = navController.rememberDestinationsNavigator()

    NavigationBar(tonalElevation = 8.dp) {
        BottomBarDestination.entries.forEach { destination ->
            val isCurrentDestOnBackStack by navController.isRouteOnBackStackAsState(destination.direction)

            val hideDestination = (destination.kPatchRequired && !kPatchReady) || (destination.aPatchRequired && !aPatchReady)
            if (hideDestination) return@forEach
            NavigationBarItem(selected = isCurrentDestOnBackStack, onClick = {
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
