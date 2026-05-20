package com.aryan.reader.pdf

import android.graphics.Rect
import com.aryan.reader.pdf.data.VirtualPage

enum class LinkSource {
    ANNOTATION, TEXT_CONTENT
}

data class PageLink(
    val highlightBounds: Rect,
    val tapBounds: Rect,
    val url: String?,
    val destPageIdx: Int?,
    val source: LinkSource
)

internal fun pdfRenderPageId(documentKey: String, pageIndex: Int, virtualPage: VirtualPage?): String {
    val sourcePageId = when (virtualPage) {
        is VirtualPage.BlankPage -> "BLANK_${virtualPage.id}"
        is VirtualPage.PdfPage -> "PDF_${virtualPage.pdfIndex}"
        null -> "PDF_$pageIndex"
    }
    return "$documentKey:$sourcePageId"
}
