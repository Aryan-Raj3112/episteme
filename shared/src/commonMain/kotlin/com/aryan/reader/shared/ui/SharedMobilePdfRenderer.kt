package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.aryan.reader.shared.BookItem

internal data class SharedMobilePdfPageRender(
    val pageCount: Int = 1,
    /** The rendered page's width divided by its height. */
    val aspectRatio: Float = DefaultSharedMobilePdfPageAspectRatio,
    val bitmap: ImageBitmap? = null,
    val errorMessage: String? = null
)

internal const val DefaultSharedMobilePdfPageAspectRatio = 0.72f

@Composable
internal expect fun rememberSharedMobilePdfPageRender(
    book: BookItem,
    pageIndex: Int
): SharedMobilePdfPageRender
