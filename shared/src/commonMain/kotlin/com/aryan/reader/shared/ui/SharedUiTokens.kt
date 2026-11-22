package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

internal object SharedUiTokens {
    val chromeRadius = 6.dp
    /**
     * Content panels (desktop library, settings panes). Kept as an alias so the legacy name keeps
     * working, but its value follows the expressive card radius: an 8dp panel behind 20dp cards on
     * the same screen was the clearest sign the two halves of the app were designed apart.
     */
    val surfaceRadius = 20.dp
    val screenPadding = 20.dp
    val panelPadding = 14.dp
    val compactGap = 8.dp
    val contentGap = 12.dp
    val sidebarWidth = 220.dp

    /**
     * M3 expressive corner scale. `large` increased is 20dp and `extra large` increased is 32dp;
     * the library uses them so cards and the continue-reading hero read as one family instead of
     * the flat 16/28dp baseline pair.
     */
    val cardRadius = 20.dp
    val heroRadius = 32.dp
}

@Composable
internal fun sharedSubtleBorder(alpha: Float = 0.55f): BorderStroke {
    return BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha))
}
