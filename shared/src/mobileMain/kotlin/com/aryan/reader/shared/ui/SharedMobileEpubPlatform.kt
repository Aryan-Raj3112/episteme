package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.reader.SharedEpubBook

internal data class SharedMobileEpubLoadState(
    val isLoading: Boolean = true,
    val book: SharedEpubBook? = null,
    val errorMessage: String? = null
)

@Composable
internal expect fun rememberSharedMobileEpubLoadState(book: BookItem): SharedMobileEpubLoadState

@Composable
internal expect fun SharedMobileEpubWebView(
    html: String,
    contentChunks: List<String>,
    appearanceScript: String,
    navigationScript: String?,
    navigationRequestId: Long,
    onBridgeMessage: (method: String, payload: String) -> Unit,
    modifier: Modifier = Modifier
)

internal expect fun openSharedMobileEpubExternalLink(url: String): Boolean
