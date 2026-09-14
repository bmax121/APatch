package me.bmax.apatch.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.util.APatchKeyHelper

@Composable
fun SuperKeyDialog(onDismiss: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.superkey_login)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.superkey_login_description))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it; failed = false },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text(stringResource(R.string.super_key)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = failed,
                )
                if (failed) Text(stringResource(R.string.superkey_login_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && key.toByteArray(Charsets.UTF_8).size in 1..63,
                onClick = {
                    busy = true
                    val enteredKey = key
                    scope.launch {
                        val ready = withContext(Dispatchers.IO) {
                            Natives.nativeReady(enteredKey).also { ready ->
                                if (ready) {
                                    APatchKeyHelper.writeSPSuperKey(enteredKey)
                                    if (APatchKeyHelper.readPendingSuperKey() == enteredKey) {
                                        APatchKeyHelper.clearPendingSuperKey()
                                    }
                                }
                            }
                        }
                        busy = false
                        failed = !ready
                        if (ready) {
                            APApplication.superKey = enteredKey
                            key = ""
                            onDismiss()
                        }
                    }
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
