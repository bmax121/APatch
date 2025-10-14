package me.bmax.apatch.util.ui

import androidx.compose.foundation.layout.*
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