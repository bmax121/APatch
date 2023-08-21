package me.bmax.akpatch.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import me.bmax.akpatch.R
import kotlin.math.max

internal val DialogMinWidth = 280.dp
internal val DialogMaxWidth = 560.dp

// Paddings for each of the dialog's parts.
private val DialogPadding = PaddingValues(all = 16.dp)
private val IconPadding = PaddingValues(bottom = 16.dp)
private val TitlePadding = PaddingValues(bottom = 16.dp)
private val TextPadding = PaddingValues(bottom = 24.dp)


@Composable
fun AlertDialogContent(
    buttons: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    input: MutableState<String>,
    icon: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    text: @Composable (() -> Unit)?,
    label: String?,
    placeholder: String?,
    shape: Shape,
    tonalElevation: Dp,
    buttonContentColor: Color = AlertDialogDefaults.containerColor,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
) {
    val inputText = remember { mutableStateOf("") }
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        tonalElevation = tonalElevation,
    ) {
        Column(
            modifier = Modifier.padding(DialogPadding)
        ) {
            icon?.let {
                CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                    Box(Modifier.padding(IconPadding)
                            .align(Alignment.CenterHorizontally)
                    ) { icon() }
                }
            }

            title?.let {
                CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                    val textStyle: TextStyle =  TextStyle(fontSize = 24.sp,)
                    ProvideTextStyle(textStyle) {
                        Box(
                            // Align the title to the center when an icon is present.
                            Modifier.padding(TitlePadding)
                                .align(
                                    if (icon == null) {
                                        Alignment.Start
                                    } else {
                                        Alignment.CenterHorizontally
                                    }
                                )
                        ) { title() }
                    }
                }
            }
            text?.let {
                CompositionLocalProvider(LocalContentColor provides textContentColor) {
                    val textStyle: TextStyle =  TextStyle(fontSize = 12.sp,)
                    ProvideTextStyle(textStyle) {
                        Box(Modifier.weight(weight = 1f, fill = false)
                                .padding(TextPadding)
                                .align(Alignment.Start)
                        ) { text() }
                    }
                }
            }
            TextField(
                value = inputText.value,
                onValueChange = {
                        newText -> inputText.value = newText
                        input.value = newText },
                label = { Text(text = label ?: "") },
                placeholder = { Text(text = placeholder ?: "") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Ascii
                ),
                keyboardActions = KeyboardActions(
                    onDone = {}
                )
            )
            Box(modifier = Modifier.align(Alignment.End)) {
                CompositionLocalProvider(LocalContentColor provides buttonContentColor) {
                    val textStyle: TextStyle =  TextStyle(fontSize = 24.sp,)
                    ProvideTextStyle(value = textStyle, content = buttons)
                }
            }
        }
    }
}

@Composable
internal fun AlertDialogFlowRow(
    mainAxisSpacing: Dp,
    crossAxisSpacing: Dp,
    content: @Composable () -> Unit
) {
    Layout(content) { measurables, constraints ->
        val sequences = mutableListOf<List<Placeable>>()
        val crossAxisSizes = mutableListOf<Int>()
        val crossAxisPositions = mutableListOf<Int>()
        var mainAxisSpace = 0
        var crossAxisSpace = 0
        val currentSequence = mutableListOf<Placeable>()
        var currentMainAxisSize = 0
        var currentCrossAxisSize = 0
        fun canAddToCurrentSequence(placeable: Placeable) =
            currentSequence.isEmpty() || currentMainAxisSize + mainAxisSpacing.roundToPx() +
                    placeable.width <= constraints.maxWidth
        fun startNewSequence() {
            if (sequences.isNotEmpty()) {
                crossAxisSpace += crossAxisSpacing.roundToPx()
            }
            sequences += currentSequence.toList()
            crossAxisSizes += currentCrossAxisSize
            crossAxisPositions += crossAxisSpace
            crossAxisSpace += currentCrossAxisSize
            mainAxisSpace = max(mainAxisSpace, currentMainAxisSize)
            currentSequence.clear()
            currentMainAxisSize = 0
            currentCrossAxisSize = 0
        }
        for (measurable in measurables) {
            val placeable = measurable.measure(constraints)
            if (!canAddToCurrentSequence(placeable)) startNewSequence()
            if (currentSequence.isNotEmpty()) {
                currentMainAxisSize += mainAxisSpacing.roundToPx()
            }
            currentSequence.add(placeable)
            currentMainAxisSize += placeable.width
            currentCrossAxisSize = max(currentCrossAxisSize, placeable.height)
        }
        if (currentSequence.isNotEmpty()) startNewSequence()
        val mainAxisLayoutSize = max(mainAxisSpace, constraints.minWidth)
        val crossAxisLayoutSize = max(crossAxisSpace, constraints.minHeight)
        val layoutWidth = mainAxisLayoutSize
        val layoutHeight = crossAxisLayoutSize
        layout(layoutWidth, layoutHeight) {
            sequences.forEachIndexed { i, placeables ->
                val childrenMainAxisSizes = IntArray(placeables.size) { j ->
                    placeables[j].width +
                            if (j < placeables.lastIndex) mainAxisSpacing.roundToPx() else 0
                }
                val arrangement = Arrangement.End
                val mainAxisPositions = IntArray(childrenMainAxisSizes.size) { 0 }
                with(arrangement) {
                    arrange(mainAxisLayoutSize, childrenMainAxisSizes,
                        layoutDirection, mainAxisPositions)
                }
                placeables.forEachIndexed { j, placeable ->
                    placeable.place(
                        x = mainAxisPositions[j],
                        y = crossAxisPositions[i]
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    input: MutableState<String>,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    label: String? = null,
    placeholder: String? = null,
    shape: Shape = AlertDialogDefaults.shape,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) = androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    properties = properties
) {
    AlertDialogContent(
        buttons = {
            AlertDialogFlowRow(
                mainAxisSpacing = 10.dp,
                crossAxisSpacing = 10.dp
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        },
        icon = icon,
        title = title,
        text = text,
        label = label,
        placeholder = placeholder,
        shape = shape,
        input = input,
        tonalElevation = tonalElevation,
    )
}
