package com.aryan.reader.shared.reader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssParser
import com.aryan.reader.paginatedreader.OptimizedCssRules
import com.aryan.reader.paginatedreader.UserAgentStylesheet
import com.aryan.reader.paginatedreader.htmlToSemanticBlocks
import com.aryan.reader.shared.FileType
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.Charset
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipFile

object SharedJvmBookLoader {
    fun load(
        file: File,
        type: FileType,
        titleOverride: String? = null,
        authorOverride: String? = null
    ): SharedEpubBook {
        require(file.isFile) { "Missing reader file: ${file.absolutePath}" }
        val loaded = when (type) {
            FileType.EPUB -> loadEpub(file)
            FileType.HTML -> loadHtml(file)
            FileType.TXT,
            FileType.MD -> loadPlainText(file)
            FileType.FB2 -> loadFb2(file)
            FileType.DOCX -> loadDocx(file)
            FileType.ODT -> loadOdt(file, isFlat = false)
            FileType.FODT -> loadOdt(file, isFlat = true)
            FileType.MOBI -> loadMobi(file)
            else -> error("${type.name} is not supported by the shared JVM reader loader.")
        }
        return loaded.withOverrides(titleOverride = titleOverride, authorOverride = authorOverride)
    }

    fun loadEpub(file: File): SharedEpubBook {
        ZipFile(file).use { zip ->
            val container = zip.readTextOrNull("META-INF/container.xml")
            val opfPath = container
                ?.substringAfter("full-path=\"", missingDelimiterValue = "")
                ?.substringBefore("\"")
                ?.takeIf { it.isNotBlank() }
                ?: zip.entries().asSequence()
                    .map { it.name }
                    .firstOrNull { it.endsWith(".opf", ignoreCase = true) }
                ?: error("EPUB container does not point to an OPF package.")
            val opf = zip.readText(opfPath)
            val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
                .let { if (it.isBlank()) "" else "$it/" }

            val title = opf.tagText("title").ifBlank { file.nameWithoutExtension }
            val author = opf.tagText("creator").ifBlank { null }
            val manifest = parseEpubManifest(opf)
            val cssByPath = loadEpubCss(zip, manifest, basePath)
            val cssRules = parseCssRules(cssByPath)
            val spine = Regex("<itemref[^>]*idref=[\"']([^\"']+)[\"'][^>]*/?>")
                .findAll(opf)
                .mapNotNull { match -> manifest[match.groupValues[1]] }
                .toList()

            val chapterPaths = spine.ifEmpty {
                manifest.values.filter { it.endsWith(".xhtml", ignoreCase = true) || it.endsWith(".html", ignoreCase = true) }
            }

            val chapters = chapterPaths.mapIndexedNotNull { index, href ->
                val path = normalizeZipPath(basePath + href)
                val html = zip.readTextOrNull(path) ?: return@mapIndexedNotNull null
                val resourceReadyHtml = html.sanitizeReaderHtml().withEmbeddedResources(zip, path)
                val text = html.htmlToText()
                if (text.isBlank()) {
                    null
                } else {
                    chapterFromHtml(
                        id = "chapter_$index",
                        title = html.tagText("h1")
                            .ifBlank { html.tagText("h2") }
                            .ifBlank { html.tagText("title") }
                            .ifBlank { "Chapter ${index + 1}" },
                        html = resourceReadyHtml,
                        plainText = text,
                        baseHref = path,
                        cssRules = cssRules
                    )
                }
            }

            return SharedEpubBook(
                id = file.absolutePath,
                fileName = file.name,
                title = title,
                author = author,
                css = cssByPath,
                chapters = chapters.ifEmpty {
                    listOf(
                        SharedEpubChapter(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            plainText = "This EPUB opened, but no readable spine text was found by the shared JVM loader."
                        )
                    )
                }
            )
        }
    }

    private fun loadPlainText(file: File): SharedEpubBook {
        val text = file.readTextLenient()
        return SharedTextBookFactory.fromPlainText(
            id = file.absolutePath,
            fileName = file.name,
            title = file.nameWithoutExtension,
            plainText = text
        )
    }

    private fun loadHtml(file: File): SharedEpubBook {
        val html = file.readTextLenient()
        val sanitized = html.sanitizeReaderHtml()
        val title = sanitized.tagText("title").ifBlank { sanitized.tagText("h1") }.ifBlank { file.nameWithoutExtension }
        return SharedEpubBook(
            id = file.absolutePath,
            fileName = file.name,
            title = title,
            chapters = listOf(
                chapterFromHtml(
                    id = "chapter_0",
                    title = sanitized.tagText("h1").ifBlank { title },
                    html = sanitized,
                    plainText = sanitized.htmlToText().ifBlank { title },
                    baseHref = file.absolutePath,
                    cssRules = parseCssRules(emptyMap())
                )
            )
        )
    }

    private fun loadFb2(file: File): SharedEpubBook {
        val bytes = if (file.extension.equals("zip", ignoreCase = true)) {
            ZipFile(file).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull { it.name.endsWith(".fb2", ignoreCase = true) }
                    ?: error("No .fb2 file found inside the ZIP archive.")
                zip.getInputStream(entry).use { it.readBytes() }
            }
        } else {
            file.readBytes()
        }
        val parsed = parseFb2(bytes, file.nameWithoutExtension)
        return parsed.toBook(file, parseCssRules(emptyMap()))
    }

    private fun loadDocx(file: File): SharedEpubBook {
        ZipFile(file).use { zip ->
            val documentXml = zip.readBytesOrNull("word/document.xml")
                ?: error("word/document.xml not found in DOCX archive.")
            val metadata = zip.readBytesOrNull("docProps/core.xml")?.let(::parseCoreMetadata) ?: ParsedMetadata()
            val html = parseDocxBody(documentXml)
            val title = metadata.title.takeUnlessBlank() ?: file.nameWithoutExtension
            return htmlBook(
                file = file,
                title = title,
                author = metadata.author.takeUnlessBlank(),
                html = html.ifBlank { "<p>This DOCX did not contain readable text.</p>" },
                chapterTitle = title
            )
        }
    }

    private fun loadOdt(file: File, isFlat: Boolean): SharedEpubBook {
        val contentBytes: ByteArray
        val metadata: ParsedMetadata
        if (isFlat) {
            contentBytes = file.readBytes()
            metadata = parseCoreMetadata(contentBytes)
        } else {
            ZipFile(file).use { zip ->
                contentBytes = zip.readBytesOrNull("content.xml") ?: error("content.xml not found in ODT archive.")
                metadata = zip.readBytesOrNull("meta.xml")?.let(::parseCoreMetadata)
                    ?: parseCoreMetadata(contentBytes)
            }
        }

        val title = metadata.title.takeUnlessBlank() ?: file.nameWithoutExtension
        val html = parseOdtBody(contentBytes)
        return htmlBook(
            file = file,
            title = title,
            author = metadata.author.takeUnlessBlank(),
            html = html.ifBlank { "<p>This document did not contain readable text.</p>" },
            chapterTitle = title
        )
    }

    private fun loadMobi(file: File): SharedEpubBook {
        val mobi = parseMobi(file.readBytes(), file.nameWithoutExtension)
        val title = mobi.title.takeUnlessBlank() ?: file.nameWithoutExtension
        val author = mobi.author.takeUnlessBlank()
        return if (mobi.html.isNotBlank()) {
            htmlBook(
                file = file,
                title = title,
                author = author,
                html = mobi.html,
                chapterTitle = title
            )
        } else {
            SharedTextBookFactory.fromPlainText(
                id = file.absolutePath,
                fileName = file.name,
                title = title,
                plainText = mobi.text.ifBlank { "This MOBI did not contain readable text." },
                author = author
            )
        }
    }

    private fun htmlBook(
        file: File,
        title: String,
        author: String?,
        html: String,
        chapterTitle: String
    ): SharedEpubBook {
        val sanitized = html.sanitizeReaderHtml()
        return SharedEpubBook(
            id = file.absolutePath,
            fileName = file.name,
            title = title,
            author = author,
            chapters = listOf(
                chapterFromHtml(
                    id = "chapter_0",
                    title = sanitized.tagText("h1").ifBlank { sanitized.tagText("h2") }.ifBlank { chapterTitle },
                    html = sanitized,
                    plainText = sanitized.htmlToText().ifBlank { title },
                    baseHref = file.absolutePath,
                    cssRules = parseCssRules(emptyMap())
                )
            )
        )
    }

    private fun ParsedDocument.toBook(file: File, cssRules: OptimizedCssRules): SharedEpubBook {
        val safeTitle = title.takeUnlessBlank() ?: file.nameWithoutExtension
        val chapterDrafts = chapters.ifEmpty {
            listOf(
                ParsedChapter(
                    title = safeTitle,
                    html = "<p>${plainText.escapeHtml()}</p>",
                    plainText = plainText
                )
            )
        }
        return SharedEpubBook(
            id = file.absolutePath,
            fileName = file.name,
            title = safeTitle,
            author = author.takeUnlessBlank(),
            chapters = chapterDrafts.mapIndexed { index, chapter ->
                val html = chapter.html.ifBlank { "<p>${chapter.plainText.escapeHtml()}</p>" }
                chapterFromHtml(
                    id = "chapter_$index",
                    title = chapter.title.takeUnlessBlank() ?: "Chapter ${index + 1}",
                    html = html,
                    plainText = chapter.plainText.takeUnlessBlank() ?: html.htmlToText(),
                    baseHref = file.absolutePath,
                    cssRules = cssRules
                )
            }
        )
    }

    private fun chapterFromHtml(
        id: String,
        title: String,
        html: String,
        plainText: String,
        baseHref: String?,
        cssRules: OptimizedCssRules
    ): SharedEpubChapter {
        val semanticBlocks = runCatching {
            htmlToSemanticBlocks(
                html = html,
                cssRules = cssRules,
                textStyle = TextStyle(fontSize = 18.sp),
                chapterAbsPath = baseHref.orEmpty(),
                extractionBasePath = "",
                density = Density(1f),
                fontFamilyMap = emptyMap(),
                constraints = Constraints(maxWidth = 980, maxHeight = 720)
            )
        }.getOrDefault(emptyList())
        return SharedEpubChapter(
            id = id,
            title = title,
            plainText = plainText,
            semanticBlocks = semanticBlocks,
            htmlContent = html.extractBodyOrSelf(),
            baseHref = baseHref
        )
    }

    private fun parseFb2(bytes: ByteArray, fallbackTitle: String): ParsedDocument {
        val document = xmlDocument(bytes)
        val titleInfo = document.allElementsByLocalTag("title-info").firstOrNull()
        val bookTitle = titleInfo
            ?.allElementsByLocalTag("book-title")
            ?.firstOrNull()
            ?.text()
            ?.normalizeReaderWhitespace()
        val authors = titleInfo
            ?.childrenByLocalTag("author")
            ?.mapNotNull { it.fb2AuthorName() }
            ?.distinct()
            .orEmpty()
        val body = document.allElementsByLocalTag("body").firstOrNull()
        val topLevelSections = body?.childrenByLocalTag("section").orEmpty()
        val chapters = if (topLevelSections.isNotEmpty()) {
            topLevelSections.mapIndexedNotNull { index, section ->
                section.toFb2Chapter(index)
            }
        } else {
            val chapter = body?.toFb2Chapter(0)
            if (chapter == null) emptyList() else listOf(chapter)
        }
        return ParsedDocument(
            title = bookTitle.takeUnlessBlank() ?: fallbackTitle,
            author = authors.joinToString(", ").takeUnlessBlank(),
            chapters = chapters
        )
    }

    private fun parseDocxBody(bytes: ByteArray): String {
        val document = xmlDocument(bytes)
        val html = StringBuilder()
        document.allElementsByLocalTag("p").forEach { paragraph ->
            val paragraphStyle = paragraph.allElementsByLocalTag("pstyle")
                .firstOrNull()
                ?.xmlAttr("val")
            val text = StringBuilder()
            paragraph.getAllElements().forEach { element ->
                when (element.xmlTag()) {
                    "t" -> text.append(element.wholeText().escapeHtml())
                    "tab" -> text.append("    ")
                    "br" -> text.append("<br/>")
                }
            }
            val paragraphHtml = text.toString()
            if (paragraphHtml.htmlToText().isNotBlank()) {
                val tag = if (paragraphStyle.orEmpty().contains("heading", ignoreCase = true)) "h2" else "p"
                html.append("<$tag>").append(paragraphHtml).append("</$tag>\n")
            }
        }
        return html.toString()
    }

    private fun parseOdtBody(bytes: ByteArray): String {
        val document = xmlDocument(bytes)
        val body = document.allElementsByLocalTag("text").firstOrNull() ?: document
        val html = StringBuilder()
        val plain = StringBuilder()
        body.childNodes().forEach { appendOdtNode(it, html, plain) }
        return html.toString()
    }

    private fun parseCoreMetadata(bytes: ByteArray): ParsedMetadata {
        val document = xmlDocument(bytes)
        val title = document.allElementsByLocalTag("title")
            .firstOrNull()
            ?.text()
            ?.normalizeReaderWhitespace()
        val author = document.allElementsByLocalTag("creator")
            .firstOrNull()
            ?.text()
            ?.normalizeReaderWhitespace()
            ?: document.allElementsByLocalTag("initial-creator")
                .firstOrNull()
                ?.text()
                ?.normalizeReaderWhitespace()
        return ParsedMetadata(title = title, author = author)
    }

    private fun Element.toFb2Chapter(index: Int): ParsedChapter? {
        val html = StringBuilder()
        val plain = StringBuilder()
        if (xmlTag() == "section" || xmlTag() == "body") {
            childNodes().forEach { appendFb2Node(it, html, plain, headingLevel = 2) }
        } else {
            appendFb2Element(this, html, plain, headingLevel = 2)
        }
        val text = plain.toString().normalizeReaderWhitespace()
        if (text.isBlank() && html.isBlank()) return null
        val title = childrenByLocalTag("title")
            .firstOrNull()
            ?.text()
            ?.normalizeReaderWhitespace()
            .takeUnlessBlank()
            ?: "Chapter ${index + 1}"
        return ParsedChapter(
            title = title,
            html = html.toString(),
            plainText = text
        )
    }

    private fun Element.fb2AuthorName(): String? {
        return listOf("first-name", "middle-name", "last-name", "nickname")
            .mapNotNull { part ->
                childrenByLocalTag(part)
                    .firstOrNull()
                    ?.text()
                    ?.normalizeReaderWhitespace()
                    .takeUnlessBlank()
            }
            .joinToString(" ")
            .takeUnlessBlank()
    }

    private fun appendFb2Node(node: Node, html: StringBuilder, plain: StringBuilder, headingLevel: Int) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotBlank()) {
                    html.append(text.escapeHtml())
                    plain.append(text)
                }
            }
            is Element -> appendFb2Element(node, html, plain, headingLevel)
        }
    }

    private fun appendFb2Element(element: Element, html: StringBuilder, plain: StringBuilder, headingLevel: Int) {
        when (element.xmlTag()) {
            "section" -> element.childNodes().forEach {
                appendFb2Node(it, html, plain, (headingLevel + 1).coerceAtMost(6))
            }
            "title" -> {
                val tag = "h${headingLevel.coerceIn(2, 6)}"
                val text = element.text().normalizeReaderWhitespace()
                if (text.isNotBlank()) {
                    html.append("<$tag>").append(text.escapeHtml()).append("</$tag>\n")
                    plain.append(text).append('\n')
                }
            }
            "p", "v" -> appendWrappedFb2Children(element, "p", html, plain, headingLevel)
            "subtitle" -> appendWrappedFb2Children(element, "h3", html, plain, headingLevel)
            "empty-line" -> {
                html.append("<br/>")
                plain.append('\n')
            }
            "strong" -> appendWrappedFb2Children(element, "b", html, plain, headingLevel, block = false)
            "emphasis" -> appendWrappedFb2Children(element, "i", html, plain, headingLevel, block = false)
            "strikethrough" -> appendWrappedFb2Children(element, "s", html, plain, headingLevel, block = false)
            "sup" -> appendWrappedFb2Children(element, "sup", html, plain, headingLevel, block = false)
            "sub" -> appendWrappedFb2Children(element, "sub", html, plain, headingLevel, block = false)
            "poem", "stanza", "epigraph" -> appendWrappedFb2Children(element, "div", html, plain, headingLevel)
            "cite" -> appendWrappedFb2Children(element, "blockquote", html, plain, headingLevel)
            "a" -> {
                val href = element.xmlAttr("href")
                html.append(if (href.isNullOrBlank()) "<a>" else "<a href=\"${href.escapeHtmlAttribute()}\">")
                element.childNodes().forEach { appendFb2Node(it, html, plain, headingLevel) }
                html.append("</a>")
            }
            "image" -> {
                val href = element.xmlAttr("href")?.removePrefix("#").orEmpty()
                if (href.isNotBlank()) {
                    html.append("<p>").append(href.escapeHtml()).append("</p>\n")
                    plain.append(href).append('\n')
                }
            }
            else -> element.childNodes().forEach {
                appendFb2Node(it, html, plain, headingLevel)
            }
        }
    }

    private fun appendWrappedFb2Children(
        element: Element,
        tag: String,
        html: StringBuilder,
        plain: StringBuilder,
        headingLevel: Int,
        block: Boolean = true
    ) {
        html.append("<$tag>")
        element.childNodes().forEach { appendFb2Node(it, html, plain, headingLevel) }
        html.append("</$tag>")
        if (block) {
            html.append('\n')
            plain.append('\n')
        }
    }

    private fun appendOdtNode(node: Node, html: StringBuilder, plain: StringBuilder) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotBlank()) {
                    html.append(text.escapeHtml())
                    plain.append(text)
                }
            }
            is Element -> appendOdtElement(node, html, plain)
        }
    }

    private fun appendOdtElement(element: Element, html: StringBuilder, plain: StringBuilder) {
        when (element.xmlTag()) {
            "h" -> {
                val level = element.xmlAttr("outline-level")
                    ?.toIntOrNull()
                    ?.coerceIn(1, 6)
                    ?: 2
                appendOdtWrappedElement(element, "h$level", html, plain)
            }
            "p" -> appendOdtWrappedElement(element, "p", html, plain)
            "span" -> appendOdtWrappedElement(element, "span", html, plain, block = false)
            "a" -> {
                val href = element.xmlAttr("href")
                html.append(if (href.isNullOrBlank()) "<a>" else "<a href=\"${href.escapeHtmlAttribute()}\">")
                element.childNodes().forEach { appendOdtNode(it, html, plain) }
                html.append("</a>")
            }
            "list" -> appendOdtWrappedElement(element, "ul", html, plain)
            "list-item" -> appendOdtWrappedElement(element, "li", html, plain)
            "table" -> appendOdtWrappedElement(element, "table", html, plain)
            "table-row" -> appendOdtWrappedElement(element, "tr", html, plain)
            "table-cell" -> appendOdtWrappedElement(element, "td", html, plain, block = false)
            "line-break" -> {
                html.append("<br/>")
                plain.append('\n')
            }
            "tab" -> {
                html.append("&nbsp;&nbsp;&nbsp;&nbsp;")
                plain.append("    ")
            }
            else -> element.childNodes().forEach { appendOdtNode(it, html, plain) }
        }
    }

    private fun appendOdtWrappedElement(
        element: Element,
        tag: String,
        html: StringBuilder,
        plain: StringBuilder,
        block: Boolean = true
    ) {
        html.append("<$tag>")
        element.childNodes().forEach { appendOdtNode(it, html, plain) }
        html.append("</$tag>")
        if (block) {
            html.append('\n')
            plain.append('\n')
        }
    }

    private fun parseMobi(bytes: ByteArray, fallbackTitle: String): ParsedMobi {
        require(bytes.size > 86) { "Invalid MOBI/Palm database." }
        val recordCount = bytes.u16(76)
        require(recordCount > 1) { "MOBI file does not contain text records." }
        val offsets = (0 until recordCount).map { index ->
            bytes.u32(78 + index * 8).toInt()
        }.filter { it in bytes.indices }
        require(offsets.size > 1) { "MOBI file has invalid record offsets." }
        val records = offsets.mapIndexed { index, offset ->
            val end = offsets.getOrNull(index + 1) ?: bytes.size
            bytes.copyOfRange(offset, end.coerceAtLeast(offset))
        }
        val header = records.first()
        require(header.size >= 16) { "MOBI text header is missing." }

        val compression = header.u16(0)
        val textLength = header.u32(4).toInt()
        val textRecordCount = header.u16(8).coerceAtMost(records.lastIndex)
        val encryption = header.u16(12)
        require(encryption == 0) { "Encrypted MOBI files are not supported." }
        require(compression == 1 || compression == 2) {
            "MOBI compression $compression is not supported by the shared JVM loader."
        }

        val encoding = if (header.size > 32 && header.asciiAt(16, 4) == "MOBI") {
            header.u32(28).toInt()
        } else {
            1252
        }
        val charset = when (encoding) {
            65001 -> Charsets.UTF_8
            1200 -> Charsets.UTF_16
            1252 -> Charset.forName("windows-1252")
            else -> Charsets.UTF_8
        }

        val rawTextBytes = buildList {
            for (index in 1..textRecordCount) {
                val record = records.getOrNull(index) ?: continue
                add(if (compression == 2) decompressPalmDoc(record) else record)
            }
        }.flattenBytes()
            .let { if (textLength in 1 until it.size) it.copyOf(textLength) else it }

        val rawText = decodeMobiText(rawTextBytes, charset)
        val metadata = parseMobiMetadata(header, charset)
        val title = metadata.title.takeUnlessBlank() ?: fallbackTitle
        val author = metadata.author.takeUnlessBlank()
        val looksLikeHtml = rawText.contains("<html", ignoreCase = true) ||
            rawText.contains("<body", ignoreCase = true) ||
            rawText.contains("<p", ignoreCase = true)
        return ParsedMobi(
            title = title,
            author = author,
            html = if (looksLikeHtml) rawText else "",
            text = if (looksLikeHtml) rawText.htmlToText() else rawText.normalizeReaderWhitespace()
        )
    }

    private fun parseMobiMetadata(header: ByteArray, charset: Charset): ParsedMetadata {
        if (header.size < 92 || header.asciiAt(16, 4) != "MOBI") return ParsedMetadata()
        val mobiHeaderLength = header.u32(20).toInt()
        val fullNameOffset = header.u32(16 + 68).toInt()
        val fullNameLength = header.u32(16 + 72).toInt()
        val fullName = header.safeString(fullNameOffset, fullNameLength, charset)
        var exthTitle: String? = null
        var author: String? = null
        val exthOffset = 16 + mobiHeaderLength
        if (exthOffset + 12 <= header.size && header.asciiAt(exthOffset, 4) == "EXTH") {
            val recordCount = header.u32(exthOffset + 8).toInt()
            var offset = exthOffset + 12
            repeat(recordCount) {
                if (offset + 8 > header.size) return@repeat
                val type = header.u32(offset).toInt()
                val size = header.u32(offset + 4).toInt()
                if (size < 8 || offset + size > header.size) return@repeat
                val value = header.safeString(offset + 8, size - 8, charset)
                when (type) {
                    100 -> author = author ?: value
                    503 -> exthTitle = exthTitle ?: value
                }
                offset += size
            }
        }
        return ParsedMetadata(title = exthTitle.takeUnlessBlank() ?: fullName.takeUnlessBlank(), author = author)
    }

    private fun decompressPalmDoc(input: ByteArray): ByteArray {
        val output = ArrayList<Byte>(input.size * 2)
        var i = 0
        while (i < input.size) {
            val c = input[i].toInt() and 0xFF
            i += 1
            when (c) {
                0 -> output.add(0)
                in 1..8 -> {
                    repeat(c) {
                        if (i < input.size) output.add(input[i++])
                    }
                }
                in 9..0x7F -> output.add(c.toByte())
                in 0x80..0xBF -> {
                    if (i >= input.size) return output.toByteArray()
                    val pair = (c shl 8) or (input[i].toInt() and 0xFF)
                    i += 1
                    val distance = (pair shr 3) and 0x7FF
                    val length = (pair and 0x7) + 3
                    val start = output.size - distance
                    if (distance > 0 && start >= 0) {
                        repeat(length) { index ->
                            output.add(output[start + index])
                        }
                    }
                }
                else -> {
                    output.add(' '.code.toByte())
                    output.add((c xor 0x80).toByte())
                }
            }
        }
        return output.toByteArray()
    }

    private fun parseEpubManifest(opf: String): Map<String, String> {
        return Regex("<item\\s+[^>]*>").findAll(opf).mapNotNull { match ->
            val item = match.value
            val id = item.attr("id")
            val href = item.attr("href")
            if (id.isBlank() || href.isBlank()) null else id to href
        }.toMap()
    }

    private fun loadEpubCss(zip: ZipFile, manifest: Map<String, String>, basePath: String): Map<String, String> {
        return manifest.values
            .filter { it.endsWith(".css", ignoreCase = true) }
            .mapNotNull { href ->
                val path = normalizeZipPath(basePath + href)
                val css = zip.readTextOrNull(path)?.withEmbeddedCssResources(zip, path).orEmpty()
                if (css.isBlank()) null else path to css
            }
            .toMap()
    }

    private fun parseCssRules(cssByPath: Map<String, String>): OptimizedCssRules {
        val constraints = Constraints(maxWidth = 980, maxHeight = 720)
        val baseRules = CssParser.parse(
            cssContent = UserAgentStylesheet.default,
            cssPath = null,
            baseFontSizeSp = 18f,
            density = 1f,
            constraints = constraints,
            isDarkTheme = false
        ).rules

        return cssByPath.entries.fold(baseRules) { rules, (path, css) ->
            if (css.isBlank()) {
                rules
            } else {
                rules.merge(
                    CssParser.parse(
                        cssContent = css,
                        cssPath = path,
                        baseFontSizeSp = 18f,
                        density = 1f,
                        constraints = constraints,
                        isDarkTheme = false
                    ).rules
                )
            }
        }
    }

    private fun xmlDocument(bytes: ByteArray): Element {
        return ByteArrayInputStream(bytes).use { input ->
            Jsoup.parse(input, null, "", Parser.xmlParser())
        }
    }

    private fun Element.xmlTag(): String {
        return tagName().substringAfter(':').lowercase()
    }

    private fun Element.xmlAttr(name: String): String? {
        val expectedLocal = name.substringAfter(':')
        for (attribute in attributes().asList()) {
            val key = attribute.key
            if (key.equals(name, ignoreCase = true) ||
                key.substringAfter(':').equals(expectedLocal, ignoreCase = true)
            ) {
                return attribute.value.takeUnlessBlank()
            }
        }
        return null
    }

    private fun Element.allElementsByLocalTag(tag: String): List<Element> {
        return getAllElements().filter { it.xmlTag() == tag }
    }

    private fun Element.childrenByLocalTag(tag: String): List<Element> {
        return children().filter { it.xmlTag() == tag }
    }

    private fun ZipFile.readText(path: String): String {
        val entry = getEntry(path) ?: error("Missing EPUB entry: $path")
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private fun ZipFile.readTextOrNull(path: String): String? {
        val entry = getEntry(path) ?: return null
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private fun ZipFile.readBytesOrNull(path: String): ByteArray? {
        val entry = getEntry(path) ?: return null
        return getInputStream(entry).use { it.readBytes() }
    }

    private fun String.attr(name: String): String {
        return Regex("""\b$name=["']([^"']+)["']""").find(this)?.groupValues?.get(1).orEmpty()
    }

    private fun String.tagText(tag: String): String {
        return Regex("<(?:[^:>]+:)?$tag\\b[^>]*>(.*?)</(?:[^:>]+:)?$tag>", RegexOption.IGNORE_CASE)
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.htmlToText()
            .orEmpty()
    }

    private fun normalizeZipPath(path: String): String {
        val parts = ArrayDeque<String>()
        path.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun String.withEmbeddedResources(zip: ZipFile, chapterPath: String): String {
        return replace(Regex("""(?i)\b(src|href)=["']([^"']+)["']""")) { match ->
            val attr = match.groupValues[1]
            val raw = match.groupValues[2]
            if (attr.equals("href", ignoreCase = true) && !raw.looksLikeEmbeddableResource()) {
                return@replace match.value
            }
            val dataUri = zip.toDataUri(raw, chapterPath)
            if (dataUri != null) "$attr=\"$dataUri\"" else match.value
        }
    }

    private fun String.looksLikeEmbeddableResource(): Boolean {
        return substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase() in setOf("css", "jpg", "jpeg", "png", "gif", "svg", "webp", "ttf", "otf", "woff", "woff2")
    }

    private fun String.withEmbeddedCssResources(zip: ZipFile, cssPath: String): String {
        return replace(Regex("""url\((['"]?)([^)'"]+)\1\)""", RegexOption.IGNORE_CASE)) { match ->
            val raw = match.groupValues[2].trim()
            val dataUri = zip.toDataUri(raw, cssPath)
            if (dataUri != null) "url('$dataUri')" else match.value
        }
    }

    private fun ZipFile.toDataUri(rawRef: String, ownerPath: String): String? {
        val ref = rawRef.substringBefore('#').trim()
        if (ref.isBlank() || ref.startsWith("data:", ignoreCase = true)) return null
        if (ref.startsWith("http://", ignoreCase = true) || ref.startsWith("https://", ignoreCase = true)) return null
        val base = ownerPath.substringBeforeLast('/', missingDelimiterValue = "")
        val path = normalizeZipPath(if (base.isBlank()) ref else "$base/$ref")
        val entry = getEntry(path) ?: return null
        val bytes = getInputStream(entry).use { it.readBytes() }
        return "data:${mimeType(path)};base64,${Base64.getEncoder().encodeToString(bytes)}"
    }

    private fun mimeType(path: String): String {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "css" -> "text/css"
            "js" -> "text/javascript"
            else -> "application/octet-stream"
        }
    }

    private fun File.readTextLenient(): String {
        val bytes = readBytes()
        return bytes.toString(Charsets.UTF_8).takeIf { '\uFFFD' !in it }
            ?: bytes.toString(Charset.forName("windows-1252"))
    }

    private fun String.extractBodyOrSelf(): String {
        return Regex("(?is)<body\\b[^>]*>(.*?)</body>")
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: this
    }

    private fun String.htmlToText(): String {
        return Jsoup.parse(this).text().normalizeReaderWhitespace()
    }

    private fun String.sanitizeReaderHtml(): String {
        return replace(Regex("(?is)<script\\b.*?</script>"), "")
            .replace(Regex("(?is)<object\\b.*?</object>"), "")
            .replace(Regex("(?is)<embed\\b[^>]*>"), "")
            .replace(Regex("""(?i)\s+on[a-z]+\s*=\s*(['"]).*?\1"""), "")
    }

    private fun String.escapeHtml(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun String.escapeHtmlAttribute(): String {
        return escapeHtml()
    }

    private fun String.normalizeReaderWhitespace(): String {
        return replace('\u0000', ' ')
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun String?.takeUnlessBlank(): String? {
        return this?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun SharedEpubBook.withOverrides(titleOverride: String?, authorOverride: String?): SharedEpubBook {
        return copy(
            title = titleOverride.takeUnlessBlank() ?: title,
            author = authorOverride.takeUnlessBlank() ?: author
        )
    }

    private fun ByteArray.u16(offset: Int): Int {
        if (offset + 2 > size) return 0
        return ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
    }

    private fun ByteArray.u32(offset: Int): Long {
        if (offset + 4 > size) return 0
        return ((this[offset].toLong() and 0xFF) shl 24) or
            ((this[offset + 1].toLong() and 0xFF) shl 16) or
            ((this[offset + 2].toLong() and 0xFF) shl 8) or
            (this[offset + 3].toLong() and 0xFF)
    }

    private fun ByteArray.asciiAt(offset: Int, length: Int): String {
        if (offset < 0 || offset + length > size) return ""
        return copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)
    }

    private fun ByteArray.safeString(offset: Int, length: Int, charset: Charset): String? {
        if (offset < 0 || length <= 0 || offset + length > size) return null
        return copyOfRange(offset, offset + length).toString(charset)
            .trim('\u0000', ' ', '\n', '\r', '\t')
            .takeUnlessBlank()
    }

    private fun decodeMobiText(bytes: ByteArray, preferred: Charset): String {
        val primary = bytes.toString(preferred)
        if ('\uFFFD' !in primary) return primary.trim('\u0000')
        return bytes.toString(Charset.forName("windows-1252")).trim('\u0000')
    }

    private fun List<ByteArray>.flattenBytes(): ByteArray {
        val total = sumOf { it.size }
        val result = ByteArray(total)
        var offset = 0
        forEach { bytes ->
            bytes.copyInto(result, offset)
            offset += bytes.size
        }
        return result
    }

    private data class ParsedChapter(
        val title: String,
        val html: String,
        val plainText: String
    )

    private data class ParsedDocument(
        val title: String?,
        val author: String? = null,
        val chapters: List<ParsedChapter> = emptyList(),
        val plainText: String = chapters.joinToString("\n\n") { it.plainText }
    )

    private data class ParsedMetadata(
        val title: String? = null,
        val author: String? = null
    )

    private data class ParsedMobi(
        val title: String?,
        val author: String?,
        val html: String,
        val text: String
    )
}
