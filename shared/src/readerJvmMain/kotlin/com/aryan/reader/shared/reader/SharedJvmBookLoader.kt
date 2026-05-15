package com.aryan.reader.shared.reader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssParser
import com.aryan.reader.paginatedreader.HtmlResourceResolver
import com.aryan.reader.paginatedreader.OptimizedCssRules
import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticMath
import com.aryan.reader.paginatedreader.SemanticSpacer
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.paginatedreader.SemanticWrappingBlock
import com.aryan.reader.paginatedreader.UserAgentStylesheet
import com.aryan.reader.paginatedreader.htmlToSemanticBlocks
import com.aryan.reader.shared.FileType
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipFile
import javax.imageio.ImageIO

object SharedJvmBookLoader {
    private val persistentBookCache = SharedJvmBookLoadCache()
    private val loadedBookCache = SharedJvmLruMemoryCache<SharedJvmBookLoadCacheKey, SharedEpubBook>(maxEntries = 12)

    fun load(
        file: File,
        type: FileType,
        titleOverride: String? = null,
        authorOverride: String? = null
    ): SharedEpubBook {
        require(file.isFile) { "Missing reader file: ${file.absolutePath}" }
        val key = SharedJvmBookLoadCacheKey(
            canonicalPath = file.canonicalPath,
            type = type,
            length = file.length(),
            lastModified = file.lastModified()
        )
        synchronized(loadedBookCache) {
            loadedBookCache[key]?.let { return it.withOverrides(titleOverride = titleOverride, authorOverride = authorOverride) }
        }

        val loaded = persistentBookCache.load(key) ?: when (type) {
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
        }.also { parsed ->
            persistentBookCache.save(key, parsed)
        }

        synchronized(loadedBookCache) {
            loadedBookCache[key] = loaded
        }
        return loaded.withOverrides(titleOverride = titleOverride, authorOverride = authorOverride)
    }

    fun clearCache() {
        persistentBookCache.clear()
        synchronized(loadedBookCache) {
            loadedBookCache.clear()
        }
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
            val tableOfContents = parseEpubTableOfContents(zip, manifest, basePath)
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
                val chapter = chapterFromHtml(
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
                chapter.takeIf { text.isNotBlank() || it.semanticBlocks.isNotEmpty() }
            }

            return SharedEpubBook(
                id = file.absolutePath,
                fileName = file.name,
                title = title,
                author = author,
                css = cssByPath,
                tableOfContents = tableOfContents,
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
        return if (mobi.chapters.isNotEmpty()) {
            val cssRules = parseCssRules(emptyMap())
            SharedEpubBook(
                id = file.absolutePath,
                fileName = file.name,
                title = title,
                author = author,
                chapters = mobi.chapters.mapIndexed { index, chapter ->
                    chapterFromHtml(
                        id = "mobi_chapter_$index",
                        title = chapter.title.takeUnlessBlank() ?: "Chapter ${index + 1}",
                        html = chapter.html,
                        plainText = chapter.plainText.takeUnlessBlank() ?: chapter.html.htmlToText(),
                        baseHref = file.absolutePath,
                        cssRules = cssRules
                    )
                }
            )
        } else if (mobi.html.isNotBlank()) {
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
                constraints = Constraints(maxWidth = 980, maxHeight = 720),
                resourceResolver = SharedJvmHtmlResourceResolver
            )
        }.getOrDefault(emptyList())
        return SharedEpubChapter(
            id = id,
            title = title,
            plainText = plainText.takeUnlessBlank() ?: semanticBlocks.semanticFallbackText().ifBlank { title },
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
        val textRecordSize = header.u16(10).takeIf { it > 0 } ?: 4096
        val encryption = header.u16(12)
        require(encryption == 0) { "Encrypted MOBI files are not supported." }
        require(compression == MOBI_COMPRESSION_NONE ||
            compression == MOBI_COMPRESSION_PALMDOC ||
            compression == MOBI_COMPRESSION_HUFFCDIC
        ) {
            "MOBI compression $compression is not supported by the shared JVM loader."
        }

        val mobiHeader = parseMobiHeaderInfo(header)
        val encoding = mobiHeader.encoding ?: 1252
        val charset = when (encoding) {
            65001 -> Charsets.UTF_8
            1200 -> Charsets.UTF_16
            1252 -> Charset.forName("windows-1252")
            else -> Charsets.UTF_8
        }
        val huffCdic = if (compression == MOBI_COMPRESSION_HUFFCDIC) {
            parseMobiHuffCdic(records, mobiHeader.huffRecordIndex, mobiHeader.huffRecordCount)
        } else {
            null
        }

        val rawTextBytes = buildList {
            for (index in 1..textRecordCount) {
                val record = records.getOrNull(index) ?: continue
                val textRecord = record.withoutMobiTrailingData(mobiHeader.extraFlags)
                add(
                    when (compression) {
                        MOBI_COMPRESSION_NONE -> textRecord.withoutOldMobiZeros()
                        MOBI_COMPRESSION_PALMDOC -> decompressPalmDoc(textRecord)
                        MOBI_COMPRESSION_HUFFCDIC -> decompressHuffman(textRecord, huffCdic, textRecordSize)
                        else -> textRecord
                    }
                )
            }
        }.flattenBytes()
            .let { if (textLength in 1 until it.size) it.copyOf(textLength) else it }

        val resourceMap = mobiHeader.imageIndex
            ?.let { imageIndex -> parseMobiResources(records, imageIndex) }
            .orEmpty()
        val rawText = decodeMobiText(rawTextBytes, charset).withMobiEmbeddedResources(resourceMap)
        val metadata = parseMobiMetadata(header, charset)
        val title = metadata.title.takeUnlessBlank() ?: fallbackTitle
        val author = metadata.author.takeUnlessBlank()
        val looksLikeHtml = rawText.contains("<html", ignoreCase = true) ||
            rawText.contains("<body", ignoreCase = true) ||
            rawText.contains("<p", ignoreCase = true)
        val html = if (looksLikeHtml) rawText else ""
        return ParsedMobi(
            title = title,
            author = author,
            html = html,
            text = if (looksLikeHtml) rawText.htmlToText() else rawText.normalizeReaderWhitespace(),
            chapters = if (looksLikeHtml) splitMobiHtmlChapters(html, title) else emptyList()
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
                    99 -> exthTitle = exthTitle ?: value
                    503 -> exthTitle = exthTitle ?: value
                }
                offset += size
            }
        }
        return ParsedMetadata(title = exthTitle.takeUnlessBlank() ?: fullName.takeUnlessBlank(), author = author)
    }

    private fun parseMobiHeaderInfo(header: ByteArray): MobiHeaderInfo {
        if (header.size < 32 || header.asciiAt(16, 4) != "MOBI") return MobiHeaderInfo()
        val mobiHeaderLength = header.u32(20).toInt()
        fun u32InHeader(offset: Int): Int? {
            if (mobiHeaderLength < offset + 4 || 16 + offset + 4 > header.size) return null
            return header.u32(16 + offset).toInt()
                .takeIf { it >= 0 && it != MOBI_NOT_SET }
        }
        fun u16InHeader(offset: Int): Int {
            if (mobiHeaderLength < offset + 2 || 16 + offset + 2 > header.size) return 0
            return header.u16(16 + offset)
        }
        return MobiHeaderInfo(
            encoding = u32InHeader(12),
            imageIndex = u32InHeader(92),
            huffRecordIndex = u32InHeader(96),
            huffRecordCount = u32InHeader(100),
            extraFlags = u16InHeader(242)
        )
    }

    private fun parseMobiHuffCdic(
        records: List<ByteArray>,
        huffRecordIndex: Int?,
        huffRecordCount: Int?
    ): MobiHuffCdic {
        val start = huffRecordIndex ?: error("HUFF/CDIC MOBI is missing HUFF record metadata.")
        val count = huffRecordCount ?: error("HUFF/CDIC MOBI is missing CDIC record metadata.")
        require(count >= 2 && start > 0 && start + count <= records.size) {
            "HUFF/CDIC record metadata points outside the MOBI record table."
        }

        val huff = records[start]
        require(huff.size >= HUFF_RECORD_MIN_SIZE && huff.asciiAt(0, 4) == "HUFF") {
            "MOBI HUFF record is missing or corrupt."
        }
        val huffHeaderLength = huff.u32(4).toInt()
        require(huffHeaderLength >= HUFF_HEADER_LENGTH) { "MOBI HUFF record header is too short." }
        val data1Offset = huff.u32(8).toInt()
        val data2Offset = huff.u32(12).toInt()
        require(data1Offset >= 0 && data1Offset + 256 * 4 <= huff.size) { "MOBI HUFF table 1 is corrupt." }
        require(data2Offset >= 0 && data2Offset + 64 * 4 <= huff.size) { "MOBI HUFF table 2 is corrupt." }

        val table1 = IntArray(256) { index -> huff.u32(data1Offset + index * 4).toInt() }
        val mincodeTable = LongArray(HUFF_CODETABLE_SIZE)
        val maxcodeTable = LongArray(HUFF_CODETABLE_SIZE)
        mincodeTable[0] = 0L
        maxcodeTable[0] = UINT32_MAX
        var tableOffset = data2Offset
        for (index in 1 until HUFF_CODETABLE_SIZE) {
            val mincode = huff.u32(tableOffset)
            val maxcode = huff.u32(tableOffset + 4)
            mincodeTable[index] = (mincode shl (32 - index)) and UINT32_MAX
            maxcodeTable[index] = (((maxcode + 1L) shl (32 - index)) - 1L) and UINT32_MAX
            tableOffset += 8
        }

        var codeLength = 0
        var indexCount = 0
        var indexRead = 0
        val symbolOffsets = mutableListOf<Int>()
        val symbols = mutableListOf<ByteArray>()

        for (recordOffset in 1 until count) {
            val cdic = records[start + recordOffset]
            require(cdic.size >= CDIC_HEADER_LENGTH && cdic.asciiAt(0, 4) == "CDIC") {
                "MOBI CDIC record is missing or corrupt."
            }
            val cdicHeaderLength = cdic.u32(4).toInt()
            require(cdicHeaderLength >= CDIC_HEADER_LENGTH) { "MOBI CDIC record header is too short." }
            val totalIndexCount = cdic.u32(8).toInt()
            val currentCodeLength = cdic.u32(12).toInt()
            require(currentCodeLength in 1..HUFF_CODELEN_MAX) { "MOBI CDIC code length is invalid." }
            if (codeLength == 0) codeLength = currentCodeLength
            if (indexCount == 0) indexCount = totalIndexCount
            require(codeLength == currentCodeLength && indexCount == totalIndexCount) {
                "MOBI CDIC records disagree about dictionary dimensions."
            }

            var entriesToRead = totalIndexCount - indexRead
            if ((entriesToRead ushr codeLength) > 0) {
                entriesToRead = 1 shl codeLength
            }
            require(entriesToRead >= 0 && CDIC_HEADER_LENGTH + entriesToRead * 2 <= cdic.size) {
                "MOBI CDIC symbol table is corrupt."
            }
            var offset = CDIC_HEADER_LENGTH
            repeat(entriesToRead) {
                val symbolOffset = cdic.u16(offset)
                val symbolStart = CDIC_HEADER_LENGTH + symbolOffset
                require(symbolStart + 2 <= cdic.size) { "MOBI CDIC symbol offset is corrupt." }
                val symbolLength = cdic.u16(symbolStart) and 0x7FFF
                require(symbolStart + 2 + symbolLength <= cdic.size) { "MOBI CDIC symbol data is corrupt." }
                symbolOffsets += symbolOffset
                indexRead += 1
                offset += 2
            }
            symbols += cdic.copyOfRange(CDIC_HEADER_LENGTH, cdic.size)
        }

        require(indexCount == indexRead && symbolOffsets.size == indexCount) {
            "MOBI CDIC dictionary did not provide all symbol offsets."
        }
        return MobiHuffCdic(
            indexCount = indexCount,
            codeLength = codeLength,
            table1 = table1,
            mincodeTable = mincodeTable,
            maxcodeTable = maxcodeTable,
            symbolOffsets = symbolOffsets.toIntArray(),
            symbols = symbols
        )
    }

    private fun decompressHuffman(input: ByteArray, huffCdic: MobiHuffCdic?, textRecordSize: Int): ByteArray {
        require(huffCdic != null) { "MOBI HUFF/CDIC dictionary is missing." }
        val output = ByteArrayOutputStream((textRecordSize * 2).coerceAtLeast(input.size))
        decompressHuffmanInto(input, output, huffCdic, depth = 0)
        return output.toByteArray()
    }

    private fun decompressHuffmanInto(
        input: ByteArray,
        output: ByteArrayOutputStream,
        huffCdic: MobiHuffCdic,
        depth: Int
    ) {
        require(depth <= MOBI_HUFFMAN_MAX_DEPTH) { "MOBI HUFF/CDIC recursion limit exceeded." }
        var bitCount = 32
        var bitsLeft = input.size * 8
        var inputOffset = 0
        var buffer = input.huffmanFill64(inputOffset)
        inputOffset += 4

        while (true) {
            if (bitCount <= 0) {
                bitCount += 32
                buffer = input.huffmanFill64(inputOffset)
                inputOffset += 4
            }
            val code = (buffer ushr bitCount) and UINT32_MAX
            val tableEntry = huffCdic.table1[(code ushr 24).toInt()].toLong() and UINT32_MAX
            var codeLength = (tableEntry and 0x1F).toInt()
            if (codeLength <= 0 || codeLength >= HUFF_CODETABLE_SIZE) {
                break
            }
            var maxcode = ((((tableEntry ushr 8) + 1L) shl (32 - codeLength)) - 1L) and UINT32_MAX
            if ((tableEntry and 0x80L) == 0L) {
                while (code < huffCdic.mincodeTable[codeLength]) {
                    codeLength += 1
                    require(codeLength < HUFF_CODETABLE_SIZE) { "MOBI HUFF code table offset is corrupt." }
                }
                maxcode = huffCdic.maxcodeTable[codeLength]
            }

            bitCount -= codeLength
            bitsLeft -= codeLength
            if (bitsLeft < 0) break

            val symbolIndex = ((maxcode - code) ushr (32 - codeLength)).toInt()
            require(symbolIndex in 0 until huffCdic.indexCount) { "MOBI HUFF symbol index is corrupt." }
            val cdicIndex = symbolIndex ushr huffCdic.codeLength
            val symbols = huffCdic.symbols.getOrNull(cdicIndex)
                ?: error("MOBI HUFF symbol record is missing.")
            val offset = huffCdic.symbolOffsets[symbolIndex]
            require(offset + 2 <= symbols.size) { "MOBI HUFF symbol offset is corrupt." }
            val symbolHeader = symbols.u16(offset)
            val isDecompressed = (symbolHeader and 0x8000) != 0
            val symbolLength = symbolHeader and 0x7FFF
            require(offset + 2 + symbolLength <= symbols.size) { "MOBI HUFF symbol data is corrupt." }

            if (isDecompressed) {
                output.write(symbols, offset + 2, symbolLength)
            } else {
                decompressHuffmanInto(
                    input = symbols.copyOfRange(offset + 2, offset + 2 + symbolLength),
                    output = output,
                    huffCdic = huffCdic,
                    depth = depth + 1
                )
            }
        }
    }

    private fun ByteArray.huffmanFill64(offset: Int): Long {
        var value = 0L
        var shiftIndex = 8
        var index = offset
        var bytesLeft = (size - offset).coerceAtLeast(0)
        while (shiftIndex > 0 && bytesLeft > 0) {
            shiftIndex -= 1
            value = value or ((this[index].toLong() and 0xFFL) shl (shiftIndex * 8))
            index += 1
            bytesLeft -= 1
        }
        return value
    }

    private fun ByteArray.withoutMobiTrailingData(extraFlags: Int): ByteArray {
        if (extraFlags == 0 || isEmpty()) return this
        val extraSize = mobiTrailingDataSize(extraFlags)
        return if (extraSize in 1 until size) copyOf(size - extraSize) else this
    }

    private fun ByteArray.mobiTrailingDataSize(extraFlags: Int): Int {
        var position = lastIndex
        var extraSize = 0
        for (bit in 15 downTo 1) {
            if ((extraFlags and (1 shl bit)) == 0) continue
            val value = readBackwardVarlen(position) ?: return 0
            position = value.nextPosition - (value.size - value.byteCount)
            if (position < -1) return 0
            extraSize += value.size
        }
        if ((extraFlags and 1) != 0 && position in indices) {
            extraSize += (this[position].toInt() and 0x03) + 1
        }
        return extraSize.coerceIn(0, size)
    }

    private fun ByteArray.readBackwardVarlen(start: Int): MobiBackwardVarlen? {
        var value = 0
        var shift = 0
        var count = 0
        var index = start
        while (index >= 0 && count < 4) {
            val byte = this[index].toInt() and 0xFF
            value = value or ((byte and 0x7F) shl shift)
            count += 1
            index -= 1
            if ((byte and 0x80) != 0) {
                return MobiBackwardVarlen(size = value, byteCount = count, nextPosition = start - count)
            }
            shift += 7
        }
        return null
    }

    private fun ByteArray.withoutOldMobiZeros(): ByteArray {
        return if (0.toByte() in this) filter { it != 0.toByte() }.toByteArray() else this
    }

    private fun parseMobiResources(records: List<ByteArray>, imageIndex: Int): Map<Int, String> {
        if (imageIndex <= 0 || imageIndex >= records.size) return emptyMap()
        var imageNumber = 1
        val resources = mutableMapOf<Int, String>()
        for (recordIndex in imageIndex until records.size) {
            val bytes = records[recordIndex]
            val mimeType = bytes.mobiResourceMimeType() ?: continue
            resources[imageNumber] = "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}"
            imageNumber += 1
        }
        return resources
    }

    private fun ByteArray.mobiResourceMimeType(): String? {
        return when {
            size >= 3 &&
                (this[0].toInt() and 0xFF) == 0xFF &&
                (this[1].toInt() and 0xFF) == 0xD8 &&
                (this[2].toInt() and 0xFF) == 0xFF -> "image/jpeg"
            size >= 8 && asciiAt(1, 3) == "PNG" -> "image/png"
            size >= 6 && (asciiAt(0, 6) == "GIF87a" || asciiAt(0, 6) == "GIF89a") -> "image/gif"
            size >= 12 && asciiAt(0, 4) == "RIFF" && asciiAt(8, 4) == "WEBP" -> "image/webp"
            size >= 2 && asciiAt(0, 2) == "BM" -> "image/bmp"
            else -> null
        }
    }

    private fun String.withMobiEmbeddedResources(resources: Map<Int, String>): String {
        if (resources.isEmpty() || !contains("kindle:", ignoreCase = true) && !contains("recindex", ignoreCase = true)) {
            return this
        }
        val document = Jsoup.parse(this)
        document.select("img").forEach { image ->
            val embedIndex = image.attr("src")
                .substringAfter("kindle:embed:", missingDelimiterValue = "")
                .substringBefore("?")
                .toIntOrNull()
            val recordIndex = image.attr("recindex").toIntOrNull()
            val replacement = embedIndex?.let(resources::get)
                ?: recordIndex?.let(resources::get)
            if (replacement != null) {
                image.attr("src", replacement)
                image.removeAttr("recindex")
            }
        }
        return document.outerHtml()
    }

    private fun splitMobiHtmlChapters(html: String, fallbackTitle: String): List<ParsedChapter> {
        val parts = Regex("(?is)<mbp:pagebreak\\b[^>]*>").split(html)
            .map { it.trim() }
            .filter { it.htmlToText().isNotBlank() }
        if (parts.size <= 1) return emptyList()
        return parts.mapIndexed { index, chapterHtml ->
            val title = chapterHtml.tagText("h1")
                .ifBlank { chapterHtml.tagText("h2") }
                .ifBlank { if (index == 0) fallbackTitle else "Chapter ${index + 1}" }
            ParsedChapter(
                title = title,
                html = chapterHtml,
                plainText = chapterHtml.htmlToText()
            )
        }
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

    private fun parseEpubTableOfContents(
        zip: ZipFile,
        manifest: Map<String, String>,
        basePath: String
    ): List<SharedEpubTocEntry> {
        val manifestNcxHref = manifest.values.firstOrNull { it.endsWith(".ncx", ignoreCase = true) }
        val ncxPath = manifestNcxHref
            ?.let { normalizeZipPath(basePath + it) }
            ?: zip.entries().asSequence()
                .map { it.name }
                .firstOrNull { it.endsWith(".ncx", ignoreCase = true) }
            ?: return emptyList()
        val document = zip.readBytesOrNull(ncxPath)?.let(::xmlDocument) ?: return emptyList()
        val navMap = document.allElementsByLocalTag("navmap").firstOrNull() ?: return emptyList()
        val ncxBasePath = ncxPath.substringBeforeLast('/', missingDelimiterValue = "")
        val entries = mutableListOf<SharedEpubTocEntry>()

        fun visit(parent: Element, depth: Int) {
            parent.childrenByLocalTag("navpoint").forEach { navPoint ->
                val label = navPoint.childrenByLocalTag("navlabel")
                    .firstOrNull()
                    ?.allElementsByLocalTag("text")
                    ?.firstOrNull()
                    ?.text()
                    ?.normalizeReaderWhitespace()
                    .takeUnlessBlank()
                    ?: "Section ${entries.size + 1}"
                val src = navPoint.childrenByLocalTag("content")
                    .firstOrNull()
                    ?.xmlAttr("src")
                    .orEmpty()
                    .trim()
                val href = src.substringBefore('#').substringBefore('?').percentDecodedOrSelf()
                val fragmentId = src.substringAfter('#', missingDelimiterValue = "")
                    .substringBefore('?')
                    .takeUnlessBlank()
                    ?.percentDecodedOrSelf()
                if (href.isNotBlank()) {
                    val absoluteHref = normalizeZipPath(
                        if (ncxBasePath.isBlank()) href else "$ncxBasePath/$href"
                    )
                    entries += SharedEpubTocEntry(
                        label = label,
                        href = absoluteHref,
                        fragmentId = fragmentId,
                        depth = depth.coerceAtLeast(0)
                    )
                }
                visit(navPoint, depth + 1)
            }
        }

        visit(navMap, depth = 0)
        return entries
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
            isDarkTheme = false,
            adaptThemeColors = false
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
                        isDarkTheme = false,
                        adaptThemeColors = false
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

    private fun String.percentDecodedOrSelf(): String {
        return runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrDefault(this)
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

    private object SharedJvmHtmlResourceResolver : HtmlResourceResolver {
        override fun resolvePath(chapterAbsPath: String, extractionBasePath: String, src: String): String? {
            val raw = src.trim().takeIf { it.isNotBlank() } ?: return null
            if (raw.startsWith("data:", ignoreCase = true)) return raw
            if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) return raw
            if (raw.startsWith("file:", ignoreCase = true)) return raw

            val clean = raw.substringBefore('#').substringBefore('?').takeIf { it.isNotBlank() } ?: return null
            val decoded = runCatching { URLDecoder.decode(clean, Charsets.UTF_8.name()) }.getOrDefault(clean)
            val direct = File(decoded)
            if (direct.isAbsolute && direct.isFile) return direct.toURI().toString()

            val chapterFile = chapterAbsPath.trim().takeIf { it.isNotBlank() }?.let(::File)
            val chapterRelative = chapterFile?.parentFile?.let { File(it, decoded) }
            if (chapterRelative?.isFile == true) return chapterRelative.toURI().toString()

            val extractionRelative = extractionBasePath.trim().takeIf { it.isNotBlank() }?.let { File(it, decoded) }
            if (extractionRelative?.isFile == true) return extractionRelative.toURI().toString()

            return null
        }

        override fun readText(path: String): String? {
            dataUriBytes(path)?.let { bytes -> return bytes.toString(Charsets.UTF_8) }
            return path.toFileOrNull()?.takeIf { it.isFile }?.readText()
        }

        override fun imageDimensions(path: String): Pair<Float?, Float?>? {
            val image = runCatching {
                dataUriBytes(path)?.let { bytes ->
                    ImageIO.read(ByteArrayInputStream(bytes))
                } ?: path.toFileOrNull()?.takeIf { it.isFile }?.let { file -> ImageIO.read(file) }
            }.getOrNull() ?: return null
            return image.width.toFloat() to image.height.toFloat()
        }

        private fun dataUriBytes(value: String): ByteArray? {
            if (!value.startsWith("data:", ignoreCase = true)) return null
            val commaIndex = value.indexOf(',')
            if (commaIndex < 0) return null
            val metadata = value.substring(0, commaIndex)
            val payload = value.substring(commaIndex + 1)
            return if (";base64" in metadata.lowercase()) {
                runCatching { Base64.getDecoder().decode(payload) }.getOrNull()
            } else {
                runCatching { URLDecoder.decode(payload, Charsets.UTF_8.name()).toByteArray(Charsets.UTF_8) }.getOrNull()
            }
        }

        private fun String.toFileOrNull(): File? {
            return when {
                startsWith("file:", ignoreCase = true) -> runCatching { File(URI(this)) }.getOrNull()
                else -> File(this)
            }
        }
    }

    private fun List<SemanticBlock>.semanticFallbackText(): String {
        return flatMap { it.semanticTextParts() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun SemanticBlock.semanticTextParts(): List<String> {
        return when (this) {
            is SemanticTextBlock -> listOf(text)
            is SemanticList -> items.flatMap { it.semanticTextParts() }
            is SemanticTable -> rows.flatMap { row -> row.flatMap { cell -> cell.content.flatMap { it.semanticTextParts() } } }
            is SemanticFlexContainer -> children.flatMap { it.semanticTextParts() }
            is SemanticWrappingBlock -> floatedImage.semanticTextParts() + paragraphsToWrap.flatMap { it.semanticTextParts() }
            is SemanticImage -> listOf(altText.orEmpty())
            is SemanticMath -> listOf(altText.orEmpty())
            is SemanticSpacer -> emptyList()
        }
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
        val text: String,
        val chapters: List<ParsedChapter> = emptyList()
    )

    private data class MobiHeaderInfo(
        val encoding: Int? = null,
        val imageIndex: Int? = null,
        val huffRecordIndex: Int? = null,
        val huffRecordCount: Int? = null,
        val extraFlags: Int = 0
    )

    private data class MobiHuffCdic(
        val indexCount: Int,
        val codeLength: Int,
        val table1: IntArray,
        val mincodeTable: LongArray,
        val maxcodeTable: LongArray,
        val symbolOffsets: IntArray,
        val symbols: List<ByteArray>
    )

    private data class MobiBackwardVarlen(
        val size: Int,
        val byteCount: Int,
        val nextPosition: Int
    )

    private const val MOBI_COMPRESSION_NONE = 1
    private const val MOBI_COMPRESSION_PALMDOC = 2
    private const val MOBI_COMPRESSION_HUFFCDIC = 17480
    private const val MOBI_NOT_SET = -1
    private const val HUFF_HEADER_LENGTH = 24
    private const val HUFF_RECORD_MIN_SIZE = 2584
    private const val HUFF_CODETABLE_SIZE = 33
    private const val HUFF_CODELEN_MAX = 16
    private const val CDIC_HEADER_LENGTH = 16
    private const val MOBI_HUFFMAN_MAX_DEPTH = 20
    private const val UINT32_MAX = 0xFFFF_FFFFL
}
