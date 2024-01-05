package me.bmax.apatch.ui.screen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import me.bmax.apatch.R
import me.bmax.apatch.ui.screen.destinations.HomeScreenDestination
import me.bmax.apatch.ui.screen.destinations.SuperUserScreenDestination
import me.bmax.apatch.ui.screen.destinations.APModuleScreenDestination
import me.bmax.apatch.ui.screen.destinations.KPModuleScreenDestination

enum class BottomBarDestination(
    val direction: DirectionDestinationSpec,
    @StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector,
) {
    Home(HomeScreenDestination, R.string.home, Icons.Filled.Home, Icons.Outlined.Home),
    KModule(KPModuleScreenDestination, R.string.kpm, Icons.Filled.Build, Icons.Outlined.Build),
    SuperUser(SuperUserScreenDestination, R.string.su_title, Icons.Filled.Security, Icons.Outlined.Security),
    AModule(APModuleScreenDestination, R.string.apm, Icons.Filled.Apps, Icons.Outlined.Apps)
}
