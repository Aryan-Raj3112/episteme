package com.aryan.reader.shared.reader

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

const val MOBILE_EPUB_MAX_METADATA_ENTRY_BYTES: Int = 4 * 1024 * 1024
const val MOBILE_EPUB_MAX_CACHED_BOOK_METADATA_BYTES: Long = 4L * 1024L * 1024L
const val MOBILE_EPUB_MAX_LOCATOR_ON_DEMAND_HTML_BYTES: Long = 2L * 1024L * 1024L
const val MOBILE_EPUB_MAX_LOCATOR_ON_DEMAND_HTML_CHARS: Int = 2 * 1024 * 1024

/**
 * The persisted EPUB chapter contract used by the Android reader.
 *
 * Keep this shape stable while the package loader moves into shared code: Android extraction
 * metadata and pagination protobuf payloads depend on these names, defaults, and field numbers.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("com.aryan.reader.epub.EpubChapter")
data class MobileEpubChapter @OptIn(ExperimentalSerializationApi::class) constructor(
    @ProtoNumber(1) val chapterId: String,
    @ProtoNumber(2) val absPath: String,
    @ProtoNumber(3) val title: String,
    @ProtoNumber(4) val htmlFilePath: String,
    @ProtoNumber(5) val plainTextContent: String,
    @ProtoNumber(6) val htmlContent: String,
    @ProtoNumber(7) val depth: Int = 0,
    @ProtoNumber(8) val isInToc: Boolean = true,
    @ProtoNumber(9) val plainTextLength: Int = plainTextContent.length
)

fun MobileEpubChapter.plainTextCharacterCount(): Int =
    maxOf(plainTextLength, plainTextContent.length)

/** Android's exact chapter projection for its lightweight extracted-book metadata cache. */
fun MobileEpubChapter.toMobileEpubExtractionCacheChapter(): MobileEpubChapter = copy(
    plainTextContent = "",
    htmlContent = "",
    plainTextLength = plainTextCharacterCount()
)

fun mobileEpubContentFilePath(path: String): String =
    path.substringBefore('#').substringBefore('?')

/** Android's extracted-cache readability decision after platform filesystem inspection. */
fun isMobileEpubExtractionCacheReadable(
    extractionBasePathPresent: Boolean,
    extractionDirectoryPresent: Boolean,
    chapterCount: Int,
    extractionDirectoryHasEntries: Boolean,
    allChapterFilesPresent: Boolean
): Boolean = when {
    !extractionBasePathPresent || !extractionDirectoryPresent -> false
    chapterCount == 0 -> extractionDirectoryHasEntries
    else -> allChapterFilesPresent
}

/** Android's WebView chapter scroll fraction, including short and not-yet-measured documents. */
fun mobileEpubChapterScrollFraction(
    scrollY: Int,
    scrollHeight: Int,
    clientHeight: Int
): Float = when {
    scrollHeight > clientHeight -> {
        val scrollableHeight = (scrollHeight - clientHeight).toFloat()
        if (scrollableHeight > 0f) (scrollY.toFloat() / scrollableHeight).coerceIn(0f, 1f) else 1f
    }
    scrollHeight > 0 -> 1f
    else -> 0f
}

/** Android's character-weighted whole-book progress after the platform resolves an offset. */
fun mobileEpubCharacterProgress(
    totalBookCharacters: Long,
    completedChapterCharacters: Long,
    currentChapterOffset: Long,
    isAtEndOfBook: Boolean
): Float {
    if (totalBookCharacters <= 0L) return 0f
    if (isAtEndOfBook) return 100f
    return (((completedChapterCharacters + currentChapterOffset).toDouble() / totalBookCharacters.toDouble()) * 100.0).toFloat()
}

/** Android's paginated footer percentage: capped above at 100 and floored to one decimal. */
fun mobileEpubCharacterDisplayProgress(
    totalBookCharacters: Long,
    completedChapterCharacters: Long,
    currentChapterOffset: Long,
    isLastPageOfBook: Boolean
): Double {
    if (isLastPageOfBook) return 100.0
    if (totalBookCharacters <= 0L) return 0.0
    val calculated =
        ((completedChapterCharacters + currentChapterOffset).toDouble() / totalBookCharacters.toDouble()) * 100.0
    return kotlin.math.floor(calculated.coerceAtMost(100.0) * 10.0) / 10.0
}

/** Exact shared owner of Android's flattened EPUB navigation entry contract. */
@Serializable
@SerialName("com.aryan.reader.epub.EpubTocEntry")
data class MobileEpubTocEntry(
    val label: String,
    val absolutePath: String,
    val fragmentId: String?,
    val depth: Int
)

data class MobileEpubLogicalSectionRange<T>(
    val entry: T,
    val startChildIndex: Int,
    val endChildIndexExclusive: Int,
    val materializationIndex: Int
)

/**
 * Android's exact fragment-section ordering and collision policy after a platform HTML adapter
 * maps fragment IDs/names to direct body-child indices.
 */
fun <T> mobileEpubLogicalSectionRanges(
    entries: List<T>,
    bodyChildCount: Int,
    fragmentId: (T) -> String?,
    idChildIndex: (String) -> Int?,
    nameChildIndex: (String) -> Int?
): List<MobileEpubLogicalSectionRange<T>> {
    if (entries.size < 2 || bodyChildCount < 2) return emptyList()
    val starts = entries.distinctBy(fragmentId).mapNotNull { entry ->
        val fragment = fragmentId(entry)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        (idChildIndex(fragment) ?: nameChildIndex(fragment))
            ?.takeIf { it in 0 until bodyChildCount }
            ?.let { entry to it }
    }.sortedBy { it.second }
    if (starts.size < 2 || starts.map { it.second }.distinct().size < 2) return emptyList()
    return starts.mapIndexedNotNull { index, (entry, startIndex) ->
        val endIndex = starts.getOrNull(index + 1)?.second ?: bodyChildCount
        if (startIndex >= endIndex) null else MobileEpubLogicalSectionRange(entry, startIndex, endIndex, index)
    }
}

/** Exact persisted NCX page-target shape consumed by Android's reader. */
@Serializable
@SerialName("com.aryan.reader.epub.EpubParser.EpubPageTarget")
data class MobileEpubPageTarget(
    val id: String?,
    val value: String?,
    val label: String?,
    val contentSrc: String
)

data class MobileEpubNcxPageNode(
    val id: String?,
    val value: String?,
    val label: String?,
    val resolvedContentSrc: String?
)

/** Android ignores NCX page targets without a content source and otherwise preserves order. */
fun mobileEpubPageTargets(nodes: List<MobileEpubNcxPageNode>): List<MobileEpubPageTarget> =
    nodes.mapNotNull { node ->
        node.resolvedContentSrc?.let { contentSrc ->
            MobileEpubPageTarget(node.id, node.value, node.label, contentSrc)
        }
    }

/** Platform-neutral NCX tree after the platform XML/path adapter has resolved each target. */
data class MobileEpubNcxNavigationNode(
    val label: String?,
    val absolutePath: String?,
    val fragmentId: String?,
    val children: List<MobileEpubNcxNavigationNode> = emptyList()
)

/**
 * Preserves Android's NCX flattening semantics: only nodes with a content target are emitted,
 * and a target-less node's descendants are deliberately not traversed.
 */
fun flattenMobileEpubNcxNavigation(
    roots: List<MobileEpubNcxNavigationNode>
): List<MobileEpubTocEntry> {
    val entries = mutableListOf<MobileEpubTocEntry>()

    fun append(nodes: List<MobileEpubNcxNavigationNode>, depth: Int) {
        nodes.forEach { node ->
            val path = node.absolutePath ?: return@forEach
            entries += MobileEpubTocEntry(
                label = node.label ?: "Untitled",
                absolutePath = path,
                fragmentId = node.fragmentId,
                depth = depth
            )
            append(node.children, depth + 1)
        }
    }

    append(roots, 0)
    return entries
}

data class MobileEpubNcxChapterMetadata(
    val title: String,
    val depth: Int
)

data class MobileEpubChapterNavigation(
    val title: String?,
    val depth: Int,
    val isInToc: Boolean
)

/** Android's exact NCX-to-spine chapter title, depth, and membership projection. */
fun resolveMobileEpubChapterNavigation(
    absolutePath: String,
    fallbackTitle: String?,
    navigationMetadata: Map<String, MobileEpubNcxChapterMetadata>
): MobileEpubChapterNavigation {
    val metadata = navigationMetadata[absolutePath.substringBefore('#')]
    return MobileEpubChapterNavigation(
        title = metadata?.title?.takeIf(String::isNotBlank) ?: fallbackTitle,
        depth = metadata?.depth ?: 0,
        isInToc = navigationMetadata.isEmpty() || metadata != null
    )
}

/** Android keeps a non-blank extracted heading or falls back to the one-based spine position. */
fun resolveMobileEpubSpineChapterTitle(extractedHeading: String?, spineIndex: Int): String =
    extractedHeading?.takeIf(String::isNotBlank) ?: "Chapter ${spineIndex + 1}"

/**
 * Android's chapter-title map traverses target-less parents, ignores blank labels, strips
 * fragments in the platform adapter, and keeps the first outermost entry for each resource.
 */
fun mobileEpubNcxChapterMetadata(
    roots: List<MobileEpubNcxNavigationNode>
): Map<String, MobileEpubNcxChapterMetadata> {
    val result = linkedMapOf<String, MobileEpubNcxChapterMetadata>()
    fun visit(nodes: List<MobileEpubNcxNavigationNode>, depth: Int) {
        nodes.forEach { node ->
            val label = node.label
            val path = node.absolutePath
            if (!label.isNullOrBlank() && path != null && path !in result) {
                result[path] = MobileEpubNcxChapterMetadata(label, depth)
            }
            visit(node.children, depth + 1)
        }
    }
    visit(roots, 0)
    return result
}

data class MobileEpubMetadata(
    val fileName: String,
    val title: String,
    val author: String,
    val language: String,
    val seriesName: String?,
    val seriesIndex: Double?,
    val description: String?
)

/** Android's exact OPF metadata defaults and Calibre-series precedence. */
fun resolveMobileEpubMetadata(
    sourceFileName: String,
    title: String?,
    author: String?,
    language: String?,
    description: String?,
    metaEntries: List<Pair<String?, String?>>
): MobileEpubMetadata {
    val resolvedTitle = title ?: sourceFileName.substringAfterLast('/').substringBeforeLast('.')
    var seriesName: String? = null
    var seriesIndex: Double? = null
    metaEntries.forEach { (name, content) ->
        if (name == "calibre:series") seriesName = content
        if (name == "calibre:series_index") seriesIndex = content?.toDoubleOrNull()
    }
    return MobileEpubMetadata(
        fileName = resolvedTitle.replace("/", "_"),
        title = resolvedTitle,
        author = author ?: "Unknown Author",
        language = language ?: "en",
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        description = description
    )
}

/** Android preserves every OPF itemref in document order, including `linear="no"`. */
fun mobileEpubSpineItemIds(itemRefs: List<String?>): List<String> =
    itemRefs.mapNotNull { it }

/** Portable equivalent of Android's `URLDecoder.decode(value, UTF_8)` with raw fallback. */
fun decodeMobileEpubUrl(value: String): String {
    val output = ArrayList<Byte>(value.length)
    val literal = StringBuilder()
    fun flushLiteral() {
        if (literal.isEmpty()) return
        output += literal.toString().encodeToByteArray().toList()
        literal.clear()
    }
    var index = 0
    while (index < value.length) {
        when (val char = value[index]) {
            '+' -> {
                flushLiteral()
                output += ' '.code.toByte()
                index++
            }
            '%' -> {
                if (index + 2 >= value.length) return value
                val decoded = value.substring(index + 1, index + 3).toIntOrNull(16) ?: return value
                flushLiteral()
                output += decoded.toByte()
                index += 3
            }
            else -> {
                literal.append(char)
                index++
            }
        }
    }
    flushLiteral()
    return output.toByteArray().decodeToString()
}

/**
 * Portable lexical equivalent of Android's `Paths.get(ownerParent, reference).normalize()`.
 * Absolute and above-root relative results are deliberately preserved; archive I/O rejects
 * them later, matching Android's extraction boundary rather than rewriting the reference.
 */
fun resolveMobileEpubReference(ownerPath: String, reference: String): String {
    val base = ownerPath.substringBeforeLast('/', missingDelimiterValue = "")
    val absolute = reference.startsWith('/')
    val candidate = if (absolute || base.isBlank()) reference else "$base/$reference"
    val parts = mutableListOf<String>()
    candidate.split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> when {
                parts.lastOrNull()?.let { it != ".." } == true -> parts.removeAt(parts.lastIndex)
                !absolute -> parts += part
            }
            else -> parts += part
        }
    }
    val normalized = parts.joinToString("/")
    return if (absolute) "/$normalized" else normalized
}

data class MobileEpubManifestItem(
    val id: String,
    val absPath: String,
    val mediaType: String,
    val properties: String
)

const val MOBILE_EPUB_EXTRACTION_CACHE_VERSION = 3

@Serializable
data class MobileEpubExtractionCacheManifest(
    val bookId: String,
    val originalBookNameHint: String,
    val parserVersion: Int,
    val parseContent: Boolean,
    val shouldUseToc: Boolean,
    val sourceFingerprint: String? = null
)

/** Android's exact extracted-book cache compatibility key. */
fun MobileEpubExtractionCacheManifest.matchesMobileEpubExtractionCache(
    bookId: String,
    originalBookNameHint: String,
    shouldUseToc: Boolean,
    sourceFingerprint: String?
): Boolean =
    this.bookId == bookId &&
        this.originalBookNameHint == originalBookNameHint &&
        parserVersion == MOBILE_EPUB_EXTRACTION_CACHE_VERSION &&
        parseContent &&
        this.shouldUseToc == shouldUseToc &&
        this.sourceFingerprint == sourceFingerprint

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("com.aryan.reader.epub.EpubImage")
data class MobileEpubImage @OptIn(ExperimentalSerializationApi::class) constructor(
    @ProtoNumber(1) val absPath: String
)

private val MobileEpubImageExtensions = setOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg")

enum class MobileEpubExtractionAction {
    SKIP,
    READ_IN_MEMORY,
    EXTRACT_WITHOUT_MEMORY,
    EXTRACT_AND_READ
}

enum class MobileEpubExtractionDirectoryMode {
    OVERRIDE,
    TEMPORARY_METADATA,
    ACTIVE_CACHE
}

data class MobileEpubExtractionLifecycle(
    val directoryMode: MobileEpubExtractionDirectoryMode,
    val mayReadCache: Boolean,
    val mayWriteCache: Boolean,
    val deleteDirectoryAfterLoad: Boolean
)

/** Android's exact extraction-directory/cache lifecycle selection. */
fun mobileEpubExtractionLifecycle(
    parseContent: Boolean,
    hasDirectoryOverride: Boolean
): MobileEpubExtractionLifecycle = when {
    hasDirectoryOverride -> MobileEpubExtractionLifecycle(
        directoryMode = MobileEpubExtractionDirectoryMode.OVERRIDE,
        mayReadCache = false,
        mayWriteCache = false,
        deleteDirectoryAfterLoad = false
    )
    !parseContent -> MobileEpubExtractionLifecycle(
        directoryMode = MobileEpubExtractionDirectoryMode.TEMPORARY_METADATA,
        mayReadCache = false,
        mayWriteCache = false,
        deleteDirectoryAfterLoad = true
    )
    else -> MobileEpubExtractionLifecycle(
        directoryMode = MobileEpubExtractionDirectoryMode.ACTIVE_CACHE,
        mayReadCache = true,
        mayWriteCache = true,
        deleteDirectoryAfterLoad = false
    )
}

/** Android's exact per-entry extraction/memory policy for full and metadata-only parsing. */
fun mobileEpubExtractionAction(
    path: String,
    parseContent: Boolean,
    extractImagesForMetadata: Boolean
): MobileEpubExtractionAction {
    val lowerPath = path.lowercase()
    val isContainerOrOpf = lowerPath.endsWith("container.xml") || lowerPath.endsWith(".opf")
    if (parseContent) {
        return if (isContainerOrOpf) {
            MobileEpubExtractionAction.EXTRACT_AND_READ
        } else {
            MobileEpubExtractionAction.EXTRACT_WITHOUT_MEMORY
        }
    }
    if (isContainerOrOpf) return MobileEpubExtractionAction.READ_IN_MEMORY
    val isImage = MobileEpubImageExtensions.any(lowerPath::endsWith)
    return if (isImage && extractImagesForMetadata) {
        MobileEpubExtractionAction.EXTRACT_WITHOUT_MEMORY
    } else {
        MobileEpubExtractionAction.SKIP
    }
}

/** Android's ordered manifest-plus-archive image inventory. */
fun mobileEpubImages(
    manifest: List<MobileEpubManifestItem>,
    archivePaths: List<String>
): List<MobileEpubImage> {
    val listed = manifest.filter { it.mediaType.startsWith("image/") }.map { MobileEpubImage(it.absPath) }
    val listedPaths = listed.map(MobileEpubImage::absPath).toSet()
    val unlisted = archivePaths.filter { path ->
        MobileEpubImageExtensions.any { path.lowercase().endsWith(it) } && path !in listedPaths
    }.map(::MobileEpubImage)
    return (listed + unlisted).distinctBy(MobileEpubImage::absPath)
}

/** Android's exact manifest-first then unlisted-archive CSS discovery order. */
fun mobileEpubCssPaths(
    manifest: List<MobileEpubManifestItem>,
    archivePaths: List<String>
): List<String> {
    val listed = manifest.filter { it.mediaType == "text/css" }.map(MobileEpubManifestItem::absPath)
    val listedPaths = listed.toSet()
    return listed + archivePaths.filter { it.endsWith(".css", ignoreCase = true) && it !in listedPaths }
}

/** Android's metadata-first then conventional-name cover candidate order. */
fun mobileEpubCoverCandidates(
    metadataCoverId: String?,
    manifest: List<MobileEpubManifestItem>,
    archivePaths: Set<String>
): List<String> = buildList {
    manifest.firstOrNull { it.id == metadataCoverId }?.absPath?.let(::add)
    listOf("cover.jpg", "cover.jpeg", "cover.png").forEach { name ->
        listOf(
            name, "images/$name", "Images/$name", "image/$name", "Image/$name",
            "OEBPS/images/$name", "OEBPS/Images/$name", "OEBPS/image/$name", "OEBPS/Image/$name",
            "OPS/images/$name", "OPS/Images/$name", "OPS/image/$name", "OPS/Image/$name"
        ).forEach { path ->
            if (path in archivePaths) add(path)
            manifest.firstOrNull {
                it.absPath.equals(path, ignoreCase = true) && it.mediaType.startsWith("image/")
            }?.absPath?.let(::add)
        }
    }
}

/** Android's power-of-two cover decode sampling policy. */
fun mobileEpubCoverBitmapSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int = 1024
): Int {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
    var sampleSize = 1
    while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    return sampleSize
}

enum class MobileEpubSpineResourceKind {
    HTML,
    IMAGE,
    UNSUPPORTED
}

/** Android's exact spine resource classification, including its XML document fallback. */
fun mobileEpubSpineResourceKind(
    mediaType: String,
    absolutePath: String
): MobileEpubSpineResourceKind = when {
    mediaType.startsWith("application/xhtml+xml") ||
        mediaType.startsWith("text/html") ||
        absolutePath.endsWith(".html", ignoreCase = true) ||
        absolutePath.endsWith(".xhtml", ignoreCase = true) ||
        absolutePath.endsWith(".xml", ignoreCase = true) -> MobileEpubSpineResourceKind.HTML
    mediaType.startsWith("image/") -> MobileEpubSpineResourceKind.IMAGE
    else -> MobileEpubSpineResourceKind.UNSUPPORTED
}

fun mobileEpubSpineResourceKind(item: MobileEpubManifestItem): MobileEpubSpineResourceKind =
    mobileEpubSpineResourceKind(item.mediaType, item.absPath)

/** Android's exact NCX selection precedence: spine toc ID, NCX media type, then extension. */
fun resolveMobileEpubNcxManifestId(
    spineTocId: String?,
    manifest: List<MobileEpubManifestItem>
): String? {
    spineTocId
        ?.takeIf(String::isNotBlank)
        ?.let { tocId -> manifest.firstOrNull { it.id == tocId }?.id }
        ?.let { return it }
    return manifest.firstOrNull {
        it.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true)
    }?.id ?: manifest.firstOrNull {
        it.absPath.endsWith(".ncx", ignoreCase = true)
    }?.id
}

/** Android uses the first container rootfile and removes only leading slashes. */
fun resolveMobileEpubOpfPath(rootfilePaths: List<String?>): String? =
    rootfilePaths.firstOrNull()?.trimStart('/')
