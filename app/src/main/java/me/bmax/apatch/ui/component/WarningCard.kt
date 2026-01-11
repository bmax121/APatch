package me.bmax.apatch.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun WarningCard(
    message: String,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null
) {
    val cardColors = CardDefaults.cardColors(
        containerColor = color ?: MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
        disabledContentColor = MaterialTheme.colorScheme.onErrorContainer
    )

    ElevatedCard(
        colors = cardColors,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(onClick?.let { Modifier.clickable { it() } } ?: Modifier)
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart).padding(end = 40.dp)
            ) {
                if (icon != null) {
                    icon()
                } else {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }


            if (onClose != null) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(android.R.string.cancel),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.clickable {
                        onClose()
                    }.size(18.dp).align(Alignment.TopEnd)
                )
            }
        }
    }
}

@Preview
@Composable
private fun WarningCardPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WarningCard(message = "Warning message")
        WarningCard(message = "Warning message", onClose = {})
        WarningCard(
            message = "Warning message ",
            MaterialTheme.colorScheme.outlineVariant,
        ) {}
    }
}