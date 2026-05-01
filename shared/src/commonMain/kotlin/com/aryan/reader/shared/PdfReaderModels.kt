package com.aryan.reader.shared

enum class SaveMode {
    ORIGINAL,
    ANNOTATED
}

enum class SearchHighlightMode {
    FOCUSED,
    ALL
}

enum class DockLocation {
    TOP,
    BOTTOM,
    FLOATING
}

enum class PdfDisplayMode {
    PAGINATION,
    VERTICAL_SCROLL
}

data class PdfTocEntry(
    val title: String,
    val pageIndex: Int,
    val nestLevel: Int
)

data class PdfLink(
    val uri: String?,
    val destPageIndex: Int?,
    val bounds: PageRect
)

data class PagePoint(
    val x: Float,
    val y: Float
)

data class PageRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class PdfTextRect(
    val rect: PageRect
)

interface SharedReaderDocument : AutoCloseable {
    suspend fun getPageCount(): Int
    suspend fun openPage(pageIndex: Int): SharedReaderPage?
    suspend fun getTableOfContents(): List<PdfTocEntry>
}

interface SharedReaderPage : AutoCloseable {
    suspend fun getPageWidthPoint(): Int
    suspend fun getPageHeightPoint(): Int
    suspend fun getPageRotation(): Int
    suspend fun openTextPage(): SharedReaderTextPage
    suspend fun getLinks(): List<PdfLink>
}

interface SharedReaderTextPage : AutoCloseable {
    suspend fun textPageCountChars(): Int
    suspend fun textPageGetText(startIndex: Int, count: Int): String?
    suspend fun textPageGetRectsForRanges(ranges: IntArray): List<PdfTextRect>?
    suspend fun textPageGetCharIndexAtPos(x: Double, y: Double, xTolerance: Double, yTolerance: Double): Int
    suspend fun textPageGetCharBox(index: Int): PageRect?
    suspend fun textPageGetUnicode(index: Int): Int
    suspend fun loadWebLink(): SharedReaderWebLinks?
}

interface SharedReaderWebLinks : AutoCloseable {
    suspend fun countWebLinks(): Int
    suspend fun getURL(linkIndex: Int, maxLength: Int): String?
    suspend fun countRects(linkIndex: Int): Int
    suspend fun getRect(linkIndex: Int, rectIndex: Int): PageRect
}
