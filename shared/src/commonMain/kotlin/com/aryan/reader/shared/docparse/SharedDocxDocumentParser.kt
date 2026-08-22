package com.aryan.reader.shared.docparse

import com.aryan.reader.shared.reader.SharedXmlDocumentContent
import com.aryan.reader.shared.reader.SharedXmlDocumentNode
import com.aryan.reader.shared.reader.decodeEpubEntities
import com.aryan.reader.shared.reader.parseSharedXmlDocument

/**
 * Portable DOCX reader producing semantic HTML comparable to Android's mammoth
 * conversion: `Heading1..6`/`Title` styles map to `h1..h6` (and start chapters,
 * like Android's downstream HTML import pipeline), bold/italic/underline/
 * strikethrough runs map to strong/em/u/s, numbered and bulleted paragraphs
 * become ordered/unordered lists using `numbering.xml`, hyperlinks resolve
 * through `document.xml.rels`, and embedded media render through a caller
 * supplied resolver (data URIs).
 */
internal object SharedDocxDocumentParser {

    data class Chapter(
        val title: String?,
        val html: String,
    )

    data class Result(
        val chapters: List<Chapter>,
    )

    /** rId → external target URL from `word/_rels/document.xml.rels`. */
    fun parseHyperlinkTargets(relsXml: String): Map<String, String> =
        parseOfficeRelationships(relsXml).filterValues { it.external }.mapValues { it.value.target }

    /** rId → internal media path (e.g. `media/image1.png`) from the relationship part. */
    fun parseMediaTargets(relsXml: String): Map<String, String> =
        parseOfficeRelationships(relsXml).filterValues { !it.external }.mapValues { it.value.target }

    private fun parseOfficeRelationships(relsXml: String): Map<String, OfficeRelationship> {
        val root = parseSharedXmlDocument(relsXml) ?: return emptyMap()
        val relationships = mutableMapOf<String, OfficeRelationship>()
        root.descendants().filter { it.localName == "relationship" }.forEach { relationship ->
            val id = relationship.attributeByLocalName("Id") ?: return@forEach
            val target = relationship.attributeByLocalName("Target") ?: return@forEach
            val external = relationship.attributeByLocalName("TargetMode").equals("External", ignoreCase = true)
            relationships[id] = OfficeRelationship(target = target, external = external)
        }
        return relationships
    }

    private data class OfficeRelationship(
        val target: String,
        val external: Boolean,
    )

    /**
     * @param hyperlinkTargets external relationship targets (see [parseHyperlinkTargets]).
     * @param mediaSrcResolver maps an embedded-media relationship id to a usable src value
     *   (typically a data URI); returning null drops the image reference.
     */
    fun parse(
        documentXml: String,
        numberingXml: String? = null,
        hyperlinkTargets: Map<String, String> = emptyMap(),
        mediaSrcResolver: (String) -> String? = { null },
    ): Result? {
        val root = parseSharedXmlDocument(documentXml) ?: return null
        val body = root.firstDescendant("body") ?: return null
        val numbering = numberingXml?.let(::parseListFormats) ?: emptyMap()
        val state = ParserState(hyperlinkTargets, mediaSrcResolver, numbering)
        body.children.forEach { state.visitBlock(it) }
        state.flushSection()
        val chapters = state.chapters
        if (chapters.isEmpty()) return null
        return Result(chapters)
    }

    private class ParserState(
        val hyperlinkTargets: Map<String, String>,
        val mediaSrcResolver: (String) -> String?,
        val listFormats: Map<String, ListFormat>,
    ) {
        val sections = mutableListOf<SectionBuilder>()
        var current: SectionBuilder = SectionBuilder(null)

        val chapters: List<Chapter>
            get() {
                val all = sections + listOfNotNull(current.takeIf { it.hasContent() })
                return all.map { Chapter(title = it.title, html = it.html.toString()) }
            }

        var openListTag: String? = null

        fun visitBlock(node: SharedXmlDocumentNode) {
            when (node.localName) {
                "p" -> visitParagraph(node)
                "tbl" -> visitTable(node)
            }
        }

        private fun closeOpenList() {
            openListTag?.let { current.html.append("</$it>\n") }
            openListTag = null
        }

        private fun ensureOpenList(tag: String) {
            if (openListTag != tag) {
                closeOpenList()
                current.html.append("<$tag>\n")
                openListTag = tag
            }
        }

        private fun visitParagraph(node: SharedXmlDocumentNode) {
            val properties = node.children.firstOrNull { it.localName == "ppr" }
            val styleName = properties
                ?.children
                ?.firstOrNull { it.localName == "pstyle" }
                ?.attributeByLocalName("val")
                ?.lowercase()
            val numberId = properties
                ?.children
                ?.firstOrNull { it.localName == "numpr" }
                ?.children
                ?.firstOrNull { it.localName == "numid" }
                ?.attributeByLocalName("val")

            if (styleName != null && styleName.startsWith("heading")) {
                closeOpenList()
                flushSection()
                val level = styleName.removePrefix("heading").trim().toIntOrNull() ?: 1
                val section = SectionBuilder(null)
                section.html.append("<h${level.coerceIn(1, 6)}>")
                visitRuns(node, section.html)
                section.html.append("</h${level.coerceIn(1, 6)}>\n")
                section.title = section.htmlText().takeIf(String::isNotBlank)
                sections += section
                current = SectionBuilder(null)
                return
            }
            if (styleName == "title") {
                closeOpenList()
                flushSection()
                val section = SectionBuilder(null)
                section.html.append("<h1>")
                visitRuns(node, section.html)
                section.html.append("</h1>\n")
                section.title = section.htmlText().takeIf(String::isNotBlank)
                sections += section
                current = SectionBuilder(null)
                return
            }

            val listFormat = numberId?.let(listFormats::get)
            if (listFormat != null) {
                ensureOpenList(if (listFormat == ListFormat.ORDERED) "ol" else "ul")
                current.html.append("<li>")
                visitRuns(node, current.html)
                current.html.append("</li>\n")
                return
            }

            closeOpenList()
            current.html.append("<p>")
            visitRuns(node, current.html)
            current.html.append("</p>\n")
        }

        private fun visitTable(node: SharedXmlDocumentNode) {
            closeOpenList()
            current.html.append("<table>")
            node.descendants().filter { it.localName == "tr" }.forEach { row ->
                current.html.append("<tr>")
                row.children.filter { it.localName == "tc" }.forEach { cell ->
                    current.html.append("<td>")
                    cell.children.forEach { inner ->
                        if (inner.localName == "p") {
                            visitRuns(inner, current.html)
                            current.html.append("<br/>")
                        }
                    }
                    current.html.append("</td>")
                }
                current.html.append("</tr>\n")
            }
            current.html.append("</table>\n")
        }

        private fun visitRuns(paragraph: SharedXmlDocumentNode, html: StringBuilder) {
            visitInlineChildren(paragraph, html, RunProperties())
        }

        private fun visitInlineChildren(
            node: SharedXmlDocumentNode,
            html: StringBuilder,
            inherited: RunProperties,
        ) {
            node.content.forEach { part ->
                when (part) {
                    is SharedXmlDocumentContent.Text -> html.append(escapeBodyText(part.value.toString()))
                    is SharedXmlDocumentContent.Child -> visitInline(part.node, html, inherited)
                }
            }
        }

        private fun visitInline(
            node: SharedXmlDocumentNode,
            html: StringBuilder,
            inherited: RunProperties,
        ) {
            when (node.localName) {
                "r" -> {
                    val props = node.children.firstOrNull { it.localName == "rpr" }?.let(::readRunProperties) ?: inherited
                    val opened = mutableListOf<String>()
                    if (props.isBold) { html.append("<strong>"); opened += "strong" }
                    if (props.isItalic) { html.append("<em>"); opened += "em" }
                    if (props.isUnderline) { html.append("<u>"); opened += "u" }
                    if (props.isStrikethrough) { html.append("<s>"); opened += "s" }
                    node.children.forEach { child ->
                        when (child.localName) {
                            "t" -> html.append(escapeBodyText(child.textContent()))
                            "br" -> html.append("<br/>")
                            "tab" -> html.append("&nbsp;&nbsp;&nbsp;&nbsp;")
                            else -> visitInline(child, html, props)
                        }
                    }
                    opened.reversed().forEach { html.append("</$it>") }
                }
                "hyperlink" -> {
                    val relationshipId = node.attributeByLocalName("id")
                    val anchor = node.attributeByLocalName("anchor")
                    val href = relationshipId?.let(hyperlinkTargets::get)
                        ?: anchor?.let { "#$it" }
                    if (href != null) {
                        html.append("<a href=\"${href.escapeHtmlAttribute()}\">")
                    }
                    node.children.forEach { child ->
                        if (child.localName == "r") visitInline(child, html, inherited)
                    }
                    if (href != null) html.append("</a>")
                }
                "drawing" -> {
                    val blip = node.firstDescendant("blip")
                    val relationshipId = blip?.attributeByLocalName("embed")
                        ?: blip?.attributeByLocalName("link")
                    val src = relationshipId?.let(mediaSrcResolver)
                    if (src != null) {
                        html.append("<img src=\"${src.escapeHtmlAttribute()}\" />")
                    }
                }
                "pict" -> {
                    val imageShape = node.firstDescendant("imagedata")
                    val relationshipId = imageShape?.attributeByLocalName("id")
                    val src = relationshipId?.let(mediaSrcResolver)
                    if (src != null) {
                        html.append("<img src=\"${src.escapeHtmlAttribute()}\" />")
                    }
                }
                else -> visitInlineChildren(node, html, inherited)
            }
        }

        private fun readRunProperties(props: SharedXmlDocumentNode): RunProperties {
            val result = RunProperties()
            props.children.forEach { child ->
                when (child.localName) {
                    "b" -> result.isBold = child.attributeByLocalName("val") != "false"
                    "i" -> result.isItalic = child.attributeByLocalName("val") != "false"
                    "u" -> result.isUnderline = child.attributeByLocalName("val") != "none"
                    "strike" -> result.isStrikethrough = child.attributeByLocalName("val") != "false"
                }
            }
            return result
        }

        fun flushSection() {
            if (current.hasContent()) {
                sections += current
            }
            current = SectionBuilder(null)
        }
    }

    private class SectionBuilder(var title: String?) {
        val html = StringBuilder()

        fun hasContent(): Boolean = html.isNotBlank()

        fun htmlText(): String {
            // Heading sections are generated as exactly one element; strip tags for the TOC title.
            return html.toString()
                .substringAfter('>', "")
                .substringBeforeLast('<', "")
                .decodeEpubEntities()
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }

    private data class RunProperties(
        var isBold: Boolean = false,
        var isItalic: Boolean = false,
        var isUnderline: Boolean = false,
        var isStrikethrough: Boolean = false,
    )

    private enum class ListFormat { BULLET, ORDERED }

    /** numId → format of its level-0 definition, resolved through abstractNum. */
    private fun parseListFormats(numberingXml: String): Map<String, ListFormat> {
        val root = parseSharedXmlDocument(numberingXml) ?: return emptyMap()
        val abstractFormats = mutableMapOf<String, ListFormat>()
        root.children.filter { it.localName == "abstractnum" }.forEach { abstractNum ->
            val id = abstractNum.attributeByLocalName("abstractNumId") ?: return@forEach
            val levelZero = abstractNum.children
                .firstOrNull { it.localName == "lvl" && it.attributeByLocalName("ilvl") == "0" }
            val format = levelZero
                ?.children
                ?.firstOrNull { it.localName == "numfmt" }
                ?.attributeByLocalName("val")
                ?.lowercase()
            abstractFormats[id] = if (format != null && format != "bullet") ListFormat.ORDERED else ListFormat.BULLET
        }
        val formats = mutableMapOf<String, ListFormat>()
        root.children.filter { it.localName == "num" }.forEach { num ->
            val numId = num.attributeByLocalName("numId") ?: return@forEach
            val abstractId = num.children
                .firstOrNull { it.localName == "abstractnumid" }
                ?.attributeByLocalName("val")
                ?: return@forEach
            abstractFormats[abstractId]?.let { formats[numId] = it }
        }
        return formats
    }

    private fun escapeBodyText(raw: String): String {
        val decoded = raw.decodeEpubEntities()
        return buildString(decoded.length) {
            decoded.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    else -> append(char)
                }
            }
        }
    }

    private fun String.escapeHtmlAttribute(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
