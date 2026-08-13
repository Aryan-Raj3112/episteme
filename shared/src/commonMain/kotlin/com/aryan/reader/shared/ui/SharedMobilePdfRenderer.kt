package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.aryan.reader.shared.BookItem

internal data class SharedMobilePdfPageRender(
    val pageCount: Int = 1,
    /** The rendered page's width divided by its height. */
    val aspectRatio: Float = DefaultSharedMobilePdfPageAspectRatio,
    val bitmap: ImageBitmap? = null,
    val errorMessage: String? = null,
    val openError: SharedMobilePdfOpenError? = null,
)

internal enum class SharedMobilePdfOpenError {
    PASSWORD_REQUIRED,
    INVALID_DOCUMENT,
}

internal fun sharedMobilePdfOpenErrorForPdfiumCode(code: Long): SharedMobilePdfOpenError =
    if (code == 4L) {
        SharedMobilePdfOpenError.PASSWORD_REQUIRED
    } else {
        SharedMobilePdfOpenError.INVALID_DOCUMENT
    }

internal data class SharedMobilePdfTileRender(
    val request: com.aryan.reader.shared.pdf.PdfZoomTileRequest,
    val bitmap: ImageBitmap
)

internal data class SharedMobilePdfPageThumbnail(
    val bitmap: ImageBitmap? = null,
    val aspectRatio: Float = DefaultSharedMobilePdfPageAspectRatio,
)

/** Splits display pages into fixed-width grid rows, mirroring Android's PAGES drawer. */
internal fun sharedPdfThumbnailRows(pageCount: Int, perRow: Int = 3): List<List<Int>> =
    (0 until pageCount.coerceAtLeast(0)).chunked(perRow.coerceAtLeast(1))

/** The grid row containing [pageIndex], for the drawer's Locate action. */
internal fun sharedPdfThumbnailRowFor(pageIndex: Int, perRow: Int = 3): Int =
    pageIndex.coerceAtLeast(0) / perRow.coerceAtLeast(1)

internal const val DefaultSharedMobilePdfPageAspectRatio = 0.72f

@Composable
internal expect fun rememberSharedMobilePdfPageRender(
    book: BookItem,
    pageIndex: Int,
    zoomScale: Float = 1f,
    password: String? = null,
): SharedMobilePdfPageRender

@Composable
internal expect fun rememberSharedMobilePdfPageThumbnail(
    book: BookItem,
    pageIndex: Int,
    password: String? = null,
): SharedMobilePdfPageThumbnail

@Composable
internal expect fun rememberSharedMobilePdfTileRenders(
    book: BookItem,
    pageIndex: Int,
    pageAspectRatio: Float,
    zoomScale: Float,
    visibleBounds: com.aryan.reader.shared.pdf.PdfPageBounds?,
    password: String? = null,
): List<SharedMobilePdfTileRender>

/**
 * OCR fallback for the highlight-all overlay (Android's ML Kit `OcrHelper`
 * path in PdfPageComposable.kt): line bounding boxes in normalized,
 * top-left-origin page coordinates. Returns an empty list when OCR is
 * unavailable or finds no text.
 */
internal expect suspend fun sharedMobilePdfOcrTextBounds(
    book: BookItem,
    pageIndex: Int,
    password: String? = null,
): List<com.aryan.reader.shared.pdf.PdfPageBounds>
