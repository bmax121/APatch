package me.bmax.apatch.util.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun NavigationBarsSpacer(
    modifier: Modifier = Modifier
) {
    val paddingValues = WindowInsets.navigationBars.asPaddingValues()

    Box(
        modifier = Modifier.padding(paddingValues)
    ) {
        Spacer(modifier = modifier)
    }
}