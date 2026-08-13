package com.aryan.reader.pdf

enum class LinkSource {
    ANNOTATION,
    TEXT_CONTENT,
}

data class PdfIntBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top

    fun contains(x: Int, y: Int): Boolean {
        return x >= left && x < right && y >= top && y < bottom
    }
}

data class PageLink(
    val highlightBounds: PdfIntBounds,
    val tapBounds: PdfIntBounds,
    val url: String?,
    val destPageIdx: Int?,
    val source: LinkSource,
)

fun buildPdfPageLink(
    highlightBounds: PdfIntBounds,
    verticalTapPaddingPx: Int,
    url: String?,
    destPageIdx: Int?,
    source: LinkSource,
): PageLink? {
    if (!isActionablePdfLinkTarget(url, destPageIdx)) return null
    if (highlightBounds.width <= 0 || highlightBounds.height <= 0) return null
    return PageLink(
        highlightBounds = highlightBounds,
        tapBounds = PdfIntBounds(
            left = highlightBounds.left,
            top = highlightBounds.top - verticalTapPaddingPx,
            right = highlightBounds.right,
            bottom = highlightBounds.bottom + verticalTapPaddingPx,
        ),
        url = url,
        destPageIdx = destPageIdx,
        source = source,
    )
}

fun isActionablePdfLinkTarget(url: String?, destPageIdx: Int?): Boolean {
    return url != null || (destPageIdx != null && destPageIdx >= 0)
}

fun pdfRenderPageId(
    documentKey: String,
    pageIndex: Int,
    pageIdentity: PdfPageIdentity?,
): String {
    val sourcePageId = when (pageIdentity) {
        is PdfPageIdentity.Blank -> "BLANK_${pageIdentity.id}"
        is PdfPageIdentity.Pdf -> "PDF_${pageIdentity.pdfIndex}"
        null -> "PDF_$pageIndex"
    }
    return "$documentKey:$sourcePageId"
}
