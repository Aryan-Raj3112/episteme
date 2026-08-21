@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.IosPdfOcrLanguagePreferences
import com.aryan.reader.shared.pdf.IosPdfOcrTextPage
import com.aryan.reader.shared.pdf.boundsForRange
import com.aryan.reader.shared.pdf.buildIosPdfOcrTextPage
import com.aryan.reader.shared.pdf.IosPdfOcrPageCache
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.IosPdfiumRuntime
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageCount
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDFText_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDFText_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDFText_CountChars
import com.aryan.reader.shared.pdfium.c.FPDFText_GetText
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

private fun String?.resolvedIosPdfPath(): String? {
    val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (!value.startsWith("file://")) return value
    return NSURL.URLWithString(value)?.path ?: value.removePrefix("file://")
}

private data class IosPdfSearchCacheKey(
    val path: String,
    val fileSize: Long,
    val modifiedAt: Long,
    val passwordHash: Int,
    val languages: List<String>,
)

private data class IosPdfSearchCacheEntry(
    val key: IosPdfSearchCacheKey,
    val index: com.aryan.reader.shared.pdf.SharedPdfSearchIndex,
    val ocrPages: Map<Int, IosPdfOcrTextPage>,
)

/**
 * Search requests can overlap (for example, a debounced query and highlight-all). Keep the
 * bounded one-document cache as one atomic entry and serialize index construction so callers
 * never observe a key/index/OCR-pages mismatch or duplicate a full-document scan.
 */
private val iosPdfSearchCacheMutex = Mutex()
private var iosPdfSearchCacheEntry: IosPdfSearchCacheEntry? = null

internal actual suspend fun searchSharedMobilePdf(
    book: BookItem,
    query: String,
    password: String?,
): List<SharedPdfSearchResult> = withContext(Dispatchers.Default) {
    iosPdfSearchCacheMutex.withLock {
        searchIosPdfLocked(book, query, password)
    }
}

private suspend fun searchIosPdfLocked(
    book: BookItem,
    query: String,
    password: String?,
): List<SharedPdfSearchResult> {
    val resolvedPath = book.path.resolvedIosPdfPath() ?: return emptyList()
    if (!NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)) {
        return emptyList()
    }

    val cacheKey = IosPdfSearchCacheKey(
        path = resolvedPath,
        fileSize = book.fileSize,
        modifiedAt = book.fileContentModifiedTimestamp,
        passwordHash = password?.hashCode() ?: 0,
        languages = IosPdfOcrLanguagePreferences.languages,
    )
    iosPdfSearchCacheEntry?.takeIf { it.key == cacheKey }?.let { cached ->
        return cached.index.search(query).withIosPdfOcrBounds(cached.ocrPages)
    }

    val indexed = IosPdfiumRuntime.mutex.withLock {
        IosPdfiumRuntime.ensureInitialized()
        val document = FPDF_LoadDocument(resolvedPath, password) ?: return@withLock null
        try {
            val pageCount = FPDF_GetPageCount(document)
            val index = com.aryan.reader.shared.pdf.SharedPdfSearchIndex(pageCount)
            val ocrCandidates = mutableListOf<Int>()

            for (pageIndex in 0 until pageCount) {
                currentCoroutineContext().ensureActive()
                val page = FPDF_LoadPage(document, pageIndex)
                if (page == null) {
                    index.putPage(pageIndex, "")
                    ocrCandidates += pageIndex
                    continue
                }
                try {
                    val textPage = FPDFText_LoadPage(page)
                    if (textPage == null) {
                        index.putPage(pageIndex, "")
                        ocrCandidates += pageIndex
                        continue
                    }
                    try {
                        val charCount = FPDFText_CountChars(textPage)
                        val pageText = if (charCount > 0) {
                            memScoped {
                                val buffer = allocArray<UShortVar>(charCount + 1)
                                val written = FPDFText_GetText(textPage, 0, charCount, buffer)
                                if (written > 0) {
                                    CharArray(written) { index -> buffer[index].toInt().toChar() }
                                        .concatToString()
                                        .trimEnd('\u0000')
                                } else {
                                    ""
                                }
                            }
                        } else ""
                        index.putPage(pageIndex, pageText)
                        if (pageText.isBlank()) ocrCandidates += pageIndex
                    } finally {
                        FPDFText_ClosePage(textPage)
                    }
                } finally {
                    FPDF_ClosePage(page)
                }
            }
            index to ocrCandidates
        } finally {
            FPDF_CloseDocument(document)
        }
    }
    val (index, ocrCandidates) = indexed ?: return emptyList()

    val ocrPages = buildMap {
        ocrCandidates.forEach { pageIndex ->
            currentCoroutineContext().ensureActive()
            val words = IosPdfOcrPageCache.getOrRecognize(
                resolvedPath,
                pageIndex,
                password,
                IosPdfOcrLanguagePreferences.languages,
            )
            val page = buildIosPdfOcrTextPage(words)
            if (page.text.isNotBlank()) {
                index.putPage(pageIndex, page.text)
                put(pageIndex, page)
            }
        }
    }
    iosPdfSearchCacheEntry = IosPdfSearchCacheEntry(cacheKey, index, ocrPages)
    return index.search(query).withIosPdfOcrBounds(ocrPages)
}

private fun List<SharedPdfSearchResult>.withIosPdfOcrBounds(
    ocrPages: Map<Int, IosPdfOcrTextPage>,
): List<SharedPdfSearchResult> = map { result ->
    val page = ocrPages[result.pageIndex]
    if (page == null) result else result.copy(
        boundsList = page.boundsForRange(result.matchIndex, result.matchLength),
    )
}
