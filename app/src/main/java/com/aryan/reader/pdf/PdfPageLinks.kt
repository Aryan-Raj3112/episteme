package com.aryan.reader.pdf

import android.graphics.Rect
import com.aryan.reader.pdf.data.VirtualPage

internal fun pdfRenderPageId(documentKey: String, pageIndex: Int, virtualPage: VirtualPage?): String {
    return pdfRenderPageId(
        documentKey = documentKey,
        pageIndex = pageIndex,
        pageIdentity = when (virtualPage) {
            is VirtualPage.BlankPage -> PdfPageIdentity.Blank(virtualPage.id)
            is VirtualPage.PdfPage -> PdfPageIdentity.Pdf(virtualPage.pdfIndex)
            null -> null
        },
    )
}

internal fun Rect.toPdfIntBounds(): PdfIntBounds = PdfIntBounds(left, top, right, bottom)
