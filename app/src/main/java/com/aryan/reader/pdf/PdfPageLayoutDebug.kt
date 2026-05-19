package com.aryan.reader.pdf

import com.aryan.reader.pdf.data.VirtualPage

internal const val PDF_BLANK_PAGE_PERSISTENCE_TAG = "PdfBlankPagePersist"

internal fun List<VirtualPage>.pdfLayoutDebugSummary(maxPages: Int = 16): String {
    val blankPages = filterIsInstance<VirtualPage.BlankPage>()
    val sample = take(maxPages).mapIndexed { displayIndex, page ->
        when (page) {
            is VirtualPage.PdfPage -> "$displayIndex:P${page.pdfIndex}"
            is VirtualPage.BlankPage ->
                "$displayIndex:B(${page.id.take(8)},${page.width}x${page.height},manual=${page.wasManuallyAdded})"
        }
    }.let { pages ->
        if (size > maxPages) pages + "...(+${size - maxPages})" else pages
    }

    return "size=$size pdf=${count { it is VirtualPage.PdfPage }} " +
        "blank=${blankPages.size} manualBlank=${blankPages.count { it.wasManuallyAdded }} " +
        "pages=${sample.joinToString(prefix = "[", postfix = "]")}"
}
