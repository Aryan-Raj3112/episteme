package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.PdfTextPageSession

/**
 * Opens an OCR-backed text session for a scanned page on demand, matching
 * Android's PdfPageComposable long-press parity path. Returns `null` when the
 * page has a native PDFium text layer (the regular session loader supplies
 * it), when recognition is unavailable, or when recognition yields nothing.
 */
internal expect suspend fun openSharedMobilePdfOcrTextSession(
    book: BookItem,
    pageIndex: Int,
    password: String?,
): PdfTextPageSession?
