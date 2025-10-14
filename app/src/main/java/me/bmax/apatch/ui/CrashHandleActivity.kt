package me.bmax.apatch.ui

import android.content.ClipData
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.R
import me.bmax.apatch.ui.theme.APatchTheme
import java.text.SimpleDateFormat
import java.util.*

class CrashHandleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        val appName = getString(R.string.app_name)
        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE

        val deviceBrand = Build.BRAND
        val deviceModel = Build.MODEL
        val sdkLevel = Build.VERSION.SDK_INT
        val currentDateTime = Calendar.getInstance().time
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDateTime = formatter.format(currentDateTime)

        val exceptionMessage = intent.getStringExtra("exception_message")
        val threadName = intent.getStringExtra("thread")

        val message = buildString {
            append(appName).append(" version: ").append(versionName).append(" ($versionCode)")
                .append("\n\n")
            append("Brand: ").append(deviceBrand).append("\n")
            append("Model: ").append(deviceModel).append("\n")
            append("SDK Level: ").append(sdkLevel).append("\n")
            append("Time: ").append(formattedDateTime).append("\n\n")
            append("Thread: ").append(threadName).append("\n")
            append("Crash Info: \n").append(exceptionMessage)
        }

        setContent {
            APatchTheme {
                CrashHandleScreen(message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashHandleScreen(
    message: String
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = {
        LargeTopAppBar(
            title = { Text(text = stringResource(R.string.crash_handle_title)) },
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        )
    }, floatingActionButton = {
        ExtendedFloatingActionButton(
            onClick = {
            scope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("CrashLog", message)),
                )
            }
        }, text = { Text(text = stringResource(R.string.crash_handle_copy)) }, icon = {
            Icon(
                imageVector = Icons.Outlined.ContentCopy, contentDescription = null
            )
        }, modifier = Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.End)
        )
        )
    }) {
        SelectionContainer(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(it)
                .padding(
                    start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + 56.dp + 16.dp
                )
        ) {
            Text(
                text = message, style = TextStyle(
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp
                )
            )
        }
    }
}

@Preview
@Composable
fun CrashHandleScreenPreview() {
    APatchTheme {
        CrashHandleScreen("Crash log here")
    }
}