@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.SharedPdfSearchEngine
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageCount
import com.aryan.reader.shared.pdfium.c.FPDF_InitLibrary
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
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

private var searchLibraryInitialized = false

private fun ensureSearchLibraryInitialized() {
    if (!searchLibraryInitialized) {
        FPDF_InitLibrary()
        searchLibraryInitialized = true
    }
}

private fun String?.resolvedIosPdfPath(): String? {
    val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (!value.startsWith("file://")) return value
    return NSURL.URLWithString(value)?.path ?: value.removePrefix("file://")
}

internal actual suspend fun searchSharedMobilePdf(
    book: BookItem,
    query: String
): List<SharedPdfSearchResult> {
    val resolvedPath = book.path.resolvedIosPdfPath() ?: return emptyList()
    if (!NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)) {
        return emptyList()
    }

    ensureSearchLibraryInitialized()

    val document = FPDF_LoadDocument(resolvedPath, null) ?: return emptyList()
    try {
        val pageCount = FPDF_GetPageCount(document)
        val pageTexts = mutableListOf<String>()

        for (pageIndex in 0 until pageCount) {
            val page = FPDF_LoadPage(document, pageIndex)
            if (page == null) {
                pageTexts.add("")
                continue
            }
            try {
                val textPage = FPDFText_LoadPage(page)
                if (textPage == null) {
                    pageTexts.add("")
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
                                pageTexts.add(chars.concatToString().trimEnd('\u0000'))
                            } else {
                                pageTexts.add("")
                            }
                        }
                    } else {
                        pageTexts.add("")
                    }
                } finally {
                    FPDFText_ClosePage(textPage)
                }
            } finally {
                FPDF_ClosePage(page)
            }
        }

        return SharedPdfSearchEngine.search(pageTexts, query)
    } finally {
        FPDF_CloseDocument(document)
    }
}
