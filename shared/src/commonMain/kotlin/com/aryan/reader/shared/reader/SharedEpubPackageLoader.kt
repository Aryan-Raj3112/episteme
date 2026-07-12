@file:OptIn(ExperimentalEncodingApi::class)

package com.aryan.reader.shared.reader

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Platform-neutral view of an EPUB ZIP archive. Platform source sets only provide archive I/O;
 * package parsing, resource resolution, sanitizing, and book construction stay shared.
 */
interface SharedEpubArchive {
    val entryPaths: Set<String>

    fun readBytes(path: String): ByteArray?

    fun readText(path: String): String? = readBytes(path)?.decodeToString()
}

object SharedEpubPackageLoader {
    fun load(
        archive: SharedEpubArchive,
        sourceId: String,
        fileName: String
    ): SharedEpubBook {
        val normalizedEntries = archive.entryPaths
            .mapNotNull(::safeEpubPathOrNull)
            .associateBy { it.lowercase() }
        fun actualPath(path: String): String? {
            val safe = safeEpubPathOrNull(path) ?: return null
            return normalizedEntries[safe.lowercase()]
        }
        fun text(path: String): String? = actualPath(path)?.let(archive::readText)
        fun bytes(path: String): ByteArray? = actualPath(path)?.let(archive::readBytes)

        val container = text("META-INF/container.xml")
        val containerRoot = container?.let(::parseSharedEpubXml)
        val containerRootFiles = containerRoot?.descendants("rootfile")?.toList().orEmpty()
        val preferredRootFile = containerRootFiles.firstOrNull {
            it.attribute("media-type").equals("application/oebps-package+xml", ignoreCase = true)
        } ?: containerRootFiles.firstOrNull()
        val opfPath = preferredRootFile
            ?.attribute("full-path")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let(::safeEpubPathOrNull)
            ?: normalizedEntries.values.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            ?: error("EPUB package document was not found")
        val opf = text(opfPath) ?: error("EPUB package document is missing: $opfPath")
        val packageRoot = parseSharedEpubXml(opf) ?: error("EPUB package document is malformed: $opfPath")
        val metadata = packageRoot.firstDescendant("metadata")
        val manifestNode = packageRoot.firstDescendant("manifest")
        val spineNode = packageRoot.firstDescendant("spine")
        val manifest = manifestNode?.children
            .orEmpty()
            .filter { it.localName == "item" }
            .mapNotNull { item ->
                val id = item.attribute("id").orEmpty().trim()
                val href = item.attribute("href").orEmpty().trim()
                if (id.isBlank() || href.isBlank()) null else {
                    id to SharedEpubManifestItem(
                        id = id,
                        path = resolveEpubPath(opfPath, href),
                        mediaType = item.attribute("media-type").orEmpty().trim().lowercase(),
                        properties = item.attribute("properties")
                            .orEmpty()
                            .split(Regex("\\s+"))
                            .filter(String::isNotBlank)
                            .map(String::lowercase)
                            .toSet()
                    )
                }
            }
            .toMap()
        val manifestMimeTypes = manifest.values.associate { it.path.lowercase() to it.mediaType }
        fun resourceMimeType(path: String): String = manifestMimeTypes[path.lowercase()]
            ?.takeIf(String::isNotBlank)
            ?: epubMimeType(path)

        val uniqueIdentifier = packageRoot.attribute("unique-identifier")
            ?.let { uniqueId -> metadata?.descendants()?.firstOrNull { it.attribute("id") == uniqueId } }
            ?.textContent()
            ?.trim()
            .orEmpty()
        val title = metadata?.descendants("title")?.firstOrNull()?.textContent()?.decodeEpubEntities()
            ?.normalizeEpubWhitespace()
            ?.takeIf(String::isNotBlank)
            ?: fileName.substringBeforeLast('.', fileName)
        val author = metadata?.descendants("creator")?.firstOrNull()?.textContent()?.decodeEpubEntities()
            ?.normalizeEpubWhitespace()
            ?.takeIf(String::isNotBlank)

        val fontObfuscation = parseFontObfuscation(text("META-INF/encryption.xml"), uniqueIdentifier)
        fun resourceBytes(path: String): ByteArray? {
            val safe = safeEpubPathOrNull(path) ?: return null
            val raw = bytes(safe) ?: return null
            val rule = fontObfuscation[safe.lowercase()] ?: return raw
            return raw.deobfuscated(rule)
        }

        val processedCss = mutableMapOf<String, String>()
        val processingCss = mutableSetOf<String>()
        fun css(path: String): String? {
            val safe = safeEpubPathOrNull(path) ?: return null
            processedCss[safe]?.let { return it }
            if (!processingCss.add(safe)) return ""
            val raw = text(safe) ?: run {
                processingCss.remove(safe)
                return null
            }
            var output = raw.sanitizeEpubCss().replace(EpubCssImportRegex) { match ->
                val reference = match.groupValues.subList(1, 4).firstOrNull(String::isNotBlank).orEmpty().trim()
                if (
                    reference.startsWith("data:", ignoreCase = true) ||
                    reference.startsWith("http://", ignoreCase = true) ||
                    reference.startsWith("https://", ignoreCase = true)
                ) {
                    return@replace match.value
                }
                val importedPath = resolveEpubPath(safe, reference)
                val imported = css(importedPath).orEmpty()
                val media = match.groupValues[4].trim()
                if (media.isBlank() || media.equals("all", ignoreCase = true)) imported else "@media $media {\n$imported\n}"
            }
            output = output.rewriteEpubCssUrls(safe) { resourcePath ->
                val content = if (resourcePath.endsWith(".css", ignoreCase = true)) {
                    css(resourcePath)?.encodeToByteArray()
                } else {
                    resourceBytes(resourcePath)
                }
                content?.toEpubDataUri(resourceMimeType(resourcePath))
            }
            processingCss.remove(safe)
            processedCss[safe] = output
            return output
        }

        (manifest.values
            .filter { it.mediaType == "text/css" || it.path.endsWith(".css", ignoreCase = true) }
            .map(SharedEpubManifestItem::path) + normalizedEntries.values.filter { it.endsWith(".css", ignoreCase = true) })
            .distinct()
            .forEach(::css)

        val spineIds = spineNode?.children
            .orEmpty()
            .filter { it.localName == "itemref" && !it.attribute("linear").equals("no", ignoreCase = true) }
            .mapNotNull { it.attribute("idref")?.trim()?.takeIf(String::isNotBlank) }
        val chapterItems = spineIds.mapNotNull(manifest::get).ifEmpty {
            manifest.values.filter { it.isHtml && "nav" !in it.properties }
        }

        fun dataUri(path: String): String? {
            val safe = safeEpubPathOrNull(path) ?: return null
            val content = if (safe.endsWith(".css", ignoreCase = true)) {
                css(safe)?.encodeToByteArray()
            } else {
                resourceBytes(safe)
            } ?: return null
            return content.toEpubDataUri(resourceMimeType(safe))
        }

        val parsedToc = parseEpubTableOfContents(
            archiveText = ::text,
            packageRoot = packageRoot,
            manifest = manifest
        )
        val tocByPath = parsedToc
            .filter { !it.fragmentId.isNullOrBlank() }
            .groupBy { it.href.lowercase() }

        val parsedChapters = chapterItems.flatMapIndexed { index, item ->
            if (!item.isHtml) {
                if (!item.isImage) return@flatMapIndexed emptyList()
                val uri = dataUri(item.path) ?: return@flatMapIndexed emptyList()
                val label = item.id
                    .replace(Regex("[_-]+"), " ")
                    .trim()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    .ifBlank { "Image ${index + 1}" }
                return@flatMapIndexed listOf(SharedEpubChapter(
                    id = item.id.ifBlank { "chapter_$index" },
                    title = label,
                    plainText = "[Image]",
                    htmlContent = "<figure class=\"reader-epub-image-page\"><img src=\"${uri.escapeEpubAttribute()}\" alt=\"${label.escapeEpubAttribute()}\"></figure>",
                    baseHref = item.path
                ))
            }
            val raw = text(item.path) ?: return@flatMapIndexed emptyList()
            raw.extractEpubStyleBlocks().takeIf(String::isNotBlank)?.let { embeddedCss ->
                processedCss["${item.path}#embedded-style"] = embeddedCss.sanitizeEpubCss().rewriteEpubCssUrls(item.path) { resourcePath ->
                    resourceBytes(resourcePath)?.toEpubDataUri(resourceMimeType(resourcePath))
                }
            }
            val body = raw
                .sanitizeEpubReaderHtml()
                .extractEpubBodyOrSelf()
                .rewriteEpubHtmlResources(item.path, ::dataUri)
            val plainText = body.epubHtmlToText()
            if (plainText.isBlank() && !body.hasEpubVisualContent()) return@flatMapIndexed emptyList()
            val fallbackTitle = raw.firstEpubHeading()
                ?: raw.epubTagText("title")
                ?: "Chapter ${index + 1}"
            val sections = materializeEpubTocSections(
                body = body,
                entries = tocByPath[item.path.lowercase()].orEmpty()
            )
            if (sections.isEmpty()) {
                listOf(
                    SharedEpubChapter(
                        id = item.id.ifBlank { "chapter_$index" },
                        title = fallbackTitle,
                        plainText = plainText.ifBlank { "Chapter ${index + 1}" },
                        htmlContent = body,
                        baseHref = item.path
                    )
                )
            } else {
                sections.mapIndexed { sectionIndex, section ->
                    SharedEpubChapter(
                        id = "${item.id.ifBlank { "chapter_$index" }}#${section.fragmentId}",
                        title = section.entry.label.ifBlank { fallbackTitle },
                        plainText = section.html.epubHtmlToText().ifBlank { fallbackTitle },
                        htmlContent = section.html,
                        baseHref = item.path,
                        fragmentId = section.fragmentId
                    )
                }
            }
        }
        if (parsedChapters.isEmpty()) {
            error(
                "EPUB contains no readable spine documents " +
                    "(manifest=${manifest.size}, spine=${spineIds.size}, candidates=${chapterItems.size}, entries=${normalizedEntries.size})"
            )
        }

        val resolvedToc = parsedToc.ifEmpty {
            parsedChapters.mapIndexed { index, chapter ->
                SharedEpubTocEntry(
                    label = chapter.title,
                    href = chapter.baseHref.orEmpty(),
                    depth = 0
                )
            }
        }
        val chapters = parsedChapters.map { chapter ->
            val tocTitle = resolvedToc.firstOrNull {
                it.href.equals(chapter.baseHref, ignoreCase = true) &&
                    (chapter.fragmentId == null || it.fragmentId == chapter.fragmentId) &&
                    it.label.isNotBlank()
            }?.label
            if (tocTitle == null) chapter else chapter.copy(title = tocTitle)
        }

        return SharedEpubBook(
            id = sourceId,
            fileName = fileName,
            title = title,
            author = author,
            chapters = chapters,
            css = processedCss.toMap(),
            tableOfContents = resolvedToc
        )
    }
}

private data class SharedEpubManifestItem(
    val id: String,
    val path: String,
    val mediaType: String,
    val properties: Set<String>
) {
    val isHtml: Boolean
        get() = mediaType == "application/xhtml+xml" ||
            mediaType == "text/html" ||
            path.endsWith(".xhtml", ignoreCase = true) ||
            path.endsWith(".html", ignoreCase = true) ||
            path.endsWith(".htm", ignoreCase = true)

    val isImage: Boolean
        get() = mediaType.startsWith("image/") ||
            path.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "svg", "webp", "avif")
}

/**
 * Mirrors Android's logical-section behavior for EPUBs whose navigation points
 * at multiple fragments in a single spine document. Splitting only at direct
 * body children keeps markup valid and deliberately ignores anchors nested in
 * the same block.
 */
private fun materializeEpubTocSections(
    body: String,
    entries: List<SharedEpubTocEntry>
): List<SharedEpubLogicalSection> {
    if (entries.size < 2) return emptyList()
    val requestedFragments = entries.mapNotNull { it.fragmentId }.toSet()
    if (requestedFragments.size < 2) return emptyList()
    val tokens = sharedEpubXmlTokens(body).toList()
    val rootStart = tokens.firstOrNull { token ->
        token.value.startsWith('<') && !token.value.startsWith("</") &&
            token.value.drop(1).trimStart().startsWith("div", ignoreCase = true)
    } ?: return emptyList()
    val childRanges = mutableListOf<IntRange>()
    val fragmentChildIndex = mutableMapOf<String, Int>()
    var depth = 0
    var currentChildStart = -1
    var currentChildIndex = -1
    var started = false

    tokens.forEach { token ->
        if (token.start < rootStart.start) return@forEach
        val value = token.value
        val isClosing = value.startsWith("</")
        val isOpening = value.startsWith('<') && !isClosing && !value.startsWith("<!--") &&
            !value.startsWith("<?") && !value.startsWith("<!")
        val selfClosing = isOpening && value.trimEnd().endsWith("/>")
        if (!started && isOpening) {
            started = true
            depth = 1
            return@forEach
        }
        if (!started) return@forEach
        if (isOpening) {
            if (depth == 1) {
                currentChildStart = token.start
                currentChildIndex = childRanges.size
            }
            val attrs = EpubXmlAttributeRegex.findAll(value).associate { match ->
                match.groupValues[1].substringAfter(':').lowercase() to match.groupValues[3].decodeEpubEntities()
            }
            val fragment = (attrs["id"] ?: attrs["name"])?.takeIf { it in requestedFragments }
            if (fragment != null && currentChildIndex >= 0) {
                if (fragment !in fragmentChildIndex) {
                    fragmentChildIndex[fragment] = currentChildIndex
                }
            }
            if (!selfClosing) depth++
            if (selfClosing && depth == 1 && currentChildStart >= 0) {
                childRanges += currentChildStart until token.endExclusive
                currentChildStart = -1
                currentChildIndex = -1
            }
        } else if (isClosing) {
            if (depth == 2 && currentChildStart >= 0) {
                childRanges += currentChildStart until token.endExclusive
                currentChildStart = -1
                currentChildIndex = -1
            }
            depth = (depth - 1).coerceAtLeast(0)
        }
    }

    val starts = entries.mapNotNull { entry ->
        val fragment = entry.fragmentId ?: return@mapNotNull null
        fragmentChildIndex[fragment]?.let { entry to it }
    }.distinctBy { it.second }.sortedBy { it.second }
    if (starts.size < 2 || childRanges.size < 2) return emptyList()
    return starts.mapIndexedNotNull { index, (entry, childIndex) ->
        val endChildIndex = starts.getOrNull(index + 1)?.second ?: childRanges.lastIndex + 1
        if (childIndex >= endChildIndex || childIndex !in childRanges.indices) return@mapIndexedNotNull null
        val startOffset = childRanges[childIndex].first
        val endOffset = childRanges[(endChildIndex - 1).coerceIn(childIndex, childRanges.lastIndex)].last + 1
        SharedEpubLogicalSection(
            entry = entry,
            fragmentId = entry.fragmentId ?: return@mapIndexedNotNull null,
            html = body.substring(startOffset, endOffset)
        )
    }
}

private data class SharedEpubLogicalSection(
    val entry: SharedEpubTocEntry,
    val fragmentId: String,
    val html: String
)

private data class SharedEpubFontObfuscation(
    val key: ByteArray,
    val byteCount: Int
)

private fun parseFontObfuscation(
    encryptionXml: String?,
    uniqueIdentifier: String
): Map<String, SharedEpubFontObfuscation> {
    val root = encryptionXml?.let(::parseSharedEpubXml) ?: return emptyMap()
    val normalizedIdentifier = uniqueIdentifier.filterNot(Char::isWhitespace)
    val idpfKey = normalizedIdentifier
        .takeIf(String::isNotBlank)
        ?.encodeToByteArray()
        ?.sha1()
    val adobeKey = Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        .find(uniqueIdentifier)
        ?.value
        ?.filter { it != '-' }
        ?.chunked(2)
        ?.map { it.toInt(16).toByte() }
        ?.toByteArray()
    return root.descendants("encrypteddata").mapNotNull { encrypted ->
        val algorithm = encrypted.firstDescendant("encryptionmethod")?.attribute("algorithm").orEmpty()
        val uri = encrypted.firstDescendant("cipherreference")?.attribute("uri").orEmpty()
        // OCF CipherReference URIs are relative to the container root, not the OPF document.
        val path = safeEpubPathOrNull(uri.substringBefore('#').substringBefore('?').percentDecodeEpubPath())
            ?.lowercase()
            ?: return@mapNotNull null
        when {
            algorithm.equals("http://www.idpf.org/2008/embedding", ignoreCase = true) && idpfKey != null ->
                path to SharedEpubFontObfuscation(idpfKey, 1040)
            algorithm.equals("http://ns.adobe.com/pdf/enc#RC", ignoreCase = true) && adobeKey != null ->
                path to SharedEpubFontObfuscation(adobeKey, 1024)
            else -> null
        }
    }.toMap()
}

private fun ByteArray.deobfuscated(rule: SharedEpubFontObfuscation): ByteArray {
    if (isEmpty() || rule.key.isEmpty()) return this
    return copyOf().also { output ->
        repeat(minOf(output.size, rule.byteCount)) { index ->
            output[index] = (output[index].toInt() xor rule.key[index % rule.key.size].toInt()).toByte()
        }
    }
}

private fun parseEpubTableOfContents(
    archiveText: (String) -> String?,
    packageRoot: SharedEpubXmlNode,
    manifest: Map<String, SharedEpubManifestItem>
): List<SharedEpubTocEntry> {
    val navItem = manifest.values.firstOrNull { "nav" in it.properties }
    if (navItem != null) {
        val navRoot = archiveText(navItem.path)?.let(::parseSharedEpubXml)
        val nav = navRoot?.descendants("nav")?.firstOrNull { node ->
            node.attributes.any { (name, value) -> name.substringAfter(':').equals("type", true) && value.split(' ').any { it.equals("toc", true) } }
        } ?: navRoot?.descendants("nav")?.firstOrNull()
        val orderedList = nav?.children?.firstOrNull { it.localName == "ol" } ?: nav?.firstDescendant("ol")
        if (orderedList != null) {
            val entries = mutableListOf<SharedEpubTocEntry>()
            fun visit(list: SharedEpubXmlNode, depth: Int) {
                list.children.filter { it.localName == "li" }.forEach { item ->
                    val anchor = item.children.firstOrNull { it.localName == "a" }
                        ?: item.firstDescendant("a")
                    val href = anchor?.attribute("href").orEmpty()
                    if (href.isNotBlank()) {
                        entries += tocEntry(
                            label = anchor?.textContent().orEmpty(),
                            rawHref = href,
                            ownerPath = navItem.path,
                            depth = depth,
                            fallbackIndex = entries.size
                        )
                    }
                    val nestedLists = item.children.filter { it.localName == "ol" }.ifEmpty {
                        item.children.flatMap { child -> child.descendants("ol").take(1).toList() }
                    }
                    nestedLists.forEach { visit(it, depth + 1) }
                }
            }
            visit(orderedList, 0)
            if (entries.isNotEmpty()) return entries
        }
    }

    val tocId = packageRoot.firstDescendant("spine")?.attribute("toc")
    val ncxPath = tocId?.let(manifest::get)?.path
        ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" || it.path.endsWith(".ncx", true) }?.path
        ?: return emptyList()
    val ncx = archiveText(ncxPath)?.let(::parseSharedEpubXml) ?: return emptyList()
    val navMap = ncx.firstDescendant("navmap") ?: return emptyList()
    val entries = mutableListOf<SharedEpubTocEntry>()
    fun visit(parent: SharedEpubXmlNode, depth: Int) {
        parent.children.filter { it.localName == "navpoint" }.forEach { point ->
            val label = point.firstDescendant("navlabel")?.firstDescendant("text")?.textContent().orEmpty()
            val href = point.firstDescendant("content")?.attribute("src").orEmpty()
            if (href.isNotBlank()) {
                entries += tocEntry(label, href, ncxPath, depth, entries.size)
            }
            visit(point, depth + 1)
        }
    }
    visit(navMap, 0)
    return entries
}

private fun tocEntry(
    label: String,
    rawHref: String,
    ownerPath: String,
    depth: Int,
    fallbackIndex: Int
): SharedEpubTocEntry {
    val hrefWithoutFragment = rawHref.substringBefore('#').substringBefore('?')
    val fragment = rawHref.substringAfter('#', missingDelimiterValue = "")
        .substringBefore('?')
        .percentDecodeEpubPath()
        .takeIf(String::isNotBlank)
    return SharedEpubTocEntry(
        label = label.decodeEpubEntities().normalizeEpubWhitespace().ifBlank { "Section ${fallbackIndex + 1}" },
        href = resolveEpubPath(ownerPath, hrefWithoutFragment),
        fragmentId = fragment,
        depth = depth.coerceAtLeast(0)
    )
}

private data class SharedEpubXmlNode(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: MutableList<SharedEpubXmlNode> = mutableListOf(),
    val content: MutableList<SharedEpubXmlContent> = mutableListOf()
) {
    val localName: String get() = name.substringAfter(':').lowercase()

    fun attribute(name: String): String? = attributes.entries
        .firstOrNull { it.key.substringAfter(':').equals(name, ignoreCase = true) }
        ?.value

    fun descendants(localName: String? = null): Sequence<SharedEpubXmlNode> = sequence {
        children.forEach { child ->
            if (localName == null || child.localName == localName.lowercase()) yield(child)
            yieldAll(child.descendants(localName))
        }
    }

    fun firstDescendant(localName: String): SharedEpubXmlNode? = descendants(localName).firstOrNull()

    fun appendText(value: String) {
        if (value.isEmpty()) return
        val existing = content.lastOrNull() as? SharedEpubXmlContent.Text
        if (existing != null) existing.value.append(value) else content += SharedEpubXmlContent.Text(StringBuilder(value))
    }

    fun textContent(): String = buildString {
        content.forEach { part ->
            when (part) {
                is SharedEpubXmlContent.Text -> append(part.value)
                is SharedEpubXmlContent.Child -> append(part.node.textContent())
            }
        }
    }
}

private sealed interface SharedEpubXmlContent {
    data class Text(val value: StringBuilder) : SharedEpubXmlContent
    data class Child(val node: SharedEpubXmlNode) : SharedEpubXmlContent
}

private fun parseSharedEpubXml(raw: String): SharedEpubXmlNode? {
    val root = SharedEpubXmlNode("#document")
    val stack = ArrayDeque<SharedEpubXmlNode>().apply { addLast(root) }
    var cursor = 0
    sharedEpubXmlTokens(raw).forEach { match ->
        if (match.start > cursor) stack.last().appendText(raw.substring(cursor, match.start))
        val token = match.value
        when {
            token.startsWith("<!--") || token.startsWith("<?") || token.startsWith("<!DOCTYPE", true) -> Unit
            token.startsWith("<![CDATA[") -> stack.last().appendText(token.removePrefix("<![CDATA[").removeSuffix("]]>") )
            token.startsWith("</") -> {
                val closingName = token.removePrefix("</").substringBefore('>').trim().substringAfter(':')
                val matchingIndex = stack.indexOfLast { it.localName.equals(closingName, true) }
                if (matchingIndex > 0) {
                    repeat(stack.size - matchingIndex) { stack.removeLast() }
                }
            }
            token.startsWith("<") -> {
                val selfClosing = token.trimEnd().endsWith("/>")
                val inside = token.removePrefix("<").removeSuffix(">").removeSuffix("/").trim()
                val name = inside.takeWhile { !it.isWhitespace() }
                if (name.isNotBlank()) {
                    val attributes = EpubXmlAttributeRegex.findAll(inside.substring(name.length)).associate { attr ->
                        attr.groupValues[1] to attr.groupValues[3].decodeEpubEntities()
                    }
                    val node = SharedEpubXmlNode(name, attributes)
                    stack.last().children += node
                    stack.last().content += SharedEpubXmlContent.Child(node)
                    if (!selfClosing) stack.addLast(node)
                }
            }
        }
        cursor = match.endExclusive
    }
    if (cursor < raw.length) stack.last().appendText(raw.substring(cursor))
    return root.children.firstOrNull()
}

private data class SharedEpubXmlToken(
    val start: Int,
    val endExclusive: Int,
    val value: String
)

private fun sharedEpubXmlTokens(raw: String): Sequence<SharedEpubXmlToken> = sequence {
    var searchFrom = 0
    while (searchFrom < raw.length) {
        val start = raw.indexOf('<', searchFrom)
        if (start < 0) break
        val endExclusive = when {
            raw.startsWith("<!--", start) -> raw.indexOf("-->", start + 4).takeIf { it >= 0 }?.plus(3)
            raw.startsWith("<![CDATA[", start) -> raw.indexOf("]]>", start + 9).takeIf { it >= 0 }?.plus(3)
            raw.startsWith("<?", start) -> raw.indexOf("?>", start + 2).takeIf { it >= 0 }?.plus(2)
            raw.regionMatches(start, "<!DOCTYPE", 0, 9, ignoreCase = true) -> raw.sharedEpubTagEnd(start, trackDoctypeSubset = true)
            else -> raw.sharedEpubTagEnd(start, trackDoctypeSubset = false)
        } ?: break
        yield(SharedEpubXmlToken(start, endExclusive, raw.substring(start, endExclusive)))
        searchFrom = endExclusive
    }
}

private fun String.sharedEpubTagEnd(start: Int, trackDoctypeSubset: Boolean): Int? {
    var quote: Char? = null
    var subsetDepth = 0
    var index = start + 1
    while (index < length) {
        val char = this[index]
        if (quote != null) {
            if (char == quote) quote = null
        } else {
            when (char) {
                '\'', '"' -> quote = char
                '[' -> if (trackDoctypeSubset) subsetDepth++
                ']' -> if (trackDoctypeSubset && subsetDepth > 0) subsetDepth--
                '>' -> if (!trackDoctypeSubset || subsetDepth == 0) return index + 1
            }
        }
        index++
    }
    return null
}

private val EpubXmlAttributeRegex = Regex("""(?is)([:\w.-]+)\s*=\s*([\"'])(.*?)\2""")
private val EpubCssImportRegex = Regex(
    """@import\s+(?:url\(\s*)?(?:\"([^\"]+)\"|'([^']+)'|([^\)\s;]+))\s*\)?\s*([^;]*);""",
    RegexOption.IGNORE_CASE
)
private val EpubCssUrlRegex = Regex("""url\(\s*([\"']?)([^\"')]+)\1\s*\)""", RegexOption.IGNORE_CASE)
private val EpubVoidElementNames = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")

private fun String.rewriteEpubCssUrls(ownerPath: String, dataUri: (String) -> String?): String {
    return replace(EpubCssUrlRegex) { match ->
        val raw = match.groupValues[2].trim()
        val path = epubResourcePath(raw, ownerPath) ?: return@replace match.value
        val resolvedDataUri = dataUri(path) ?: return@replace match.value
        val fragment = raw.substringAfter('#', missingDelimiterValue = "").takeIf(String::isNotBlank)
        val uri = resolvedDataUri + fragment?.let { "#$it" }.orEmpty()
        "url('$uri')"
    }
}

private fun String.sanitizeEpubCss(): String =
    replace(Regex("(?i)</style"), "<\\/style")
        .replace(Regex("(?i)javascript\\s*:"), "")
        .replace(Regex("(?i)expression\\s*\\("), "blocked(")

private fun String.rewriteEpubHtmlResources(ownerPath: String, dataUri: (String) -> String?): String {
    var output = replace(Regex("""(?is)\b(src|poster|href|xlink:href)\s*=\s*([\"'])(.*?)\2""")) { match ->
        val attribute = match.groupValues[1]
        val raw = match.groupValues[3].trim().decodeEpubEntities()
        val path = epubResourcePath(raw, ownerPath) ?: return@replace match.value
        if (attribute.equals("href", true) && !path.isEpubEmbeddableResource()) return@replace match.value
        val fragment = raw.substringAfter('#', missingDelimiterValue = "").takeIf(String::isNotBlank)
        val uri = (dataUri(path) ?: return@replace match.value) + fragment?.let { "#$it" }.orEmpty()
        "$attribute=\"$uri\""
    }
    output = output.replace(Regex("""(?is)\bsrcset\s*=\s*([\"'])(.*?)\1""")) { match ->
        val rawSrcSet = match.groupValues[2]
        if (rawSrcSet.contains("data:", ignoreCase = true)) return@replace match.value
        val candidates = rawSrcSet.split(',').map { candidate ->
            val pieces = candidate.trim().split(Regex("\\s+"), limit = 2)
            val originalSource = pieces.firstOrNull().orEmpty()
            val source = originalSource.decodeEpubEntities()
            val path = epubResourcePath(source, ownerPath)
            val fragment = source.substringAfter('#', missingDelimiterValue = "").takeIf(String::isNotBlank)
            val uri = path?.let(dataUri)?.plus(fragment?.let { "#$it" }.orEmpty()) ?: originalSource
            listOf(uri, pieces.getOrNull(1)).filterNotNull().joinToString(" ")
        }
        "srcset=\"${candidates.joinToString(", ")}\""
    }
    return output.rewriteEpubCssUrls(ownerPath, dataUri)
}

private fun String.sanitizeEpubReaderHtml(): String {
    return replace(Regex("(?is)<script\\b.*?</script>"), "")
        .replace(Regex("(?is)<script\\b[^>]*/>"), "")
        .replace(Regex("(?is)<style\\b.*?</style>"), "")
        .replace(Regex("(?is)<(?:object|iframe)\\b.*?</(?:object|iframe)>"), "")
        .replace(Regex("(?is)<(?:object|iframe|embed)\\b[^>]*/?>"), "")
        .replace(Regex("(?is)</?form\\b[^>]*>"), "")
        .replace(Regex("""(?is)\s+on[a-z][\w:.-]*\s*=\s*(?:\"[^\"]*\"|'[^']*'|[^\s>]+)"""), "")
        .replace(Regex("""(?is)\s+srcdoc\s*=\s*(?:\"[^\"]*\"|'[^']*'|[^\s>]+)"""), "")
        .replace(Regex("""(?is)\s+(?:src|href|xlink:href|action|formaction)\s*=\s*([\"'])\s*(?:javascript|vbscript):.*?\1"""), "")
        .expandEpubSelfClosingElements()
}

private fun String.expandEpubSelfClosingElements(): String {
    return replace(Regex("(?is)<([a-z][\\w:.-]*)([^<>]*?)/\\s*>")) { match ->
        val qualifiedName = match.groupValues[1]
        if (qualifiedName.substringAfter(':').lowercase() in EpubVoidElementNames) {
            match.value
        } else {
            "<$qualifiedName${match.groupValues[2]}></$qualifiedName>"
        }
    }
}

private fun String.extractEpubBodyOrSelf(): String {
    val tokens = sharedEpubXmlTokens(this).toList()
    fun openingName(token: SharedEpubXmlToken): String? {
        val value = token.value
        if (!value.startsWith('<') || value.startsWith("</") || value.startsWith("<!") || value.startsWith("<?")) return null
        return value.drop(1).trimStart().takeWhile { !it.isWhitespace() && it != '/' && it != '>' }
    }
    fun closingName(token: SharedEpubXmlToken): String? {
        if (!token.value.startsWith("</")) return null
        return token.value.drop(2).trimStart().takeWhile { !it.isWhitespace() && it != '>' }
    }
    val bodyStart = tokens.firstOrNull { openingName(it)?.substringAfter(':').equals("body", ignoreCase = true) }
        ?: return this
    val bodyEnd = tokens.firstOrNull {
        it.start >= bodyStart.endExclusive && closingName(it)?.substringAfter(':').equals("body", ignoreCase = true)
    } ?: return this
    val bodyName = openingName(bodyStart).orEmpty()
    val bodyAttributes = bodyStart.value
        .removePrefix("<")
        .removeSuffix(">")
        .removeSuffix("/")
        .trim()
        .removePrefix(bodyName)
    val htmlStart = tokens.firstOrNull { openingName(it)?.substringAfter(':').equals("html", ignoreCase = true) }
    val htmlName = htmlStart?.let(::openingName).orEmpty()
    val htmlAttributes = htmlStart?.value
        ?.removePrefix("<")
        ?.removeSuffix(">")
        ?.removeSuffix("/")
        ?.trim()
        ?.removePrefix(htmlName)
        .orEmpty()
    fun attributes(raw: String): Map<String, String> = EpubXmlAttributeRegex.findAll(raw).associate { match ->
        match.groupValues[1].substringAfter(':').lowercase() to match.groupValues[3].decodeEpubEntities()
    }
    val bodyValues = attributes(bodyAttributes)
    val htmlValues = attributes(htmlAttributes)
    val bodyId = bodyValues["id"]?.trim()?.takeIf(String::isNotBlank)
    val cssClass = bodyValues["class"]?.trim()?.takeIf(String::isNotBlank)
    val direction = (bodyValues["dir"] ?: htmlValues["dir"])
        ?.lowercase()
        ?.takeIf { it == "ltr" || it == "rtl" || it == "auto" }
    val language = (bodyValues["lang"] ?: htmlValues["lang"])
        ?.trim()
        ?.takeIf(String::isNotBlank)
    val style = bodyValues["style"]?.sanitizeEpubCss()?.trim()?.takeIf(String::isNotBlank)
    val wrapperAttributes = buildString {
        append(" class=\"reader-epub-body")
        cssClass?.let { append(" ").append(it.escapeEpubAttribute()) }
        append('"')
        bodyId?.let { append(" id=\"").append(it.escapeEpubAttribute()).append('"') }
        direction?.let { append(" dir=\"").append(it).append('"') }
        language?.let { append(" lang=\"").append(it.escapeEpubAttribute()).append('"') }
        style?.let { append(" style=\"").append(it.escapeEpubAttribute()).append('"') }
    }
    return "<div$wrapperAttributes>${substring(bodyStart.endExclusive, bodyEnd.start).trim()}</div>"
}

private fun String.extractEpubStyleBlocks(): String {
    return Regex("(?is)<style\\b[^>]*>(.*?)</style>")
        .findAll(this)
        .joinToString("\n") { it.groupValues[1] }
}

private fun String.epubHtmlToText(): String {
    return replace(Regex("(?is)<style\\b.*?</style>"), " ")
        .replace(Regex("(?i)<\\s*br\\s*/?\\s*>"), "\n")
        .replace(Regex("(?i)</\\s*(p|div|section|article|aside|main|header|footer|h[1-6]|li|tr|table|blockquote|ul|ol)\\s*>"), "\n")
        .replace(Regex("(?is)<[^>]+>"), " ")
        .decodeEpubEntities()
        .normalizeEpubWhitespace()
}

private fun String.firstEpubHeading(): String? {
    return (1..6).firstNotNullOfOrNull { level -> epubTagText("h$level") }
}

private fun String.epubTagText(tag: String): String? {
    return Regex("(?is)<(?:[^:>]+:)?$tag\\b[^>]*>(.*?)</(?:[^:>]+:)?$tag>")
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.epubHtmlToText()
        ?.takeIf(String::isNotBlank)
}

private fun String.hasEpubVisualContent(): Boolean = contains(
    Regex("""<\s*(img|svg|math|video|audio|picture|canvas)\b""", RegexOption.IGNORE_CASE)
)

private fun String.isEpubEmbeddableResource(): Boolean {
    return substringAfterLast('.', "").lowercase() in setOf(
        "css", "jpg", "jpeg", "png", "gif", "svg", "webp", "avif",
        "ttf", "otf", "woff", "woff2", "mp3", "m4a", "aac", "ogg", "wav", "mp4", "webm"
    )
}

private fun epubResourcePath(raw: String, ownerPath: String): String? {
    val ref = raw.substringBefore('#').substringBefore('?').trim()
    if (ref.isBlank() || ref.startsWith("data:", true) || ref.startsWith("blob:", true)) return null
    if (ref.startsWith("http://", true) || ref.startsWith("https://", true) || ref.startsWith("mailto:", true)) return null
    return resolveEpubPath(ownerPath, ref)
}

private fun resolveEpubPath(ownerPath: String, reference: String): String {
    val decoded = reference.substringBefore('#').substringBefore('?').percentDecodeEpubPath()
    val base = ownerPath.substringBeforeLast('/', missingDelimiterValue = "")
    return safeEpubPathOrNull(if (decoded.startsWith('/')) decoded.removePrefix("/") else if (base.isBlank()) decoded else "$base/$decoded")
        ?: error("Unsafe EPUB resource path: $reference")
}

private fun safeEpubPathOrNull(path: String): String? {
    val parts = ArrayDeque<String>()
    path.replace('\\', '/').split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeLast()
            else -> {
                if ('\u0000' in part) return null
                parts.addLast(part)
            }
        }
    }
    return parts.joinToString("/").takeIf(String::isNotBlank)
}

private fun String.percentDecodeEpubPath(): String {
    val output = ArrayList<Byte>(length)
    var index = 0
    while (index < length) {
        val char = this[index]
        if (char == '%' && index + 2 < length) {
            val value = substring(index + 1, index + 3).toIntOrNull(16)
            if (value != null) {
                output += value.toByte()
                index += 3
                continue
            }
        }
        output += char.toString().encodeToByteArray().toList()
        index++
    }
    return output.toByteArray().decodeToString()
}

private fun String.decodeEpubEntities(): String {
    return replace(Regex("&#(?:x([0-9a-fA-F]+)|([0-9]+));")) { match ->
        val codePoint = match.groupValues[1].takeIf(String::isNotBlank)?.toIntOrNull(16)
            ?: match.groupValues[2].toIntOrNull()
        codePoint?.takeIf { it in 0..0x10FFFF && it !in 0xD800..0xDFFF }?.let(::epubCodePointToString) ?: match.value
    }.replace(Regex("&([A-Za-z][A-Za-z0-9]+);")) { match ->
        EpubNamedEntities[match.groupValues[1].lowercase()] ?: match.value
    }
}

private val EpubNamedEntities = mapOf(
    "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "ensp" to " ", "emsp" to " ", "thinsp" to " ", "shy" to "", "ndash" to "–", "mdash" to "—",
    "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»",
    "hellip" to "…", "bull" to "•", "middot" to "·", "copy" to "©", "reg" to "®", "trade" to "™"
)

private fun String.escapeEpubAttribute(): String = replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun epubCodePointToString(codePoint: Int): String {
    if (codePoint <= 0xFFFF) return codePoint.toChar().toString()
    val value = codePoint - 0x10000
    return charArrayOf(((value ushr 10) + 0xD800).toChar(), ((value and 0x3FF) + 0xDC00).toChar()).concatToString()
}

private fun String.normalizeEpubWhitespace(): String {
    return replace('\u0000', ' ')
        .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        .replace(Regex(" *\\n *"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun ByteArray.toEpubDataUri(mimeType: String): String {
    return "data:$mimeType;base64,${Base64.Default.encode(this)}"
}

private fun epubMimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "xhtml", "html", "htm" -> "application/xhtml+xml"
    "css" -> "text/css"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "svg" -> "image/svg+xml"
    "webp" -> "image/webp"
    "avif" -> "image/avif"
    "ttf" -> "font/ttf"
    "otf" -> "font/otf"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "aac" -> "audio/aac"
    "ogg" -> "audio/ogg"
    "wav" -> "audio/wav"
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    else -> "application/octet-stream"
}

private fun ByteArray.sha1(): ByteArray {
    val bitLength = size.toLong() * 8L
    val paddedLength = ((size + 9 + 63) / 64) * 64
    val input = ByteArray(paddedLength)
    copyInto(input)
    input[size] = 0x80.toByte()
    repeat(8) { index -> input[paddedLength - 1 - index] = (bitLength ushr (index * 8)).toByte() }
    var h0 = 0x67452301
    var h1 = 0xEFCDAB89.toInt()
    var h2 = 0x98BADCFE.toInt()
    var h3 = 0x10325476
    var h4 = 0xC3D2E1F0.toInt()
    val words = IntArray(80)
    for (chunk in input.indices step 64) {
        repeat(16) { index ->
            val offset = chunk + index * 4
            words[index] = ((input[offset].toInt() and 0xFF) shl 24) or
                ((input[offset + 1].toInt() and 0xFF) shl 16) or
                ((input[offset + 2].toInt() and 0xFF) shl 8) or
                (input[offset + 3].toInt() and 0xFF)
        }
        for (index in 16 until 80) words[index] = (words[index - 3] xor words[index - 8] xor words[index - 14] xor words[index - 16]).rotateLeft(1)
        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        repeat(80) { index ->
            val (f, k) = when (index) {
                in 0..19 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                in 20..39 -> (b xor c xor d) to 0x6ED9EBA1
                in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                else -> (b xor c xor d) to 0xCA62C1D6.toInt()
            }
            val temp = a.rotateLeft(5) + f + e + k + words[index]
            e = d
            d = c
            c = b.rotateLeft(30)
            b = a
            a = temp
        }
        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
    }
    return listOf(h0, h1, h2, h3, h4).flatMap { it.toBigEndianBytes().asIterable() }.toByteArray()
}

private fun Int.toBigEndianBytes(): ByteArray = byteArrayOf(
    (this ushr 24).toByte(),
    (this ushr 16).toByte(),
    (this ushr 8).toByte(),
    toByte()
)
