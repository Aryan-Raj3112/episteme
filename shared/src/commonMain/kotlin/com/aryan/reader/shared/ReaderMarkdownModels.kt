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

/**
 * Android parity (MarkdownParser.parse(...).text): plain speakable/display
 * text for AI markdown without syntax markers. Block order and separators
 * mirror the parser above so TTS chunks and highlight ranges line up with
 * [SharedMarkdownText] rendering.
 */
fun readerMarkdownPlainText(markdown: String): String {
    val document = ReaderMarkdownParser.parse(markdown)
    if (document.blocks.isEmpty()) return markdown.trim()
    return document.blocks.joinToString("\n\n") { block ->
        when (block) {
            is ReaderMarkdownBlock.Heading -> block.text.stripMarkdownInline()
            is ReaderMarkdownBlock.Paragraph -> block.text.stripMarkdownInline()
            is ReaderMarkdownBlock.Quote -> block.text.stripMarkdownInline()
            is ReaderMarkdownBlock.CodeBlock -> block.text
            is ReaderMarkdownBlock.ListItems -> block.items.joinToString("\n") {
                it.stripMarkdownInline()
            }
        }
    }.trim()
}

private fun String.stripMarkdownInline(): String {
    var text = this
    // Links [label](url) -> label (mirror SharedMarkdownText inline parsing).
    while (true) {
        val open = text.indexOf('[')
        val mid = if (open >= 0) text.indexOf("](", open + 1) else -1
        val close = if (mid > open) text.indexOf(')', mid + 2) else -1
        if (open < 0 || mid < 0 || close < 0) break
        text = text.substring(0, open) + text.substring(open + 1, mid) + text.substring(close + 1)
    }
    text = text.replace("**", "").replace("`", "")
    // Single-asterisk italics: strip delimiter asterisks, keep inner text.
    val out = StringBuilder()
    var index = 0
    while (index < text.length) {
        if (text[index] == '*') {
            val end = text.indexOf('*', index + 1)
            if (end > index + 1) {
                out.append(text.substring(index + 1, end))
                index = end + 1
            } else {
                out.append(text[index])
                index += 1
            }
        } else {
            out.append(text[index])
            index += 1
        }
    }
    return out.toString().replace(Regex("\\s+"), " ").trim()
}
