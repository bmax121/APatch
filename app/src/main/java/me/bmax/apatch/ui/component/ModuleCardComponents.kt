package me.bmax.apatch.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.bmax.apatch.R

@Composable
fun ModuleUpdateButton(
    onClick: () -> Unit
) = FilledTonalButton(
    onClick = onClick, enabled = true, contentPadding = PaddingValues(12.dp)
) {
    Icon(
        modifier = Modifier.size(20.dp),
        painter = painterResource(id = R.drawable.device_mobile_down),
        contentDescription = stringResource(id = R.string.apm_update)
    )
}

@Composable
fun ModuleRemoveButton(
    enabled: Boolean, onClick: () -> Unit
) = FilledTonalButton(
    onClick = onClick, enabled = enabled, contentPadding = PaddingValues(12.dp)
) {
    Icon(
        modifier = Modifier.size(20.dp),
        painter = painterResource(id = R.drawable.trash),
        contentDescription = stringResource(id = R.string.apm_remove)
    )
}

@Composable
fun KPModuleRemoveButton(
    enabled: Boolean, onClick: () -> Unit
) = FilledTonalButton(
    onClick = onClick, enabled = enabled, contentPadding = PaddingValues(12.dp)
) {
    Icon(
        modifier = Modifier.size(20.dp),
        painter = painterResource(id = R.drawable.trash),
        contentDescription = stringResource(id = R.string.kpm_unload)
    )
}

@Composable
fun ModuleStateIndicator(
    @DrawableRes icon: Int, color: Color = MaterialTheme.colorScheme.outline
) {
    Image(
        modifier = Modifier.requiredSize(150.dp),
        painter = painterResource(id = icon),
        contentDescription = null,
        alpha = 0.1f,
        colorFilter = ColorFilter.tint(color)
    )
}