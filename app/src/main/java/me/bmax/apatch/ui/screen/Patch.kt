package me.bmax.apatch.ui.screen

import android.net.Uri
import android.system.Os
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.internal.NOPList
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.R
import me.bmax.apatch.TAG
import me.bmax.apatch.apApp
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.StringBuilder
import java.security.SecureRandom
import java.util.*
import java.util.zip.ZipInputStream


@Composable
@Destination
fun PatchScreen(navigator: DestinationsNavigator, uri: Uri, superKey: String) {
    var text by remember { mutableStateOf("") }
    var showFloatAction by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (text.isNotEmpty()) {
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val ret = patchBootimg(uri, superKey, object : CallbackList<String>() {
                override fun onAddElement(e: String?) {
                    e ?: return
                    text += e
                    Log.d(TAG, e)
                    text += '\n'
                }
            })
            if(ret) {
//                showFloatAction = true
            }
        }
    }

    Scaffold(
        topBar = { TopBar(
            onBack = {
                navigator.popBackStack()
            }) },
        floatingActionButton = {
            if (showFloatAction) {
                val reboot = stringResource(id = R.string.reboot)
                ExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
//                                reboot()
                            }
                        }
                    },
                    icon = { Icon(Icons.Filled.Refresh, reboot) },
                    text = { Text(text = reboot) },
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize(1f)
                .padding(innerPadding)
                .verticalScroll(scrollState),
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                text = text,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            )
            LaunchedEffect(text) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onBack: () -> Unit = {}) {
    TopAppBar(
        title = { Text("Patch") },
        navigationIcon = {
            IconButton(
                onClick = onBack
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
        }
    )
}

inline fun <In : InputStream, Out : OutputStream> withStreams(
    inStream: In,
    outStream: Out,
    withBoth: (In, Out) -> Unit
) {
    inStream.use { reader ->
        outStream.use { writer ->
            withBoth(reader, writer)
        }
    }
}

private val cr get() = apApp.contentResolver

fun Uri.inputStream() = cr.openInputStream(this) ?: throw FileNotFoundException()

fun Uri.outputStream() = cr.openOutputStream(this, "rwt") ?: throw FileNotFoundException()

fun Uri.fileDescriptor(mode: String) = cr.openFileDescriptor(this, mode) ?: throw FileNotFoundException()

fun InputStream.copyAndClose(out: OutputStream) = withStreams(this, out) { i, o -> i.copyTo(o) }
fun InputStream.writeTo(file: File) = copyAndClose(file.outputStream())

private fun InputStream.copyAndCloseOut(out: OutputStream) = out.use { copyTo(it) }


private val shell = Shell.getShell()
private fun String.fsh() = ShellUtils.fastCmd(shell, this)
private fun Array<String>.fsh() = ShellUtils.fastCmd(shell, *this)


fun patchBootimg(uri: Uri, superKey: String, logs: MutableList<String>): Boolean {
    var installDir: ExtendedFile = FileSystemManager.getLocal().getFile(apApp.filesDir.parent, "install")
    installDir.deleteRecursively()
    installDir.mkdirs()

    val srcBoot = installDir.getChildFile("boot.img")

    // Process input file
    try {
        uri.inputStream().buffered().use { src ->
            srcBoot.also {
                src.copyAndCloseOut(it.newOutputStream())
            }
        }
    } catch (e: IOException) {
        logs.add("Copy boot image error: " + e)
        return false
    }

    val execs = listOf("libkptools.so", "libmagiskboot.so", "libbusybox.so")

    val info = apApp.applicationInfo
    var libs = File(info.nativeLibraryDir).listFiles { _, name ->
        execs.contains(name)
    } ?: emptyArray()

    for (lib in libs) {
        val name = lib.name.substring(3, lib.name.length - 3)
        Os.symlink(lib.path, "$installDir/$name")
    }

    // Extract scripts
    for (script in listOf("util_functions.sh", "boot_patch.sh", "kpimg")) {
        val dest = File(installDir, script)
        apApp.assets.open(script).writeTo(dest)
    }

    val cmds = arrayOf(
        "cd $installDir",
        "sh boot_patch.sh ${srcBoot.path} $superKey",
    )
    shell.newJob().add(*cmds).to(logs, logs).exec()

    return true
}
