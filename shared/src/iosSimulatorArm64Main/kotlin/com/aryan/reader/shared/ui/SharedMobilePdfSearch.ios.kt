@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
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
)

private var iosPdfSearchCacheKey: IosPdfSearchCacheKey? = null
private var iosPdfSearchIndex: com.aryan.reader.shared.pdf.SharedPdfSearchIndex? = null

internal actual suspend fun searchSharedMobilePdf(
    book: BookItem,
    query: String,
    password: String?,
): List<SharedPdfSearchResult> = withContext(Dispatchers.Default) {
    val resolvedPath = book.path.resolvedIosPdfPath() ?: return@withContext emptyList()
    if (!NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)) {
        return@withContext emptyList()
    }

    IosPdfiumRuntime.mutex.withLock {
        IosPdfiumRuntime.ensureInitialized()
        val cacheKey = IosPdfSearchCacheKey(
            path = resolvedPath,
            fileSize = book.fileSize,
            modifiedAt = book.fileContentModifiedTimestamp,
            passwordHash = password?.hashCode() ?: 0,
        )
        if (iosPdfSearchCacheKey == cacheKey) {
            iosPdfSearchIndex?.let { return@withLock it.search(query) }
        }

        val document = FPDF_LoadDocument(resolvedPath, password) ?: return@withLock emptyList()
        try {
        val pageCount = FPDF_GetPageCount(document)
        val index = com.aryan.reader.shared.pdf.SharedPdfSearchIndex(pageCount)

        for (pageIndex in 0 until pageCount) {
            currentCoroutineContext().ensureActive()
            val page = FPDF_LoadPage(document, pageIndex)
            if (page == null) {
                index.putPage(pageIndex, "")
                continue
            }
            try {
                val textPage = FPDFText_LoadPage(page)
                if (textPage == null) {
                    index.putPage(pageIndex, "")
                    continue
                }
                try {
                    val charCount = FPDFText_CountChars(textPage)
                    if (charCount > 0) {
                        memScoped {
                            val buffer = allocArray<UShortVar>(charCount + 1)
                            val written = FPDFText_GetText(textPage, 0, charCount, buffer)
                            if (written > 0) {
                                val chars = CharArray(written) { index ->
                                    buffer[index].toInt().toChar()
                                }
                                index.putPage(pageIndex, chars.concatToString().trimEnd('\u0000'))
                            } else {
                                index.putPage(pageIndex, "")
                            }
                        }
                    } else {
                        index.putPage(pageIndex, "")
                    }
                } finally {
                    FPDFText_ClosePage(textPage)
                }
            } finally {
                FPDF_ClosePage(page)
            }
        }

            iosPdfSearchCacheKey = cacheKey
            iosPdfSearchIndex = index
            index.search(query)
        } finally {
            FPDF_CloseDocument(document)
        }
    }
}
