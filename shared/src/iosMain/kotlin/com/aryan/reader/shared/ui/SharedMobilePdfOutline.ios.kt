@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.PdfTocEntry
import com.aryan.reader.shared.pdf.IosPdfiumRuntime
import com.aryan.reader.shared.pdfium.c.FPDFBookmark_GetDest
import com.aryan.reader.shared.pdfium.c.FPDFBookmark_GetAction
import com.aryan.reader.shared.pdfium.c.FPDFAction_GetDest
import com.aryan.reader.shared.pdfium.c.FPDFAction_GetType
import com.aryan.reader.shared.pdfium.c.FPDFBookmark_GetFirstChild
import com.aryan.reader.shared.pdfium.c.FPDFBookmark_GetNextSibling
import com.aryan.reader.shared.pdfium.c.FPDFBookmark_GetTitle
import com.aryan.reader.shared.pdfium.c.FPDFDest_GetDestPageIndex
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.PDFACTION_GOTO
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

internal actual suspend fun loadSharedMobilePdfOutline(book: BookItem): List<PdfTocEntry> {
    val path = book.path?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
        if (value.startsWith("file://")) NSURL.URLWithString(value)?.path ?: value.removePrefix("file://") else value
    } ?: return emptyList()
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return emptyList()
    return withContext(Dispatchers.Main) { IosPdfiumRuntime.mutex.withLock {
        IosPdfiumRuntime.ensureInitialized()
        val document = FPDF_LoadDocument(path, null) ?: return@withLock emptyList()
        try {
        val result = mutableListOf<PdfTocEntry>()
        fun titleOf(bookmark: com.aryan.reader.shared.pdfium.c.FPDF_BOOKMARK): String = memScoped {
            val byteCount = FPDFBookmark_GetTitle(bookmark, null, 0u)
            if (byteCount <= 2u) return@memScoped ""
            val bytes = allocArray<ByteVar>(byteCount.toInt())
            FPDFBookmark_GetTitle(bookmark, bytes, byteCount)
            CharArray(byteCount.toInt() / 2 - 1) { index ->
                val low = bytes[index * 2].toInt() and 0xFF
                val high = bytes[index * 2 + 1].toInt() and 0xFF
                ((high shl 8) or low).toChar()
            }.concatToString().trim()
        }
        var visitedCount = 0
        fun appendChildren(parent: com.aryan.reader.shared.pdfium.c.FPDF_BOOKMARK?, level: Int) {
            if (level > 64 || visitedCount >= 10_000) return
            var bookmark = FPDFBookmark_GetFirstChild(document, parent)
            while (bookmark != null) {
                if (++visitedCount > 10_000) return
                // Resolve the sibling while this bookmark is still the active PDFium handle.
                // This also avoids consulting a parent handle again after walking its children.
                val nextSibling = FPDFBookmark_GetNextSibling(document, bookmark)
                val title = titleOf(bookmark)
                val dest = FPDFBookmark_GetDest(document, bookmark)
                    ?: FPDFBookmark_GetAction(bookmark)?.let { action ->
                        // PDFium only permits FPDFAction_GetDest for local GoTo actions. Calling
                        // it for URI, launch, remote or unsupported actions is undefined native
                        // behavior and can surface as EXC_BAD_ACCESS on nested outlines.
                        if (FPDFAction_GetType(action) == PDFACTION_GOTO.toULong()) {
                            FPDFAction_GetDest(document, action)
                        } else {
                            null
                        }
                    }
                val pageIndex = dest?.let { FPDFDest_GetDestPageIndex(document, it) } ?: -1
                val appended = title.isNotBlank() && pageIndex >= 0
                if (appended) result += PdfTocEntry(title, pageIndex, level)
                // PDF outlines may use structural parents with no destination. Do not leave
                // their navigable children at an orphaned depth that the drawer cannot reveal.
                appendChildren(bookmark, if (appended) level + 1 else level)
                bookmark = nextSibling
            }
        }
        appendChildren(null, 0)
            result
        } finally {
            FPDF_CloseDocument(document)
        }
    } }
}
