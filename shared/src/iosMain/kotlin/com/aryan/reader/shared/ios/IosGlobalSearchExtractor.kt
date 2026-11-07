@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.pdf.IosPdfiumRuntime
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageCount
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDFText_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDFText_CountChars
import com.aryan.reader.shared.pdfium.c.FPDFText_GetText
import com.aryan.reader.shared.pdfium.c.FPDFText_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDF_DOCUMENT
import com.aryan.reader.shared.search.SharedGlobalSearchPolicy
import com.aryan.reader.shared.search.SharedSearchChunk
import com.aryan.reader.shared.search.SharedSearchChunkIndexer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Native-text extraction feeding the library inside-files index on iOS.
 * Mirrors Android's `GlobalSearchIndexWorker.extractNativeChunks`: the same
 * parsers the reader uses, so the index agrees with what readers find on
 * the page. Never OCR: image PDFs extract zero pages and persist a
 * NO_NATIVE_TEXT tombstone via the session.
 *
 * - PDF: Pdfium native text pages only (no bitmap render, no Vision).
 * - EPUB/MOBI/FB2/DOCX/DOC/ODT/FODT/MD/TXT/HTML/PPTX: [loadIosEpubBook]
 *   chapters' plain text (PPTX is one chapter per slide, so slides land on
 *   the chapter axis exactly like Android's slide-text feeder).
 * - Comics/audiobook/unknown: empty (policy gate, tombstoned upstream).
 *
 * Throwing means deterministically unparseable (DRM, truncated file); the
 * session maps it to a tombstone rather than retrying forever.
 */
internal suspend fun extractIosGlobalSearchChunks(book: BookItem): List<SharedSearchChunk> {
    if (!SharedGlobalSearchPolicy.isContentSearchable(book.type)) return emptyList()
    return when (book.type) {
        FileType.PDF -> SharedSearchChunkIndexer.fromPdfPageTexts(extractIosPdfNativePageTexts(book))
        FileType.CBZ, FileType.CBR, FileType.CB7, FileType.CBT,
        FileType.AUDIOBOOK, FileType.UNKNOWN -> emptyList()
        else -> withContext(Dispatchers.Default) {
            // Same loader the reader opens, so MOBI/DOCX/ODT/PPTX/plain
            // text-likes index byte-identically to what the reader shows.
            // Heavy (zip inflate + parse + disk cache): stays off Main.
            val loaded = loadIosEpubBook(book)
            loaded.chapters.flatMapIndexed { chapterIndex, chapter ->
                currentCoroutineContext().ensureActive()
                SharedSearchChunkIndexer.fromPlainText(chapter.plainText, chapterIndex)
            }
        }
    }
}

/** Pdfium native text per page, in order. Blank pages contribute "". */
private suspend fun extractIosPdfNativePageTexts(book: BookItem): List<String> {
    // Scope must span the whole document read (resolve + every page),
    // mirroring the in-reader search lock.
    val scope = acquireScopeForBookPath(book.path)
    try {
        return extractIosPdfNativePageTextsScoped(book)
    } finally {
        scope?.close()
    }
}

private suspend fun extractIosPdfNativePageTextsScoped(book: BookItem): List<String> {
    val resolvedPath = book.path.resolveIosReadablePath() ?: error("PDF path is unavailable")
    return IosPdfiumRuntime.withPdfium {
        val document = FPDF_LoadDocument(resolvedPath, null) ?: error("Cannot open PDF document")
        try {
            val pageCount = FPDF_GetPageCount(document)
            buildList {
                for (pageIndex in 0 until pageCount) {
                    currentCoroutineContext().ensureActive()
                    add(extractIosPdfiumPageText(document, pageIndex))
                }
            }
        } finally {
            FPDF_CloseDocument(document)
        }
    }
}

private suspend fun extractIosPdfiumPageText(
    document: FPDF_DOCUMENT,
    pageIndex: Int,
): String {
    val page = FPDF_LoadPage(document, pageIndex) ?: return ""
    try {
        val textPage = FPDFText_LoadPage(page) ?: return ""
        try {
            val charCount = FPDFText_CountChars(textPage)
            if (charCount <= 0) return ""
            return memScoped {
                val buffer = allocArray<UShortVar>(charCount + 1)
                val written = FPDFText_GetText(textPage, 0, charCount, buffer)
                if (written <= 0) {
                    ""
                } else {
                    CharArray(written) { i -> buffer[i].toInt().toChar() }
                        .concatToString()
                        .trimEnd('\u0000')
                }
            }
        } finally {
            FPDFText_ClosePage(textPage)
        }
    } finally {
        FPDF_ClosePage(page)
    }
}
