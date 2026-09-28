package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.PdfTextPageSession

/**
 * Android does not use the shared OCR on-demand path: PdfPageComposable owns
 * selection OCR through ML Kit with its own ripple indicator, so the shared
 * overlay fallback stays inert here.
 */
internal actual suspend fun openSharedMobilePdfOcrTextSession(
    book: BookItem,
    pageIndex: Int,
    password: String?,
): PdfTextPageSession? = null
