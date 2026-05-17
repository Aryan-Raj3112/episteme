package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

internal object SharedUiTokens {
    val chromeRadius = 6.dp
    val surfaceRadius = 8.dp
    val screenPadding = 20.dp
    val panelPadding = 14.dp
    val compactGap = 8.dp
    val contentGap = 12.dp
    val sidebarWidth = 220.dp
}

@Composable
internal fun sharedSubtleBorder(alpha: Float = 0.38f): BorderStroke {
    return BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha))
}
