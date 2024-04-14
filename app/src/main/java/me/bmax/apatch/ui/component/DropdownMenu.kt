package me.bmax.apatch.ui.component

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun ProvideMenuShape(
    value: CornerBasedShape = RoundedCornerShape(8.dp), content: @Composable () -> Unit
) = MaterialTheme(
    shapes = MaterialTheme.shapes.copy(extraSmall = value), content = content
)