package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.PdfTextPageSession

/**
 * Opens a [PdfTextPageSession] for [pageIndex] of [book.path].
 *
 * The returned session must be released via [PdfTextPageSession.close] when
 * the calling Composable leaves the composition. On iOS the session is backed
 * by PDFium's text-page API and returns `null` when the document can't be
 * opened (no path, missing file, encrypted PDF without password, etc.).
 *
 * Platforms without a text-page backend (Android currently uses its own
 * `PdfPageComposable` machinery and does not call this expect) may return `null`.
 */
@Composable
expect fun rememberPdfTextPageSession(
    book: BookItem,
    pageIndex: Int,
    password: String? = null,
): PdfTextPageSession?
