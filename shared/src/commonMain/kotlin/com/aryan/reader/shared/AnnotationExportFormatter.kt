package com.aryan.reader.shared

import com.aryan.reader.shared.pdf.DEFAULT_SHARED_PDF_COMMENT_AUTHOR
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationComment
import com.aryan.reader.shared.pdf.visiblePdfAnnotationComments

enum class AnnotationExportFormat(
    val extension: String,
    val mimeType: String
) {
    MARKDOWN("md", "text/markdown"),
    TEXT("txt", "text/plain")
}

data class AnnotationExportDocument(
    val bookTitle: String,
    val sourceType: FileType,
    val entries: List<AnnotationExportEntry>
) {
    val hasAnnotations: Boolean get() = entries.isNotEmpty()
}

data class AnnotationExportEntry(
    val locationLabel: String,
    val highlightedText: String,
    val colorLabel: String? = null,
    val note: String? = null,
    val comments: List<AnnotationExportComment> = emptyList(),
    val sortIndex: Int = 0
)

data class AnnotationExportComment(
    val author: String,
    val contents: String,
    val depth: Int = 0,
    val sortIndex: Int = 0
)

object AnnotationExportFormatter {
    fun fromEpubBook(book: BookItem): AnnotationExportDocument {
        val entries = book.readerHighlights.toAnnotationExportEntries()
        return AnnotationExportDocument(
            bookTitle = book.exportTitle(),
            sourceType = book.type,
            entries = entries
        )
    }

    fun fromEpubHighlights(
        bookTitle: String,
        sourceType: FileType,
        highlights: List<UserHighlight>
    ): AnnotationExportDocument {
        return AnnotationExportDocument(
            bookTitle = bookTitle.exportTitleFallback(),
            sourceType = sourceType,
            entries = highlights.toAnnotationExportEntries()
        )
    }

    fun fromPdfAnnotations(
        bookTitle: String,
        sourceType: FileType = FileType.PDF,
        annotations: List<SharedPdfAnnotation>
    ): AnnotationExportDocument {
        val entries = annotations
            .asSequence()
            .filter { it.kind == PdfAnnotationKind.HIGHLIGHT }
            .mapNotNull { annotation ->
                val text = annotation.text.cleanExportText()
                val note = annotation.note.cleanExportText().takeIf { it.isNotBlank() }
                val comments = annotation.comments.toExportComments()
                if (text.isBlank() && note == null && comments.isEmpty()) return@mapNotNull null
                AnnotationExportEntry(
                    locationLabel = "Page ${annotation.pageIndex + 1}",
                    highlightedText = text,
                    colorLabel = annotation.colorArgb.toExportColorLabel(),
                    note = note,
                    comments = comments,
                    sortIndex = annotation.pageIndex
                )
            }
            .sortedWith(compareBy({ it.sortIndex }, { it.locationLabel }, { it.highlightedText }))
            .toList()
        return AnnotationExportDocument(
            bookTitle = bookTitle.exportTitleFallback(),
            sourceType = sourceType,
            entries = entries
        )
    }

    fun render(document: AnnotationExportDocument, format: AnnotationExportFormat): String {
        if (!document.hasAnnotations) return ""
        return when (format) {
            AnnotationExportFormat.MARKDOWN -> renderMarkdown(document)
            AnnotationExportFormat.TEXT -> renderText(document)
        }
    }

    fun suggestedFileName(bookTitle: String, format: AnnotationExportFormat): String {
        return "${bookTitle.exportSafeFileStem()}-annotations.${format.extension}"
    }

    private fun renderMarkdown(document: AnnotationExportDocument): String = buildString {
        appendLine("# ${document.bookTitle.markdownHeadingText()}")
        appendLine()
        appendLine("- Source: ${document.sourceType.name}")
        appendLine("- Annotations: ${document.entries.size}")
        appendLine()
        document.entries.forEachIndexed { index, entry ->
            appendLine("## Annotation ${index + 1}")
            appendLine()
            appendLine("- Location: ${entry.locationLabel}")
            entry.colorLabel?.takeIf { it.isNotBlank() }?.let { appendLine("- Color: $it") }
            if (entry.highlightedText.isNotBlank()) {
                appendLine()
                entry.highlightedText.lines().forEach { line ->
                    appendLine("> ${line.trimEnd()}")
                }
            }
            entry.note?.takeIf { it.isNotBlank() }?.let { note ->
                appendLine()
                appendLine("**Note**")
                appendLine()
                appendLine(note)
            }
            if (entry.comments.isNotEmpty()) {
                appendLine()
                appendLine("**Comments**")
                appendLine()
                entry.comments.forEach { comment ->
                    val indent = "  ".repeat(comment.depth.coerceAtLeast(0))
                    val author = comment.author.ifBlank { DEFAULT_SHARED_PDF_COMMENT_AUTHOR }
                    appendLine("$indent- **${author.markdownInlineText()}**: ${comment.contents.markdownCommentText(indent)}")
                }
            }
            if (index != document.entries.lastIndex) appendLine()
        }
    }.trimEnd() + "\n"

    private fun renderText(document: AnnotationExportDocument): String = buildString {
        appendLine(document.bookTitle)
        appendLine("Source: ${document.sourceType.name}")
        appendLine("Annotations: ${document.entries.size}")
        appendLine()
        document.entries.forEachIndexed { index, entry ->
            appendLine("${index + 1}. ${entry.locationLabel}")
            entry.colorLabel?.takeIf { it.isNotBlank() }?.let { appendLine("Color: $it") }
            if (entry.highlightedText.isNotBlank()) {
                appendLine("Highlight:")
                entry.highlightedText.lines().forEach { line -> appendLine("  $line") }
            }
            entry.note?.takeIf { it.isNotBlank() }?.let { note ->
                appendLine("Note:")
                note.lines().forEach { line -> appendLine("  $line") }
            }
            if (entry.comments.isNotEmpty()) {
                appendLine("Comments:")
                entry.comments.forEach { comment ->
                    val indent = "  " + "  ".repeat(comment.depth.coerceAtLeast(0))
                    val author = comment.author.ifBlank { DEFAULT_SHARED_PDF_COMMENT_AUTHOR }
                    appendLine("${indent}${author}:")
                    comment.contents.lines().forEach { line -> appendLine("$indent  $line") }
                }
            }
            if (index != document.entries.lastIndex) appendLine()
        }
    }.trimEnd() + "\n"

    private fun List<UserHighlight>.toAnnotationExportEntries(): List<AnnotationExportEntry> {
        return asSequence()
            .mapNotNull { highlight ->
                val text = highlight.text.cleanExportText()
                val note = highlight.note.cleanExportText().takeIf { it.isNotBlank() }
                if (text.isBlank() && note == null) return@mapNotNull null
                val chapterNumber = highlight.chapterIndex + 1
                val page = highlight.locator.pageIndex?.let { ", Page ${it + 1}" }.orEmpty()
                AnnotationExportEntry(
                    locationLabel = "Chapter $chapterNumber$page",
                    highlightedText = text,
                    colorLabel = highlight.colorArgb?.toExportColorLabel() ?: highlight.color.id,
                    note = note,
                    sortIndex = highlight.chapterIndex
                )
            }
            .sortedWith(compareBy({ it.sortIndex }, { it.locationLabel }, { it.highlightedText }))
            .toList()
    }

    private fun List<SharedPdfAnnotationComment>.toExportComments(): List<AnnotationExportComment> {
        val visible = visiblePdfAnnotationComments()
        if (visible.isEmpty()) return emptyList()
        val byParent = visible.groupBy { it.parentId }
        val ids = visible.map { it.id }.toSet()
        val roots = visible
            .filter { it.parentId == null || it.parentId !in ids }
            .sortedByComment()
        val emitted = mutableSetOf<String>()
        val result = mutableListOf<AnnotationExportComment>()

        fun append(comment: SharedPdfAnnotationComment, depth: Int) {
            if (!emitted.add(comment.id)) return
            result += AnnotationExportComment(
                author = comment.author.trim().ifBlank { DEFAULT_SHARED_PDF_COMMENT_AUTHOR },
                contents = comment.contents.cleanExportText(),
                depth = depth,
                sortIndex = comment.createdAt.takeIf { it > 0L }?.toInt() ?: result.size
            )
            byParent[comment.id].orEmpty().sortedByComment().forEach { child -> append(child, depth + 1) }
        }

        roots.forEach { append(it, 0) }
        visible.sortedByComment().forEach { append(it, 0) }
        return result
    }

    private fun List<SharedPdfAnnotationComment>.sortedByComment(): List<SharedPdfAnnotationComment> {
        return sortedWith(compareBy({ it.createdAt.takeIf { timestamp -> timestamp > 0L } ?: Long.MAX_VALUE }, { it.id }))
    }
}

private fun BookItem.exportTitle(): String = (title ?: displayName).exportTitleFallback()

private fun String?.exportTitleFallback(): String = cleanExportText().ifBlank { "Untitled" }

private fun String?.cleanExportText(): String {
    if (this == null) return ""
    return replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .trim()
}

private fun String.markdownHeadingText(): String {
    return replace("#", "\\#").cleanExportText().ifBlank { "Untitled" }
}

private fun String.markdownInlineText(): String {
    return cleanExportText()
        .replace("\\", "\\\\")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("`", "\\`")
        .replace("[", "\\[")
        .replace("]", "\\]")
}

private fun String.markdownCommentText(indent: String): String {
    val lines = cleanExportText().lines()
    if (lines.isEmpty()) return ""
    return lines.mapIndexed { index, line ->
        if (index == 0) line else "\n$indent  $line"
    }.joinToString("")
}

private fun String.exportSafeFileStem(): String {
    return exportTitleFallback()
        .replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)
        .ifBlank { "book" }
}

private fun Int.toExportColorLabel(): String {
    val rgb = this and 0x00FFFFFF
    return "#${rgb.toString(16).padStart(6, '0').uppercase()}"
}
