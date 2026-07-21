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

internal data class SharedMobilePdfTileRender(
    val request: com.aryan.reader.shared.pdf.PdfZoomTileRequest,
    val bitmap: ImageBitmap
)

internal const val DefaultSharedMobilePdfPageAspectRatio = 0.72f

@Composable
internal expect fun rememberSharedMobilePdfPageRender(
    book: BookItem,
    pageIndex: Int,
    zoomScale: Float = 1f
): SharedMobilePdfPageRender

@Composable
internal expect fun rememberSharedMobilePdfTileRenders(
    book: BookItem,
    pageIndex: Int,
    pageAspectRatio: Float,
    zoomScale: Float,
    visibleBounds: com.aryan.reader.shared.pdf.PdfPageBounds?
): List<SharedMobilePdfTileRender>
