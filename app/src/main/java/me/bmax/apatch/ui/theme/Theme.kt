package me.bmax.apatch.ui.theme

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.systemuicontroller.rememberSystemUiController

@Composable
fun APatchTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val darkTheme = if (prefs.getBoolean("night_mode_follow_sys", true)) {
        isSystemInDarkTheme()
    } else {
        prefs.getBoolean("night_mode_enabled", false)
    }
    // Dynamic color is available on Android 12+, and custom 1t!
    val dynamicColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) prefs.getBoolean("use_system_color_theme", true) else false

    val customColorScheme = prefs.getString("custom_color", "blue")

    val colorScheme = if (!dynamicColor) {
        if (darkTheme) {
            when (customColorScheme) {
                "amber" -> DarkAmberTheme
                "blue_grey" -> DarkBlueGreyTheme
                "blue" -> DarkBlueTheme
                "brown" -> DarkBrownTheme
                "cyan" -> DarkCyanTheme
                "deep_orange" -> DarkDeepOrangeTheme
                "deep_purple" -> DarkDeepPurpleTheme
                "green" -> DarkGreenTheme
                "indigo" -> DarkIndigoTheme
                "light_blue" -> DarkLightBlueTheme
                "light_green" -> DarkLightGreenTheme
                "lime" -> DarkLimeTheme
                "orange" -> DarkOrangeTheme
                "pink" -> DarkPinkTheme
                "purple" -> DarkPurpleTheme
                "red" -> DarkRedTheme
                "sakura" -> DarkSakuraTheme
                "teal" -> DarkTealTheme
                "yellow" -> DarkYellowTheme
                else -> DarkBlueTheme
            }
        } else {
            when (customColorScheme) {
                "amber" -> LightAmberTheme
                "blue_grey" -> LightBlueGreyTheme
                "blue" -> LightBlueTheme
                "brown" -> LightBrownTheme
                "cyan" -> LightCyanTheme
                "deep_orange" -> LightDeepOrangeTheme
                "deep_purple" -> LightDeepPurpleTheme
                "green" -> LightGreenTheme
                "indigo" -> LightIndigoTheme
                "light_blue" -> LightLightBlueTheme
                "light_green" -> LightLightGreenTheme
                "lime" -> LightLimeTheme
                "orange" -> LightOrangeTheme
                "pink" -> LightPinkTheme
                "purple" -> LightPurpleTheme
                "red" -> LightRedTheme
                "sakura" -> LightSakuraTheme
                "teal" -> LightTealTheme
                "yellow" -> LightYellowTheme
                else -> LightBlueTheme
            }
        }
    } else {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkBlueTheme
            else -> LightBlueTheme
        }
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
        typography = Typography,
        content = content
    )
}
