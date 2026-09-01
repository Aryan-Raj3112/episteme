package com.aryan.reader.shared.docparse

import com.aryan.reader.shared.reader.SharedXmlDocumentNode
import com.aryan.reader.shared.reader.decodeEpubEntities
import com.aryan.reader.shared.reader.parseSharedXmlDocument

/**
 * Portable FB2 (FictionBook 2) parser producing the same chapter structure and
 * HTML as Android's `Fb2Parser`: one chapter per top-level `<section>`, the
 * same inline tag mapping (emphasis→i, strikethrough→s, poem/stanza/epigraph
 * wrappers, subtitle→h3, empty-line divs), and `<title>` promotion to the
 * chapter heading. Images reference FB2 `<binary id>` values; platforms embed
 * the base64 payloads as data URIs.
 */
internal object SharedFb2DocumentParser {

    data class Chapter(
        val title: String,
        val html: String,
    )

    data class Result(
        val title: String,
        val author: String,
        val coverBinaryId: String?,
        val chapters: List<Chapter>,
        val binariesById: Map<String, Binary>,
    )

    data class Binary(
        val base64: String,
        val contentType: String?,
    )

    fun parse(xml: String, fileNameHint: String): Result? {
        val root = parseSharedXmlDocument(xml) ?: return null
        val state = ParseState(fileNameHint)
        walk(root, state)
        state.saveChapter()
        if (state.chapters.isEmpty()) return null
        return Result(
            title = state.title,
            author = state.author,
            coverBinaryId = state.coverBinaryId,
            chapters = state.chapters,
            binariesById = state.binaries,
        )
    }

    private class ParseState(fileNameHint: String) {
        var title: String = fileNameHint.substringBeforeLast('.')
        var author: String = "Unknown"
        var coverBinaryId: String? = null
        val chapters = mutableListOf<Chapter>()
        val binaries = linkedMapOf<String, Binary>()

        var inBody = false
        var inTitle = false
        val titleBuilder = StringBuilder()
        val chapterHtml = StringBuilder()
        var chapterTitle = "Chapter 1"
        var chapterCount = 0

        fun saveChapter() {
            if (chapterHtml.isBlank()) return
            chapterCount++
            chapters += Chapter(
                title = chapterTitle,
                html = chapterHtml.toString(),
            )
            chapterHtml.clear()
            chapterTitle = "Chapter ${chapterCount + 1}"
        }
    }

    /** Depth-first document-order walk replicating Android's pull-parser event stream. */
    private fun walk(node: SharedXmlDocumentNode, state: ParseState) {
        val name = node.name.lowercase()
        when (name) {
            "book-title" -> {
                val value = node.textContent().decodeEpubEntities().trim()
                if (value.isNotBlank()) state.title = value
                return
            }
            "first-name", "middle-name", "last-name" -> {
                val value = node.textContent().decodeEpubEntities().trim()
                if (value.isNotBlank()) {
                    if (state.author == "Unknown") state.author = value else state.author += " $value"
                }
                return
            }
            "body" -> {
                state.inBody = true
                node.children.forEach { walk(it, state) }
                state.inBody = false
                return
            }
            "section" -> {
                if (state.inBody) {
                    state.saveChapter()
                }
                node.children.forEach { walk(it, state) }
                return
            }
            "title" -> {
                val promote = state.inBody && state.chapterHtml.isEmpty()
                if (promote) {
                    state.inTitle = true
                    state.titleBuilder.clear()
                }
                state.chapterHtml.append("<h2>")
                walkChildren(node, state)
                state.chapterHtml.append("</h2>\n")
                if (state.inTitle) {
                    val heading = state.titleBuilder.toString().replace(Regex("\\s+"), " ").trim()
                    state.chapterTitle = heading.ifBlank { "Chapter ${state.chapterCount + 1}" }
                    state.inTitle = false
                }
                return
            }
            "p", "v" -> {
                when {
                    !state.inTitle -> state.chapterHtml.append(
                        if (name == "p") "<p>" else "<p style='text-indent: 0; text-align: left;'>"
                    )
                    state.titleBuilder.isNotEmpty() -> {
                        state.titleBuilder.append(" ")
                        state.chapterHtml.append("<br>")
                    }
                }
                walkChildren(node, state)
                if (!state.inTitle) state.chapterHtml.append("</p>\n")
                return
            }
            "subtitle" -> {
                state.chapterHtml.append("<h3>")
                walkChildren(node, state)
                state.chapterHtml.append("</h3>\n")
                return
            }
            "empty-line" -> {
                when {
                    !state.inTitle -> state.chapterHtml.append("<div class='empty-line'></div>")
                    state.titleBuilder.isNotEmpty() -> {
                        state.titleBuilder.append(" ")
                        state.chapterHtml.append("<br>")
                    }
                }
                return
            }
            "strong", "emphasis", "strikethrough", "sup", "sub" -> {
                val open = when (name) {
                    "strong" -> "<b>"
                    "emphasis" -> "<i>"
                    "strikethrough" -> "<s>"
                    "sup" -> "<sup>"
                    else -> "<sub>"
                }
                val close = when (name) {
                    "strong" -> "</b>"
                    "emphasis" -> "</i>"
                    "strikethrough" -> "</s>"
                    "sup" -> "</sup>"
                    else -> "</sub>"
                }
                state.chapterHtml.append(open)
                walkChildren(node, state)
                state.chapterHtml.append(close)
                return
            }
            "epigraph" -> {
                state.chapterHtml.append("<div class='epigraph'>")
                walkChildren(node, state)
                state.chapterHtml.append("</div>\n")
                return
            }
            "cite" -> {
                state.chapterHtml.append("<blockquote class='cite'>")
                walkChildren(node, state)
                state.chapterHtml.append("</blockquote>\n")
                return
            }
            "poem", "stanza" -> {
                state.chapterHtml.append("<div class='${if (name == "poem") "poem" else "stanza"}'>")
                walkChildren(node, state)
                state.chapterHtml.append("</div>\n")
                return
            }
            "a" -> {
                val href = node.attributeByLocalName("href")
                if (!state.inTitle) {
                    state.chapterHtml.append(if (href != null) "<a href=\"${href.escapeHtmlAttribute()}\">" else "<a>")
                }
                walkChildren(node, state)
                if (!state.inTitle) state.chapterHtml.append("</a>")
                return
            }
            "image" -> {
                val href = node.attributeByLocalName("href")
                if (href != null) {
                    val id = href.removePrefix("#")
                    if (!state.inBody) {
                        if (state.coverBinaryId == null) state.coverBinaryId = id
                    } else {
                        state.chapterHtml.append("<img src=\"${id.escapeHtmlAttribute()}\" />")
                    }
                }
                return
            }
            "binary" -> {
                val id = node.attribute("id") ?: return
                val base64 = node.textContent().filterNot(Char::isWhitespace)
                if (base64.isNotEmpty()) {
                    state.binaries[id] = Binary(base64, node.attributeByLocalName("content-type"))
                    if (id == state.coverBinaryId || (state.coverBinaryId == null && id.contains("cover", ignoreCase = true))) {
                        state.coverBinaryId = id
                    }
                }
                return
            }
            else -> {
                walkChildren(node, state)
            }
        }
    }

    private fun walkChildren(node: SharedXmlDocumentNode, state: ParseState) {
        node.content.forEach { part ->
            when (part) {
                is com.aryan.reader.shared.reader.SharedXmlDocumentContent.Text -> {
                    // The XML tree keeps raw entities in text; decode them before
                    // re-escaping for HTML output.
                    val text = part.value.toString().decodeEpubEntities()
                        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    if (text.isNotBlank()) {
                        if (state.inTitle) {
                            state.titleBuilder.append(text)
                            state.chapterHtml.append(text)
                        } else if (state.inBody) {
                            state.chapterHtml.append(text)
                        }
                    }
                }
                is com.aryan.reader.shared.reader.SharedXmlDocumentContent.Child -> walk(part.node, state)
            }
        }
    }

    private fun String.escapeHtmlAttribute(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
