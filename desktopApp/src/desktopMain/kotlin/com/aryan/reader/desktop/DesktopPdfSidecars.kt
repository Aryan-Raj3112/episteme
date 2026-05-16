package com.aryan.reader.desktop

import java.io.File
import java.util.Base64

private const val DesktopPdfSearchIndexHeader = "EpistemePdfSearchIndex\t1"

internal fun desktopPdfAnnotationFile(documentPath: String): File {
    val safeName = documentPath.hashCode().toString().replace("-", "n")
    return File(desktopUserDataRoot(), "annotations/pdf_$safeName.json")
}

internal fun desktopPdfBookmarkFile(documentPath: String): File {
    val safeName = documentPath.hashCode().toString().replace("-", "n")
    return File(desktopUserDataRoot(), "annotations/pdf_${safeName}_bookmarks.json")
}

internal fun desktopPdfRichTextFile(documentPath: String): File {
    val safeName = documentPath.hashCode().toString().replace("-", "n")
    return File(desktopUserDataRoot(), "annotations/pdf_${safeName}_rich_text.json")
}

internal fun desktopPdfSearchIndexFile(documentPath: String): File {
    val safeName = documentPath.hashCode().toString().replace("-", "n")
    return File(desktopUserCacheRoot(), "search/pdf_${safeName}_text_index.tsv")
}

internal fun restoreDesktopPdfSearchIndex(document: DesktopPdfDocument, indexFile: File): Int {
    val sourceFile = File(document.path)
    val lines = runCatching { indexFile.readLines(Charsets.UTF_8) }.getOrNull() ?: return document.indexedSearchTextPageCount()
    if (lines.firstOrNull() != DesktopPdfSearchIndexHeader) return 0
    val metadata = lines
        .asSequence()
        .drop(1)
        .takeWhile { !it.startsWith("page\t") }
        .mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }
        .toMap()
    val isFresh = metadata["pathHash"] == document.path.hashCode().toString() &&
        metadata["fileSize"] == sourceFile.length().toString() &&
        metadata["lastModified"] == sourceFile.lastModified().toString() &&
        metadata["pageCount"] == document.pageCount.toString()
    if (!isFresh) return 0

    val decoder = Base64.getDecoder()
    lines.asSequence()
        .filter { it.startsWith("page\t") }
        .forEach { line ->
            val parts = line.split('\t', limit = 3)
            val pageIndex = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val text = runCatching {
                String(decoder.decode(parts.getOrNull(2).orEmpty()), Charsets.UTF_8)
            }.getOrDefault("")
            document.cacheSearchTextPage(pageIndex, text)
        }
    return document.indexedSearchTextPageCount()
}

internal fun saveDesktopPdfSearchIndex(document: DesktopPdfDocument, indexFile: File) {
    val sourceFile = File(document.path)
    val pages = document.indexedSearchPages()
    if (pages.isEmpty()) return
    val encoder = Base64.getEncoder()
    val payload = buildString {
        appendLine(DesktopPdfSearchIndexHeader)
        appendLine("pathHash\t${document.path.hashCode()}")
        appendLine("fileSize\t${sourceFile.length()}")
        appendLine("lastModified\t${sourceFile.lastModified()}")
        appendLine("pageCount\t${document.pageCount}")
        pages.forEach { page ->
            append("page\t")
            append(page.pageIndex)
            append('\t')
            appendLine(encoder.encodeToString(page.text.toByteArray(Charsets.UTF_8)))
        }
    }
    runCatching {
        indexFile.parentFile?.mkdirs()
        indexFile.writeText(payload, Charsets.UTF_8)
    }
}
