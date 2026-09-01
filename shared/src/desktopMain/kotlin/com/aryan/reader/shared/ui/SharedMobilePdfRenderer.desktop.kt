package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.PdfReverseColorMode

@Composable
internal actual fun rememberSharedMobilePdfPageRender(
    book: BookItem,
    pageIndex: Int,
    zoomScale: Float,
    password: String?,
    reverseColorMode: PdfReverseColorMode,
    preserveImageColors: Boolean,
): SharedMobilePdfPageRender = SharedMobilePdfPageRender()

@Composable
internal actual fun rememberSharedMobilePdfPageThumbnail(
    book: BookItem,
    pageIndex: Int,
    password: String?,
    reverseColorMode: PdfReverseColorMode,
    preserveImageColors: Boolean,
): SharedMobilePdfPageThumbnail = SharedMobilePdfPageThumbnail()

@Composable
internal actual fun rememberSharedMobilePdfTileRenders(
    book: BookItem,
    pageIndex: Int,
    pageAspectRatio: Float,
    zoomScale: Float,
    visibleBounds: com.aryan.reader.shared.pdf.PdfPageBounds?,
    password: String?,
    reverseColorMode: PdfReverseColorMode,
    preserveImageColors: Boolean,
): List<SharedMobilePdfTileRender> = emptyList()

internal actual suspend fun sharedMobilePdfOcrTextBounds(
    book: BookItem,
    pageIndex: Int,
    password: String?,
): List<com.aryan.reader.shared.pdf.PdfPageBounds> = emptyList()
