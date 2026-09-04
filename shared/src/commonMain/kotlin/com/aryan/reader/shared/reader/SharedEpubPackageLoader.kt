@file:OptIn(ExperimentalEncodingApi::class)

package com.aryan.reader.shared.reader

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.aryan.reader.shared.sharedHtmlToPlainText

/**
 * Platform-neutral view of an EPUB ZIP archive. Platform source sets only provide archive I/O;
 * package parsing, resource resolution, sanitizing, and book construction stay shared.
 */
interface SharedEpubArchive {
    val entryPaths: Set<String>

    fun readBytes(path: String): ByteArray?

    fun readText(path: String): String? = readBytes(path)?.decodeToString()
}

private class ParsedMobileEpubPackage(
    private val archive: SharedEpubArchive,
    val entryPaths: List<String>,
    val packageRoot: SharedXmlDocumentNode,
    val metadata: SharedXmlDocumentNode,
    val manifest: Map<String, MobileEpubManifestItem>,
    val spineNode: SharedXmlDocumentNode,
    val spineIds: List<String>
) {
    private val entryPathSet = entryPaths.toSet()

    fun actualPath(path: String): String? {
        val safe = safeEpubPathOrNull(path) ?: return null
        return safe.takeIf { it in entryPathSet }
    }

    fun text(path: String): String? = actualPath(path)?.let(archive::readText)

    fun bytes(path: String): ByteArray? = actualPath(path)?.let(archive::readBytes)
}

private fun parseMobileEpubPackage(archive: SharedEpubArchive): ParsedMobileEpubPackage {
    val entryPaths = archive.entryPaths.mapNotNull(::safeEpubPathOrNull)
    val entryPathSet = entryPaths.toSet()
    fun text(path: String): String? {
        val safe = safeEpubPathOrNull(path)?.takeIf { it in entryPathSet } ?: return null
        return archive.readText(safe)
    }

    val container = text("META-INF/container.xml") ?: error("META-INF/container.xml file missing")
    val containerRoot = parseSharedXmlDocument(container)
    val rootfiles = containerRoot?.descendantsNamed("rootfile")?.toList().orEmpty()
    val opfPath = resolveMobileEpubOpfPath(
        rootfiles.map { it.attribute("full-path")?.let(::decodeMobileEpubUrl) }
    )?.let(::safeEpubPathOrNull)
        ?: error("Invalid container.xml: Could not find rootfile full-path")
    val opf = text(opfPath) ?: error("EPUB package document is missing: $opfPath")
    val packageRoot = parseSharedXmlDocument(opf) ?: error("EPUB package document is malformed: $opfPath")
    val metadata = packageRoot.firstDescendantNamed("metadata", "opf:metadata")
        ?: error("EPUB package metadata section is missing: $opfPath")
    val manifestNode = packageRoot.firstDescendantNamed("manifest", "opf:manifest")
        ?: error("EPUB package manifest section is missing: $opfPath")
    val spineNode = packageRoot.firstDescendantNamed("spine", "opf:spine")
        ?: error("EPUB package spine section is missing: $opfPath")
    val manifest = manifestNode.children.filter { it.name == "item" }
        .ifEmpty { manifestNode.children.filter { it.name == "opf:item" } }
        .mapNotNull { item ->
            val href = item.attribute("href") ?: return@mapNotNull null
            val id = item.attribute("id").orEmpty()
            id to MobileEpubManifestItem(
                id = id,
                absPath = resolveMobileEpubPackagePath(opfPath, href),
                mediaType = item.attribute("media-type").orEmpty(),
                properties = item.attribute("properties").orEmpty()
            )
        }
        .toMap()
    val spineIds = mobileEpubSpineItemIds(
        spineNode.children.filter { it.name == "itemref" }
            .ifEmpty { spineNode.children.filter { it.name == "opf:itemref" } }
            .map { it.attribute("idref") }
    )
    return ParsedMobileEpubPackage(
        archive = archive,
        entryPaths = entryPaths,
        packageRoot = packageRoot,
        metadata = metadata,
        manifest = manifest,
        spineNode = spineNode,
        spineIds = spineIds
    )
}

object SharedEpubPackageLoader {
    fun load(
        archive: SharedEpubArchive,
        sourceId: String,
        fileName: String,
        shouldUseToc: Boolean = true
    ): SharedEpubBook {
        val loadMark = sharedEpubOpenTraceMark()
        sharedEpubOpenTrace { "packageLoad start sourceId=$sourceId fileName=$fileName entries=${archive.entryPaths.size}" }
        val parseStart = sharedEpubOpenTraceMark()
        val parsedPackage = parseMobileEpubPackage(archive)
        sharedEpubOpenTrace { "packageLoad containerParsedAndOpfParsed ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(parseStart))} spineItems=${parsedPackage.spineIds.size} manifestItems=${parsedPackage.manifest.size}" }
        val normalizedEntries = parsedPackage.entryPaths
        val normalizedEntrySet = normalizedEntries.toSet()
        fun text(path: String): String? = parsedPackage.text(path)
        fun bytes(path: String): ByteArray? = parsedPackage.bytes(path)
        val packageRoot = parsedPackage.packageRoot
        val metadata = parsedPackage.metadata
        val manifest = parsedPackage.manifest
        val spineNode = parsedPackage.spineNode
        val manifestMimeTypes = manifest.values.associate { it.absPath.lowercase() to it.mediaType }
        fun resourceMimeType(path: String): String = manifestMimeTypes[path.lowercase()]
            ?.takeIf(String::isNotBlank)
            ?: epubMimeType(path)

        val uniqueIdentifier = packageRoot.attribute("unique-identifier")
            ?.let { uniqueId -> metadata.descendants().firstOrNull { it.attribute("id") == uniqueId } }
            ?.textContent()
            ?.trim()
            .orEmpty()
        val resolvedMetadata = resolveMobileEpubMetadata(
            sourceFileName = fileName,
            title = metadata.firstChildNamed("dc:title")?.textContent()?.decodeEpubEntities(),
            author = metadata.firstChildNamed("dc:creator")?.textContent()?.decodeEpubEntities(),
            language = metadata.firstChildNamed("dc:language")?.textContent()?.decodeEpubEntities(),
            description = metadata.firstChildNamed("dc:description")?.textContent()?.decodeEpubEntities(),
            metaElements = metadata.androidMetadataChildren("meta", "opf:meta")
                .map(SharedXmlDocumentNode::toMobileEpubMetaElement)
        )
        val metadataCoverId = metadata.androidMetadataChildren("meta", "opf:meta")
            .firstOrNull { it.attribute("name") == "cover" }
            ?.attribute("content")
        val images = mobileEpubImages(manifest.values.toList(), normalizedEntries)
        val coverMark = sharedEpubOpenTraceMark()
        val coverImagePath = mobileEpubCoverCandidates(
            metadataCoverId = metadataCoverId,
            manifest = manifest.values.toList(),
            archivePaths = normalizedEntrySet
        ).firstOrNull { path -> bytes(path)?.isNotEmpty() == true }
        sharedEpubOpenTrace { "packageLoad cover ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(coverMark))} path=$coverImagePath" }

        val fontMark = sharedEpubOpenTraceMark()
        val fontObfuscation = parseFontObfuscation(text("META-INF/encryption.xml"), uniqueIdentifier)
        sharedEpubOpenTrace { "packageLoad fontObfuscationScan ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(fontMark))} rules=${fontObfuscation.size}" }
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
                if (isSharedEpubSchemeServableResource(resourcePath)) {
                    sharedEpubResourceUrl(sourceId, resourcePath)
                } else {
                    val content = if (resourcePath.endsWith(".css", ignoreCase = true)) {
                        css(resourcePath)?.encodeToByteArray()
                    } else {
                        resourceBytes(resourcePath)
                    }
                    content?.toEpubDataUri(resourceMimeType(resourcePath))
                }
            }
            processingCss.remove(safe)
            processedCss[safe] = output
            return output
        }

        val cssPaths = mobileEpubCssPaths(manifest.values.toList(), normalizedEntries)
        val cssMark = sharedEpubOpenTraceMark()
        var cssBytesInlined = 0L
        cssPaths.forEach { path ->
            val mark = sharedEpubOpenTraceMark()
            val processed = css(path)
            if (processed != null) cssBytesInlined += processed.length
            sharedEpubOpenTrace {
                "packageLoad css ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(mark))} path=$path chars=${processed?.length ?: 0}"
            }
        }
        sharedEpubOpenTrace { "packageLoad cssPass ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(cssMark))} stylesheets=${cssPaths.size} processedTotalChars=$cssBytesInlined" }

        val spineIds = parsedPackage.spineIds
        val chapterItems = spineIds.mapNotNull(manifest::get)

        fun dataUri(path: String): String? {
            val safe = safeEpubPathOrNull(path) ?: return null
            if (isSharedEpubSchemeServableResource(safe)) {
                return sharedEpubResourceUrl(sourceId, safe)
            }
            val content = if (safe.endsWith(".css", ignoreCase = true)) {
                css(safe)?.encodeToByteArray()
            } else {
                resourceBytes(safe)
            } ?: return null
            return content.toEpubDataUri(resourceMimeType(safe))
        }

        val navigationMark = sharedEpubOpenTraceMark()
        val navigation = if (shouldUseToc) {
            parseEpubNavigation(
                archiveText = ::text,
                spineNode = spineNode,
                manifest = manifest
            )
        } else {
            ParsedEpubNavigation()
        }
        sharedEpubOpenTrace {
            "packageLoad navigation ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(navigationMark))} " +
                "tocEntries=${navigation.tableOfContents.size} pageList=${navigation.pageList.size}"
        }
        val parsedToc = navigation.tableOfContents
        val tocByPath = parsedToc
            .filter { !it.fragmentId.isNullOrBlank() }
            .groupBy(SharedEpubTocEntry::href)
        val navigationMetadata = navigation.chapterMetadata

        val chaptersMark = sharedEpubOpenTraceMark()
        var spineHtmlCount = 0
        var styleBlockTotalMs = 0.0
        var sanitizeTotalMs = 0.0
        var bodyExtractTotalMs = 0.0
        var resourceRewriteTotalMs = 0.0
        var plainTextTotalMs = 0.0
        var semanticBlocksTotalMs = 0.0
        var tocSectionsTotalMs = 0.0
        var slowestChapterMs = 0.0
        var slowestChapterHref: String? = null
        val parsedChapters = chapterItems.flatMapIndexed { index, item ->
            val chapterMark = sharedEpubOpenTraceMark()
            fun finishChapter(chapters: List<SharedEpubChapter>): List<SharedEpubChapter> {
                val chapterMs = sharedEpubOpenTraceElapsedMs(chapterMark)
                if (chapterMs > slowestChapterMs) {
                    slowestChapterMs = chapterMs
                    slowestChapterHref = item.absPath
                }
                return chapters
            }
            if (!item.isHtml) {
                if (!item.isImage) return@flatMapIndexed finishChapter(emptyList())
                val uriMark = sharedEpubOpenTraceMark()
                val uri = dataUri(item.absPath) ?: return@flatMapIndexed finishChapter(emptyList())
                val uriMs = sharedEpubOpenTraceElapsedMs(uriMark)
                val navigation = resolveMobileEpubChapterNavigation(item.absPath, "Image", navigationMetadata)
                sharedEpubOpenTrace { "chapter index=$index path=${item.absPath} kind=image inlinedChars=${uri.length} ms=${sharedEpubOpenTraceMs(uriMs)}" }
                return@flatMapIndexed finishChapter(listOf(SharedEpubChapter(
                    id = item.id.ifBlank { "chapter_$index" },
                    title = navigation.title ?: "Image",
                    plainText = "[Image]",
                    htmlContent = "<figure class=\"reader-epub-image-page\"><img src=\"${uri.escapeEpubAttribute()}\" alt=\"${(navigation.title ?: "Image").escapeEpubAttribute()}\"></figure>",
                    baseHref = item.absPath,
                    depth = navigation.depth,
                    isInToc = navigation.isInToc
                )))
            }
            spineHtmlCount++
            val raw = text(item.absPath) ?: return@flatMapIndexed finishChapter(emptyList())
            val styleMark = sharedEpubOpenTraceMark()
            raw.extractEpubStyleBlocks().takeIf(String::isNotBlank)?.let { embeddedCss ->
                processedCss["${item.absPath}#embedded-style"] = embeddedCss.sanitizeEpubCss().rewriteEpubCssUrls(item.absPath) { resourcePath ->
                    if (isSharedEpubSchemeServableResource(resourcePath)) {
                        sharedEpubResourceUrl(sourceId, resourcePath)
                    } else {
                        resourceBytes(resourcePath)?.toEpubDataUri(resourceMimeType(resourcePath))
                    }
                }
            }
            val styleMs = sharedEpubOpenTraceElapsedMs(styleMark)
            styleBlockTotalMs += styleMs
            val sanitizeMark = sharedEpubOpenTraceMark()
            val sanitizedBody = raw.sanitizeEpubReaderHtml()
            val sanitizeMs = sharedEpubOpenTraceElapsedMs(sanitizeMark)
            sanitizeTotalMs += sanitizeMs
            val bodyMark = sharedEpubOpenTraceMark()
            val bodyOnly = sanitizedBody.extractEpubBodyOrSelf()
            val bodyMs = sharedEpubOpenTraceElapsedMs(bodyMark)
            bodyExtractTotalMs += bodyMs
            val rewriteMark = sharedEpubOpenTraceMark()
            val body = bodyOnly.rewriteEpubHtmlResources(item.absPath, ::dataUri)
            val rewriteMs = sharedEpubOpenTraceElapsedMs(rewriteMark)
            resourceRewriteTotalMs += rewriteMs
            val plainTextMark = sharedEpubOpenTraceMark()
            val plainText = raw.epubHtmlToText()
            val plainTextMs = sharedEpubOpenTraceElapsedMs(plainTextMark)
            plainTextTotalMs += plainTextMs
            val semanticMark = sharedEpubOpenTraceMark()
            val semanticBlocks = sharedEpubHtmlToSemanticBlocks(body)
            val semanticMs = sharedEpubOpenTraceElapsedMs(semanticMark)
            semanticBlocksTotalMs += semanticMs
            val fallbackTitle = resolveMobileEpubSpineChapterTitle(raw.firstEpubHeading(), index)
            val navigation = resolveMobileEpubChapterNavigation(item.absPath, fallbackTitle, navigationMetadata)
            val sectionsMark = sharedEpubOpenTraceMark()
            val sections = materializeEpubTocSections(
                body = body,
                entries = tocByPath[item.absPath].orEmpty()
            )
            val sectionsMs = sharedEpubOpenTraceElapsedMs(sectionsMark)
            tocSectionsTotalMs += sectionsMs
            sharedEpubOpenTrace {
                "chapter index=$index path=${item.absPath} kind=html rawChars=${raw.length} bodyChars=${body.length} sections=${sections.size} " +
                    "ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(chapterMark))} " +
                    "(style=${sharedEpubOpenTraceMs(styleMs)} sanitize=${sharedEpubOpenTraceMs(sanitizeMs)} body=${sharedEpubOpenTraceMs(bodyMs)} " +
                    "rewrite=${sharedEpubOpenTraceMs(rewriteMs)} text=${sharedEpubOpenTraceMs(plainTextMs)} " +
                    "semantic=${sharedEpubOpenTraceMs(semanticMs)} tocSplit=${sharedEpubOpenTraceMs(sectionsMs)})"
            }
            finishChapter(if (sections.isEmpty()) {
                listOf(
                    SharedEpubChapter(
                        id = item.id.ifBlank { "chapter_$index" },
                        title = navigation.title ?: fallbackTitle,
                        plainText = plainText,
                        semanticBlocks = semanticBlocks,
                        htmlContent = body,
                        baseHref = item.absPath,
                        depth = navigation.depth,
                        isInToc = navigation.isInToc
                    )
                )
            } else {
                sections.mapIndexed { sectionIndex, section ->
                    val sectionMark = sharedEpubOpenTraceMark()
                    val sectionText = section.html.epubHtmlToText().ifBlank { fallbackTitle }
                    plainTextTotalMs += sharedEpubOpenTraceElapsedMs(sectionMark)
                    val sectionSemanticMark = sharedEpubOpenTraceMark()
                    val sectionSemantic = sharedEpubHtmlToSemanticBlocks(section.html)
                    semanticBlocksTotalMs += sharedEpubOpenTraceElapsedMs(sectionSemanticMark)
                    SharedEpubChapter(
                        id = "${item.id.ifBlank { "chapter_$index" }}#${section.fragmentId}",
                        title = section.entry.label.ifBlank { fallbackTitle },
                        plainText = sectionText,
                        semanticBlocks = sectionSemantic,
                        htmlContent = section.html,
                        baseHref = item.absPath,
                        fragmentId = section.fragmentId,
                        depth = section.entry.depth,
                        isInToc = true
                    )
                }
            })
        }
        sharedEpubOpenTrace {
            "chapterLoop ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(chaptersMark))} spineItems=${chapterItems.size} htmlChapters=$spineHtmlCount " +
                "styleBlocks=${sharedEpubOpenTraceMs(styleBlockTotalMs)} sanitize=${sharedEpubOpenTraceMs(sanitizeTotalMs)} " +
                "bodyExtract=${sharedEpubOpenTraceMs(bodyExtractTotalMs)} resourceRewrite=${sharedEpubOpenTraceMs(resourceRewriteTotalMs)} " +
                "plainText=${sharedEpubOpenTraceMs(plainTextTotalMs)} semanticBlocks=${sharedEpubOpenTraceMs(semanticBlocksTotalMs)} " +
                "tocSections=${sharedEpubOpenTraceMs(tocSectionsTotalMs)} slowest=$slowestChapterHref (${sharedEpubOpenTraceMs(slowestChapterMs)}ms)"
        }
        val titleResolveMark = sharedEpubOpenTraceMark()
        val resolvedToc = parsedToc
        val chapters = parsedChapters.map { chapter ->
            val tocTitle = resolvedToc.firstOrNull {
                it.href == chapter.baseHref &&
                    (chapter.fragmentId == null || it.fragmentId == chapter.fragmentId) &&
                    it.label.isNotBlank()
            }?.label
            if (tocTitle == null) chapter else chapter.copy(title = tocTitle)
        }
        sharedEpubOpenTrace { "packageLoad tocTitleResolve ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(titleResolveMark))} chapters=${chapters.size} tocEntries=${resolvedToc.size}" }

        return SharedEpubBook(
            id = sourceId,
            fileName = resolvedMetadata.fileName,
            title = resolvedMetadata.title,
            author = resolvedMetadata.author,
            chapters = chapters,
            css = processedCss.toMap(),
            tableOfContents = resolvedToc,
            pageList = navigation.pageList,
            images = images,
            coverImagePath = coverImagePath,
            language = resolvedMetadata.language,
            seriesName = resolvedMetadata.seriesName,
            seriesIndex = resolvedMetadata.seriesIndex,
            description = resolvedMetadata.description
        ).also { book ->
            sharedEpubOpenTrace {
                "packageLoad done ms=${sharedEpubOpenTraceMs(sharedEpubOpenTraceElapsedMs(loadMark))} chapters=${book.chapters.size} " +
                    "htmlChars=${book.chapters.sumOf { it.htmlContent.length }} cssMaps=${book.css.size} cssChars=${book.css.values.sumOf { it.length }}"
            }
        }
    }
}

private val MobileEpubManifestItem.isHtml: Boolean
    get() = mobileEpubSpineResourceKind(this) == MobileEpubSpineResourceKind.HTML

private val MobileEpubManifestItem.isImage: Boolean
    get() = mobileEpubSpineResourceKind(this) == MobileEpubSpineResourceKind.IMAGE

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
    val distinctEntries = entries.distinctBy(SharedEpubTocEntry::fragmentId)
    val requestedFragments = distinctEntries.mapNotNull { it.fragmentId }.toSet()
    if (requestedFragments.size < 2) return emptyList()
    val tokens = sharedEpubXmlTokens(body).toList()
    val rootStart = tokens.firstOrNull { token ->
        token.value.startsWith('<') && !token.value.startsWith("</") &&
            token.value.drop(1).trimStart().startsWith("div", ignoreCase = true)
    } ?: return emptyList()
    val childRanges = mutableListOf<IntRange>()
    val fragmentIdChildIndex = mutableMapOf<String, Int>()
    val fragmentNameChildIndex = mutableMapOf<String, Int>()
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
            val id = attrs["id"]?.takeIf { it in requestedFragments }
            val name = attrs["name"]?.takeIf { it in requestedFragments }
            if (currentChildIndex >= 0) {
                if (id != null && id !in fragmentIdChildIndex) fragmentIdChildIndex[id] = currentChildIndex
                if (name != null && name !in fragmentNameChildIndex) fragmentNameChildIndex[name] = currentChildIndex
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

    val ranges = mobileEpubLogicalSectionRanges(
        entries = distinctEntries,
        bodyChildCount = childRanges.size,
        fragmentId = SharedEpubTocEntry::fragmentId,
        idChildIndex = fragmentIdChildIndex::get,
        nameChildIndex = fragmentNameChildIndex::get
    )
    return ranges.mapNotNull { range ->
        val entry = range.entry
        val startOffset = childRanges[range.startChildIndex].first
        val endOffset = childRanges[range.endChildIndexExclusive - 1].last + 1
        SharedEpubLogicalSection(
            entry = entry,
            fragmentId = entry.fragmentId ?: return@mapNotNull null,
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
    val root = encryptionXml?.let(::parseSharedXmlDocument) ?: return emptyMap()
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
        val algorithm = encrypted.firstDescendant("encryptionmethod")
            ?.attributeLocalIgnoreCase("algorithm").orEmpty()
        val uri = encrypted.firstDescendant("cipherreference")
            ?.attributeLocalIgnoreCase("uri").orEmpty()
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

private data class ParsedEpubNavigation(
    val tableOfContents: List<SharedEpubTocEntry> = emptyList(),
    val pageList: List<MobileEpubPageTarget> = emptyList(),
    val chapterMetadata: Map<String, MobileEpubNcxChapterMetadata> = emptyMap()
)

private fun parseEpubNavigation(
    archiveText: (String) -> String?,
    spineNode: SharedXmlDocumentNode,
    manifest: Map<String, MobileEpubManifestItem>
): ParsedEpubNavigation {
    val ncxId = resolveMobileEpubNcxManifestId(
        spineTocId = spineNode.attribute("toc"),
        manifest = manifest.values.toList()
    )
    val ncxPath = ncxId?.let(manifest::get)?.absPath
        ?: return ParsedEpubNavigation()
    val ncxText = archiveText(ncxPath) ?: return ParsedEpubNavigation()
    val ncx = parseSharedXmlDocument(ncxText) ?: error("EPUB NCX document is malformed: $ncxPath")

    fun nodes(parent: SharedXmlDocumentNode): List<MobileEpubNcxNavigationNode> =
        parent.children.filter { it.name == "navPoint" }.map { point ->
            val href = point.firstChildNamed("content")?.attribute("src")
            MobileEpubNcxNavigationNode(
                label = point.firstChildNamed("navLabel")?.firstChildNamed("text")
                    ?.textContent()?.decodeEpubEntities()?.trim(),
                absolutePath = href?.let { resolveMobileEpubPackagePath(ncxPath, it) },
                fragmentId = href?.substringAfter('#', missingDelimiterValue = "")
                    ?.substringBefore('?')?.let(::decodeMobileEpubUrl)?.takeIf(String::isNotBlank),
                children = nodes(point)
            )
        }

    val navigationNodes = ncx.descendantsNamed("navMap").firstOrNull()?.let(::nodes).orEmpty()
    val tableOfContents = flattenMobileEpubNcxNavigation(navigationNodes).map { entry ->
        SharedEpubTocEntry(entry.label, entry.absolutePath, entry.fragmentId, entry.depth)
    }

    val pageNodes = ncx.descendantsNamed("pageList").firstOrNull()?.children.orEmpty()
        .filter { it.name == "pageTarget" }
        .map { target ->
            val rawSrc = target.firstChildNamed("content")?.attribute("src")
            MobileEpubNcxPageNode(
                id = target.attribute("id"),
                value = target.attribute("value"),
                label = target.firstChildNamed("navLabel")?.firstChildNamed("text")
                    ?.textContent()?.decodeEpubEntities(),
                resolvedContentSrc = rawSrc?.takeIf(String::isNotBlank)?.let {
                    val suffix = it.drop(it.indexOfAny(charArrayOf('#', '?')).takeIf { index -> index >= 0 } ?: it.length)
                    resolveMobileEpubPackagePath(ncxPath, it) + decodeMobileEpubUrl(suffix)
                }
            )
        }
    return ParsedEpubNavigation(
        tableOfContents = tableOfContents,
        pageList = mobileEpubPageTargets(pageNodes),
        chapterMetadata = mobileEpubNcxChapterMetadata(navigationNodes)
    )
}

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
    output = output.replace(Regex("""(?is)\b(src|poster|href|xlink:href)\s*=\s*([^\s\"'=<>`]+)""")) { match ->
        val attribute = match.groupValues[1]
        val raw = match.groupValues[2].trim().decodeEpubEntities()
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
    return Regex("(?is)<(?:[^:>]+:)?h[1-6]\\b[^>]*>(.*?)</(?:[^:>]+:)?h[1-6]>")
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.epubHtmlToText()
        ?.takeIf(String::isNotBlank)
}

private fun String.epubTagText(tag: String): String? {
    return Regex("(?is)<(?:[^:>]+:)?$tag\\b[^>]*>(.*?)</(?:[^:>]+:)?$tag>")
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.epubHtmlToText()
        ?.takeIf(String::isNotBlank)
}

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

private fun resolveMobileEpubPackagePath(ownerPath: String, reference: String): String =
    resolveMobileEpubReference(ownerPath, decodeMobileEpubUrl(reference).substringBefore('#').substringBefore('?'))

private fun safeEpubPathOrNull(path: String): String? {
    if (path.startsWith('/')) return null
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

internal fun String.decodeEpubEntities(): String {
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

internal fun String.normalizeEpubWhitespace(): String {
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

/**
 * A plain-text-only spine document produced by [loadSharedEpubTtsChapters].
 */
data class SharedEpubTtsChapter(
    val id: String,
    val title: String,
    val plainText: String,
)

/**
 * Lightweight text-only EPUB extraction for text-to-speech. Mirrors the chapter
 * granularity of [load] (spine order, TOC section splitting, heading-based titles)
 * but skips all presentation work: CSS processing, image/font embedding, sanitizing,
 * and resource rewriting. Each spine document is read and its text is extracted as-is.
 */
fun loadSharedEpubTtsChapters(
    archive: SharedEpubArchive,
    fileName: String,
): List<SharedEpubTtsChapter> {
    val parsedPackage = parseMobileEpubPackage(archive)
    fun text(path: String): String? = parsedPackage.text(path)
    val manifest = parsedPackage.manifest
    val spineNode = parsedPackage.spineNode
    val spineIds = parsedPackage.spineIds
    val chapterItems = spineIds.mapNotNull(manifest::get)

    val tocByPath = parseEpubNavigation(
        archiveText = ::text,
        spineNode = spineNode,
        manifest = manifest
    ).tableOfContents
        .filter { !it.fragmentId.isNullOrBlank() }
        .groupBy(SharedEpubTocEntry::href)

    val chapters = chapterItems.flatMapIndexed { index, item ->
        if (!item.isHtml) return@flatMapIndexed emptyList()
        val raw = text(item.absPath) ?: return@flatMapIndexed emptyList()
        val body = raw.extractEpubBodyOrSelf()
        val fallbackTitle = resolveMobileEpubSpineChapterTitle(raw.firstEpubHeading(), index)
        val sections = materializeEpubTocSections(
            body = body,
            entries = tocByPath[item.absPath].orEmpty()
        )
        if (sections.isEmpty()) {
            val plainText = sharedHtmlToPlainText(raw)
            if (plainText.isBlank()) return@flatMapIndexed emptyList()
            listOf(
                SharedEpubTtsChapter(
                    id = item.id.ifBlank { "chapter_$index" },
                    title = fallbackTitle,
                    plainText = plainText,
                )
            )
        } else {
            sections.mapIndexedNotNull { sectionIndex, section ->
                val plainText = sharedHtmlToPlainText(section.html)
                if (plainText.isBlank()) return@mapIndexedNotNull null
                SharedEpubTtsChapter(
                    id = "${item.id.ifBlank { "chapter_$index" }}#${section.fragmentId}",
                    title = section.entry.label.ifBlank { fallbackTitle },
                    plainText = plainText,
                )
            }
        }
    }
    return chapters
}
