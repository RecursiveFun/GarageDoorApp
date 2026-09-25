package com.garagedoor.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

/** Keep content and controls above system bars (edge-to-edge safe areas). */
fun Modifier.screenInsetsPadding(): Modifier = composed {
    val status = WindowInsets.statusBars.asPaddingValues()
    val nav = WindowInsets.navigationBars.asPaddingValues()
    padding(
        start = 16.dp,
        end = 16.dp,
        top = status.calculateTopPadding() + 8.dp,
        bottom = nav.calculateBottomPadding() + 16.dp,
    )
}

/** Extra bottom padding for screens with a fixed bottom button row. */
@Composable
fun navigationBarBottomPadding(): androidx.compose.ui.unit.Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
