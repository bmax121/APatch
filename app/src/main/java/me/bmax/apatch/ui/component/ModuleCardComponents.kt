package me.bmax.apatch.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    onClick = onClick, enabled = true, contentPadding = PaddingValues(horizontal = 12.dp)
) {
    Icon(
        modifier = Modifier.size(20.dp),
        painter = painterResource(id = R.drawable.device_mobile_down),
        contentDescription = null
    )

    Spacer(modifier = Modifier.width(6.dp))
    Text(
        text = stringResource(id = R.string.apm_update),
        maxLines = 1,
        overflow = TextOverflow.Visible,
        softWrap = false
    )
}

@Composable
fun IconTextButton(
    iconRes: Int,
    textRes: Int,
    showText: Boolean? = null,
    onClick: () -> Unit
) {
    val finalShowText = showText ?: true

    FilledTonalButton(
        onClick = onClick,
        enabled = true,
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(id = iconRes),
                contentDescription = null
            )
            if (finalShowText) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(id = textRes),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun ModuleRemoveButton(
    enabled: Boolean, onClick: () -> Unit
) = FilledTonalButton(
    onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp)
) {
    Icon(
        modifier = Modifier.size(20.dp),
        painter = painterResource(id = R.drawable.trash),
        contentDescription = null
    )

    Spacer(modifier = Modifier.width(6.dp))
    Text(
        text = stringResource(id = R.string.apm_remove),
        maxLines = 1,
        overflow = TextOverflow.Visible,
        softWrap = false
    )
}

@Composable
fun KPModuleRemoveButton(
    enabled: Boolean, onClick: () -> Unit
) = FilledTonalButton(
    onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp)
) {
    Icon(
        modifier = Modifier.size(20.dp),
        painter = painterResource(id = R.drawable.trash),
        contentDescription = null
    )

    Spacer(modifier = Modifier.width(6.dp))
    Text(
        text = stringResource(id = R.string.kpm_unload),
        maxLines = 1,
        overflow = TextOverflow.Visible,
        softWrap = false
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