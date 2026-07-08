package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.aryan.reader.shared.BookItem

internal data class SharedMobilePdfPageRender(
    val pageCount: Int = 1,
    val bitmap: ImageBitmap? = null,
    val errorMessage: String? = null
)

@Composable
internal expect fun rememberSharedMobilePdfPageRender(
    book: BookItem,
    pageIndex: Int
): SharedMobilePdfPageRender
