package com.aryan.reader.shared.docparse

import com.aryan.reader.shared.reader.SharedXmlDocumentContent
import com.aryan.reader.shared.reader.SharedXmlDocumentNode
import com.aryan.reader.shared.reader.decodeEpubEntities
import com.aryan.reader.shared.reader.parseSharedXmlDocument

/**
 * Portable ODT/FODT parser producing the same chapters and HTML as Android's
 * `OdtParser`: style-aware bold/italic/underline/strikethrough spans,
 * `text:h` headings, lists rendered as `<ul>`, tables, footnote spans, MathML
 * pass-through, and 64 KB chapter splitting ("Part N"). Zip-archive images keep
 * their archive href (`Pictures/…`) for platform embedding; flat FODT images
 * become data URIs directly.
 */
internal object SharedOdtDocumentParser {

    data class Chapter(
        val title: String,
        val html: String,
    )

    data class Result(
        val title: String,
        val author: String,
        val chapters: List<Chapter>,
        /** Archive image paths referenced by chapters (zip ODT only). */
        val imagePaths: List<String>,
    )

    private const val MaxChapterHtmlChars = 64 * 1024

    fun parse(
        contentXml: String,
        stylesXml: String?,
        isFlat: Boolean,
        fileNameHint: String,
    ): Result? {
        val contentRoot = parseSharedXmlDocument(contentXml) ?: return null
        val styles = mutableMapOf<String, StyleProps>()
        stylesXml?.let { stylesXmlText ->
            parseSharedXmlDocument(stylesXmlText)?.let { stylesRoot -> collectStyles(stylesRoot, styles) }
        }
        collectStyles(contentRoot, styles)

        val state = ParseState(fileNameHint, styles, isFlat)
        state.walkChildren(contentRoot)
        state.saveChapter()
        if (state.chapters.isEmpty()) return null
        return Result(
            title = state.title,
            author = state.author,
            chapters = state.chapters,
            imagePaths = state.imagePaths.distinct(),
        )
    }

    internal data class StyleProps(
        var isBold: Boolean = false,
        var isItalic: Boolean = false,
        var isStrikethrough: Boolean = false,
        var isUnderline: Boolean = false,
    )

    private class ParseState(
        fileNameHint: String,
        val styles: MutableMap<String, StyleProps>,
        val isFlat: Boolean,
    ) {
        var title: String = fileNameHint.substringBeforeLast('.')
        var author: String = "Unknown"
        val chapters = mutableListOf<Chapter>()
        val imagePaths = mutableListOf<String>()

        val chapterHtml = StringBuilder()
        var chapterCount = 0
        var inOfficeBinaryData = false
        val binaryDataBase64 = StringBuilder()
        var flatImageHref: String? = null

        fun saveChapter() {
            if (chapterHtml.isEmpty()) return
            chapterCount++
            chapters += Chapter(title = "Part $chapterCount", html = chapterHtml.toString())
            chapterHtml.clear()
        }
    }

    private fun collectStyles(root: SharedXmlDocumentNode, styles: MutableMap<String, StyleProps>) {
        root.descendants().forEach { node ->
            when (node.localName) {
                "style" -> {
                    node.attributeByLocalName("name")?.let { name ->
                        styles[name] = StyleProps()
                        applyTextProperties(node, styles[name]!!)
                    }
                }
                // Standalone text-properties under styles whose parent was seen earlier
                // (tree order makes the style:name parent arrive first).
                else -> Unit
            }
        }
    }

    private fun applyTextProperties(styleNode: SharedXmlDocumentNode, props: StyleProps) {
        val textProperties = styleNode.firstDescendant("text-properties") ?: return
        if (textProperties.attributeByLocalName("font-weight") == "bold") props.isBold = true
        if (textProperties.attributeByLocalName("font-style") == "italic") props.isItalic = true
        textProperties.attributeByLocalName("text-line-through-style")?.let {
            if (it != "none") props.isStrikethrough = true
        }
        textProperties.attributeByLocalName("text-underline-style")?.let {
            if (it != "none") props.isUnderline = true
        }
    }

    private fun ParseState.walk(node: SharedXmlDocumentNode) {
        val name = node.localName

        when (name) {
            "title" -> {
                val value = node.textContent().trim()
                if (value.isNotBlank()) title = value
            }
            "creator" -> {
                val value = node.textContent().trim()
                if (value.isNotBlank()) author = value
            }
            "style" -> {
                node.attributeByLocalName("name")?.let { styleName ->
                    styles[styleName] = StyleProps()
                    applyTextProperties(node, styles[styleName]!!)
                }
            }
            "h" -> {
                val level = node.attributeByLocalName("outline-level")?.toIntOrNull() ?: 2
                val tag = "h${level.coerceIn(1, 6)}"
                chapterHtml.append("<$tag>")
                walkChildren(node)
                chapterHtml.append("</$tag>\n")
            }
            "p" -> {
                chapterHtml.append("<p>")
                walkChildren(node)
                chapterHtml.append("</p>\n")
                if (chapterHtml.length >= MaxChapterHtmlChars) saveChapter()
            }
            "span" -> {
                val props = node.attributeByLocalName("style-name")?.let(styles::get)
                val opened = mutableListOf<String>()
                if (props != null) {
                    if (props.isBold) { chapterHtml.append("<b>"); opened += "b" }
                    if (props.isItalic) { chapterHtml.append("<i>"); opened += "i" }
                    if (props.isUnderline) { chapterHtml.append("<u>"); opened += "u" }
                    if (props.isStrikethrough) { chapterHtml.append("<s>"); opened += "s" }
                }
                walkChildren(node)
                opened.reversed().forEach { chapterHtml.append("</$it>") }
            }
            "a" -> {
                val href = node.attributeByLocalName("href").orEmpty()
                chapterHtml.append("<a href=\"${href.escapeHtmlAttribute()}\">")
                walkChildren(node)
                chapterHtml.append("</a>")
            }
            "list" -> {
                chapterHtml.append("<ul>\n")
                walkChildren(node)
                chapterHtml.append("</ul>\n")
            }
            "list-item" -> {
                chapterHtml.append("<li>")
                walkChildren(node)
                chapterHtml.append("</li>\n")
            }
            "table" -> {
                chapterHtml.append("<table>")
                walkChildren(node)
                chapterHtml.append("</table>\n")
            }
            "table-row" -> {
                chapterHtml.append("<tr>")
                walkChildren(node)
                chapterHtml.append("</tr>\n")
            }
            "table-cell" -> {
                val colspan = node.attributeByLocalName("number-columns-spanned") ?: "1"
                val rowspan = node.attributeByLocalName("number-rows-spanned") ?: "1"
                chapterHtml.append("<td colspan=\"$colspan\" rowspan=\"$rowspan\">")
                walkChildren(node)
                chapterHtml.append("</td>\n")
            }
            "line-break" -> {
                chapterHtml.append("<br/>")
            }
            "tab" -> {
                chapterHtml.append("&nbsp;&nbsp;&nbsp;&nbsp;")
            }
            "note" -> {
                chapterHtml.append("<span class=\"footnote\">[Note: ")
                walkChildren(node)
                chapterHtml.append("]</span>")
            }
            "math" -> {
                // MathML pass-through exactly like Android's inMath pull-parser state:
                // raw descendant markup, unescaped text.
                chapterHtml.append("<math xmlns=\"http://www.w3.org/1998/Math/MathML\">")
                node.content.forEach { part ->
                    when (part) {
                        is SharedXmlDocumentContent.Text -> chapterHtml.append(part.value.toString())
                        is SharedXmlDocumentContent.Child -> chapterHtml.append(serializeMathSubtree(part.node))
                    }
                }
                chapterHtml.append("</math>")
            }
            "image" -> {
                val href = node.attributeByLocalName("href") ?: return
                if (isFlat) {
                    flatImageHref = href
                    // The base64 payload arrives in the sibling office:binary-data child.
                    walkChildren(node)
                } else {
                    imagePaths += href
                    chapterHtml.append("<img src=\"${href.escapeHtmlAttribute()}\" />")
                }
            }
            "binary-data" -> {
                if (isFlat) {
                    inOfficeBinaryData = true
                    binaryDataBase64.clear()
                    walkChildren(node)
                    inOfficeBinaryData = false
                    appendFlatImage()
                }
            }
            else -> walkChildren(node)
        }
    }

    private fun ParseState.walkChildren(node: SharedXmlDocumentNode) {
        node.content.forEach { part ->
            when (part) {
                is SharedXmlDocumentContent.Text -> {
                    if (inOfficeBinaryData) {
                        binaryDataBase64.append(part.value)
                    } else {
                        appendBodyText(part.value.toString())
                    }
                }
                is SharedXmlDocumentContent.Child -> walk(part.node)
            }
        }
    }

    private fun serializeMathSubtree(node: SharedXmlDocumentNode): String = buildString {
        append("<${node.name}")
        node.attributes.forEach { (key, value) ->
            append(" $key=\"${value.replace("\"", "&quot;")}\"")
        }
        append(">")
        node.content.forEach { part ->
            when (part) {
                is SharedXmlDocumentContent.Text -> append(part.value.toString())
                is SharedXmlDocumentContent.Child -> append(serializeMathSubtree(part.node))
            }
        }
        append("</${node.name}>")
    }

    /** Mirrors Android's flat-ODT image naming: reuse the href basename when usable. */
    private fun ParseState.appendFlatImage() {
        val base64 = binaryDataBase64.toString()
        val extension = flatImageHref?.substringAfterLast('/', "")
        ?.substringAfterLast('\\', "")
        ?.substringAfterLast('.', "")
        ?.lowercase()
            ?.takeIf { it.isNotBlank() && it.length <= 12 && it.all(Char::isLetterOrDigit) }
            ?: "png"
        val mimeType = when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/png"
        }
        chapterHtml.append("<img src=\"data:$mimeType;base64,")
        chapterHtml.append(base64.filterNot { it.isWhitespace() })
        chapterHtml.append("\" />")
        flatImageHref = null
    }

    private fun ParseState.appendBodyText(raw: String) {
        val text = raw.decodeEpubEntities()
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        // Preserve single spaces required between words and inline formatting.
        if (text.isNotEmpty()) chapterHtml.append(text)
    }

    private fun String.escapeHtmlAttribute(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
