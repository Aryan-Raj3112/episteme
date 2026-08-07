package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import com.aryan.reader.shared.BookItem

@Composable
internal actual fun rememberSharedMobilePdfPageRender(
    book: BookItem,
    pageIndex: Int,
    zoomScale: Float,
    password: String?,
): SharedMobilePdfPageRender = SharedMobilePdfPageRender()

@Composable
internal actual fun rememberSharedMobilePdfPageThumbnail(
    book: BookItem,
    pageIndex: Int,
    password: String?,
): SharedMobilePdfPageThumbnail = SharedMobilePdfPageThumbnail()

@Composable
internal actual fun rememberSharedMobilePdfTileRenders(
    book: BookItem,
    pageIndex: Int,
    pageAspectRatio: Float,
    zoomScale: Float,
    visibleBounds: com.aryan.reader.shared.pdf.PdfPageBounds?,
    password: String?,
): List<SharedMobilePdfTileRender> = emptyList()

internal actual suspend fun sharedMobilePdfOcrTextBounds(
    book: BookItem,
    pageIndex: Int,
    password: String?,
): List<com.aryan.reader.shared.pdf.PdfPageBounds> = emptyList()
