package com.aryan.reader.shared

data class ReaderMarkdownDocument(
    val blocks: List<ReaderMarkdownBlock>
)

sealed interface ReaderMarkdownBlock {
    data class Heading(val level: Int, val text: String) : ReaderMarkdownBlock
    data class Paragraph(val text: String) : ReaderMarkdownBlock
    data class ListItems(val ordered: Boolean, val items: List<String>) : ReaderMarkdownBlock
    data class CodeBlock(val text: String) : ReaderMarkdownBlock
    data class Quote(val text: String) : ReaderMarkdownBlock
}

object ReaderMarkdownParser {
    fun parse(markdown: String): ReaderMarkdownDocument {
        val lines = markdown.replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<ReaderMarkdownBlock>()
        val paragraph = mutableListOf<String>()
        var index = 0

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += ReaderMarkdownBlock.Paragraph(paragraph.joinToString(" ").trim())
                paragraph.clear()
            }
        }

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            when {
                trimmed.isBlank() -> {
                    flushParagraph()
                    index += 1
                }

                trimmed.startsWith("```") -> {
                    flushParagraph()
                    val code = mutableListOf<String>()
                    index += 1
                    while (index < lines.size && !lines[index].trim().startsWith("```")) {
                        code += lines[index]
                        index += 1
                    }
                    if (index < lines.size) index += 1
                    blocks += ReaderMarkdownBlock.CodeBlock(code.joinToString("\n").trimEnd())
                }

                trimmed.headingLevel() != null -> {
                    flushParagraph()
                    val level = trimmed.headingLevel() ?: 1
                    blocks += ReaderMarkdownBlock.Heading(
                        level = level,
                        text = trimmed.drop(level).trim()
                    )
                    index += 1
                }

                trimmed.startsWith(">") -> {
                    flushParagraph()
                    val quote = mutableListOf<String>()
                    while (index < lines.size && lines[index].trim().startsWith(">")) {
                        quote += lines[index].trim().removePrefix(">").trim()
                        index += 1
                    }
                    blocks += ReaderMarkdownBlock.Quote(quote.joinToString(" ").trim())
                }

                trimmed.unorderedListText() != null || trimmed.orderedListText() != null -> {
                    flushParagraph()
                    val ordered = trimmed.orderedListText() != null
                    val items = mutableListOf<String>()
                    while (index < lines.size) {
                        val itemLine = lines[index].trim()
                        val item = if (ordered) itemLine.orderedListText() else itemLine.unorderedListText()
                        if (item == null) break
                        items += item
                        index += 1
                    }
                    blocks += ReaderMarkdownBlock.ListItems(ordered = ordered, items = items)
                }

                else -> {
                    paragraph += trimmed
                    index += 1
                }
            }
        }

        flushParagraph()
        return ReaderMarkdownDocument(blocks)
    }
}

private fun String.headingLevel(): Int? {
    val count = takeWhile { it == '#' }.length
    return count.takeIf { it in 1..6 && getOrNull(it) == ' ' }
}

private fun String.unorderedListText(): String? {
    return if (length > 2 && first() in listOf('-', '*', '+') && this[1] == ' ') {
        drop(2).trim()
    } else {
        null
    }
}

private fun String.orderedListText(): String? {
    val dotIndex = indexOf('.')
    if (dotIndex <= 0 || dotIndex + 1 >= length || this[dotIndex + 1] != ' ') return null
    return take(dotIndex).takeIf { number -> number.all { it.isDigit() } }
        ?.let { drop(dotIndex + 2).trim() }
}
