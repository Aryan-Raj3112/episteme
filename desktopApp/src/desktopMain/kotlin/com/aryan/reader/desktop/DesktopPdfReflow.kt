package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.pdf.SharedPdfReflowHtml
import java.io.File

private const val DesktopPdfReflowSuffix = "_reflow"

internal object DesktopPdfReflowGenerator {
    fun generateHtmlFile(
        document: DesktopPdfDocument,
        destFile: File,
        startPage: Int = 1,
        onProgress: (Float) -> Unit
    ): Boolean {
        require(document.formatLabel == "PDF") { "Only PDF documents can be converted to text view." }
        if (document.pageCount <= 0) return false

        val firstPageIndex = (startPage - 1).coerceIn(0, document.pageCount - 1)
        val totalPagesToGenerate = (document.pageCount - firstPageIndex).coerceAtLeast(1)
        val headerFooterStrings = detectRepeatingHeaderFooter(document)
        destFile.parentFile?.mkdirs()

        return runCatching {
            destFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(SharedPdfReflowHtml.buildGlobalHtmlHeader())
                for (pageIndex in firstPageIndex until document.pageCount) {
                    if (pageIndex > firstPageIndex) {
                        writer.write("\n<page-break></page-break>\n")
                    }
                    val page = DesktopPdfium.loadReflowPage(document, pageIndex)
                    writer.write(SharedPdfReflowHtml.buildPageHtml(page, headerFooterStrings))
                    if (pageIndex % 5 == 0 || pageIndex == document.pageCount - 1) {
                        val completedPages = pageIndex - firstPageIndex + 1
                        onProgress(completedPages.toFloat() / totalPagesToGenerate.toFloat())
                    }
                }
                writer.write(SharedPdfReflowHtml.buildGlobalHtmlFooter())
            }
            true
        }.getOrDefault(false)
    }

    private fun detectRepeatingHeaderFooter(document: DesktopPdfDocument): Set<String> {
        if (document.pageCount < 5) return emptySet()
        val step = maxOf(1, document.pageCount / 8)
        val samplePageLines = (0 until document.pageCount)
            .filter { it % step == 0 }
            .take(8)
            .map { pageIndex -> DesktopPdfium.loadReflowEdgeLines(document, pageIndex) }
        return SharedPdfReflowHtml.detectRepeatingHeaderFooter(samplePageLines)
    }
}

internal fun desktopPdfReflowBookId(pdfBookId: String): String = "${pdfBookId}$DesktopPdfReflowSuffix"

internal fun isDesktopPdfReflowBookId(bookId: String): Boolean = bookId.endsWith(DesktopPdfReflowSuffix)

internal fun desktopPdfReflowDisplayName(originalTitle: String): String = "$originalTitle (Text View)"

internal fun desktopPdfReflowTitle(originalTitle: String): String = "$originalTitle (Reflow)"

internal fun desktopPdfReflowGeneratedAuthor(): String = "Generated"

internal fun desktopPdfReflowFileName(pdfBookId: String, originalTitle: String): String {
    val stem = (pdfBookId.ifBlank { originalTitle })
        .toDesktopSafeFileName()
    return "${stem}$DesktopPdfReflowSuffix.html"
}

internal fun desktopPdfReflowBookItem(
    sourceBook: BookItem,
    generatedFile: File,
    nowMillis: Long,
    initialPageIndex: Int? = null
): BookItem {
    val originalTitle = sourceBook.title?.takeIf { it.isNotBlank() }
        ?: sourceBook.displayName.substringBeforeLast('.', sourceBook.displayName)
            .takeIf { it.isNotBlank() }
        ?: "Document"
    return BookItem(
        id = desktopPdfReflowBookId(sourceBook.id),
        path = generatedFile.absolutePath,
        type = FileType.HTML,
        displayName = desktopPdfReflowDisplayName(originalTitle),
        timestamp = nowMillis,
        title = desktopPdfReflowTitle(originalTitle),
        author = desktopPdfReflowGeneratedAuthor(),
        isRecent = true,
        fileSize = generatedFile.length(),
        fileContentModifiedTimestamp = generatedFile.lastModified(),
        lastPageIndex = initialPageIndex
    )
}
