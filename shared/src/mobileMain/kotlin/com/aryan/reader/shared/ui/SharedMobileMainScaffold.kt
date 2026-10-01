package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The main-screen shell shared by both phone hosts.
 *
 * Library Beta is the only main destination, so the shell carries no navigation bar and
 * offers no destination switching. It is retained as a single `Scaffold` so both hosts keep
 * identical window-inset behaviour now that the bar is gone.
 */
@Composable
fun SharedMobileMainScaffold(
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}
