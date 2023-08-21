package me.bmax.akpatch

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.systemuicontroller.rememberSystemUiController

@Composable
fun AKPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> darkColorScheme(
//            primary = Color(0x00e600),
//            secondary = Color(0x00e600),
//            tertiary = Color(0x00e600),
        )
        else -> lightColorScheme(
//            primary =  Color(0x00e600),
//            secondary = Color(0x00e600),
//            tertiary = Color(0x00e600)
        )
    }
    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setStatusBarColor(
            color = colorScheme.surface,
            darkIcons = !darkTheme
        )
        // To match the App Navbar color
        systemUiController.setNavigationBarColor(
            color = colorScheme.surfaceColorAtElevation(8.dp),
            darkIcons = !darkTheme,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
