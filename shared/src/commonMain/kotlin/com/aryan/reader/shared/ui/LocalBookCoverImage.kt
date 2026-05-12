package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun LocalBookCoverImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier
)
