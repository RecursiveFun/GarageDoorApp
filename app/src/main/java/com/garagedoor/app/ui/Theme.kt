package com.garagedoor.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BluePrimary = Color(0xFF1565C0)

@Composable
fun GarageDoorTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = BluePrimary)
    } else {
        lightColorScheme(primary = BluePrimary)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
