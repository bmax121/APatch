package me.bmax.apatch.ui.component

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private const val TAG = "SearchBar"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppBar(
    title: @Composable () -> Unit,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onClearClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    dropdownContent: @Composable (() -> Unit)? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var onSearch by remember { mutableStateOf(false) }

    if (onSearch) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    BackHandler(
        enabled = onSearch,
        onBack = {
            keyboardController?.hide()
            onClearClick()
            onSearch = !onSearch
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
        }
    }

    TopAppBar(
        title = {
            Box {
                AnimatedVisibility(
                    modifier = Modifier.align(Alignment.CenterStart),
                    visible = !onSearch,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    content = { title() }
                )

                AnimatedVisibility(
                    visible = onSearch,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 2.dp,
                                bottom = 2.dp,
                                end = if (onBackClick != null) 0.dp else 14.dp
                            )
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) onSearch = true
                                Log.d(TAG, "onFocusChanged: $focusState")
                            },
                        value = searchText,
                        onValueChange = onSearchTextChange,
                        shape = RoundedCornerShape(15.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    onSearch = false
                                    keyboardController?.hide()
                                    onClearClick()
                                },
                                content = { Icon(Icons.Filled.Close, null) }
                            )
                        },
                        maxLines = 1,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions {
                            defaultKeyboardAction(ImeAction.Search)
                            keyboardController?.hide()
                            onConfirm?.invoke()
                        },
                    )
                }
            }
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    content = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
                )
            }
        },
        actions = {
            AnimatedVisibility(
                visible = !onSearch
            ) {
                IconButton(
                    onClick = { onSearch = true },
                    content = { Icon(Icons.Filled.Search, null) }
                )
            }

            dropdownContent?.invoke()

        }
    )
}

@Preview
@Composable
private fun SearchAppBarPreview() {
    var searchText by remember { mutableStateOf("") }
    SearchAppBar(
        title = { Text("Search text") },
        searchText = searchText,
        onSearchTextChange = { searchText = it },
        onClearClick = { searchText = "" }
    )
}
