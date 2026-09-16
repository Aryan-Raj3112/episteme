/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.epubreader

import android.content.Context
import com.aryan.reader.R
import com.aryan.reader.applyBookReplacementsToHtmlDocument
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.contentFilePath
import com.aryan.reader.paginatedreader.LocatorConverter
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import timber.log.Timber
import java.io.File

private const val TXT_FORMAT_TRACE_TAG = "TxtFormatTrace"
private const val BLANK_PAGE_DIAG_TAG = "EpubBlankDiag"
private const val MAX_INITIAL_WEBVIEW_CHUNKS = 8
private const val ESTIMATED_READER_CHUNK_ELEMENT_HEIGHT_PX = 72
private const val ESTIMATED_READER_CHUNK_IMAGE_HEIGHT_PX = 280

private fun String.txtFormatTracePreview(maxLength: Int = 220): String {
    // Truncate first: previews must stay allocation-bounded even when the
    // caller passes a chapter-sized string.
    val truncated = take(maxLength)
    val suffix = if (length > maxLength) "..." else ""
    return truncated
        .replace("\\", "\\\\")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
        .plus(suffix)
        .replace("\"", "\\\"")
}

data class ChapterLoadingResult(
    val head: String,
    val chunks: List<String>,
    val startChunkIndex: Int,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val chunkElementStartIndices: List<Int> = emptyList(),
    val chunkElementCounts: List<Int> = emptyList()
)

internal data class ReaderHtmlChunk(
    val html: String,
    val elementStartIndex: Int,
    val elementCount: Int
)

internal fun splitBodyNodesIntoReaderChunks(
    bodyNodes: List<Node>,
    chunkSize: Int = 20
): List<ReaderHtmlChunk> {
    // Standard Ebooks chapters wrap everything in a single container
    // (<section id="...">); chunking the wrapper itself collapses the whole
    // chapter into one 200000px+ chunk and defeats virtualization. Descend
    // into lone passthrough containers so figures and paragraphs chunk.
    val effectiveNodes = effectiveReaderChunkNodes(bodyNodes, chunkSize)
    var elementStartIndex = 0
    return effectiveNodes.chunked(chunkSize).map { nodes ->
        val elementCount = nodes.count { it is Element }
        ReaderHtmlChunk(
            html = nodes.joinToString(separator = "\n") { it.outerHtml() },
            elementStartIndex = elementStartIndex,
            elementCount = elementCount
        ).also {
            elementStartIndex += elementCount
        }
    }
}

private val READER_CHUNK_PASSTHROUGH_CONTAINERS = setOf("section", "div", "article", "main", "aside")

/**
 * Unwraps lone structural containers so [splitBodyNodesIntoReaderChunks] and
 * [Element.readerTopLevelBodyChildIndex] agree on what "top level" means.
 * Small wrappers stay intact: zero behavior change for normal chapters.
 */
internal fun effectiveReaderChunkNodes(
    bodyNodes: List<Node>,
    chunkSize: Int = 20
): List<Node> {
    val size = chunkSize.coerceAtLeast(1)
    var nodes = bodyNodes
    var guard = 0
    while (guard++ < 8) {
        val elements = nodes.filterIsInstance<Element>()
        if (elements.size != 1) break
        val container = elements.single()
        if (container.tagName().lowercase() !in READER_CHUNK_PASSTHROUGH_CONTAINERS) break
        val innerElements = container.childNodes().filterIsInstance<Element>()
        if (innerElements.size <= size) break
        nodes = container.childNodes().toList()
    }
    return nodes
}

internal fun readerChunkContainerAttributes(
    index: Int,
    chunkElementStartIndices: List<Int>,
    chunkElementCounts: List<Int>
): String {
    val startIndex = chunkElementStartIndices.getOrElse(index) { index * 20 }
    val elementCount = chunkElementCounts.getOrElse(index) { 20 }
    return "data-chunk-index='$index' data-element-start-index='$startIndex' data-element-count='$elementCount'"
}

internal fun initialReaderLoadedChunkCount(
    totalChunks: Int,
    targetChunkIndex: Int,
    maxInitialChunks: Int = MAX_INITIAL_WEBVIEW_CHUNKS
): Int {
    if (totalChunks <= 0 || maxInitialChunks <= 0) return 0
    val boundedTarget = targetChunkIndex.coerceIn(0, totalChunks - 1)
    return minOf(totalChunks, boundedTarget + 2, maxInitialChunks)
}

internal fun shouldInlineInitialReaderChunk(
    index: Int,
    totalChunks: Int,
    targetChunkIndex: Int,
    maxInitialChunks: Int = MAX_INITIAL_WEBVIEW_CHUNKS
): Boolean {
    return index in 0 until initialReaderLoadedChunkCount(totalChunks, targetChunkIndex, maxInitialChunks)
}

internal fun readerChunkPlaceholderHeightPx(
    index: Int,
    chunkElementCounts: List<Int>,
    chunkImageCounts: List<Int> = emptyList()
): Int {
    val elementCount = chunkElementCounts.getOrElse(index) { 20 }.coerceAtLeast(1)
    // Illustrations without decoded dimensions collapse to one 72px row and
    // leave blank gaps that fill in late; reserve image space up front.
    val imageCount = chunkImageCounts.getOrElse(index) { 0 }.coerceAtLeast(0)
    return elementCount * ESTIMATED_READER_CHUNK_ELEMENT_HEIGHT_PX +
        imageCount * ESTIMATED_READER_CHUNK_IMAGE_HEIGHT_PX
}

/**
 * Marks content images lazy/async and backfills real bounds from local files
 * so illustration chapters reserve space before first paint instead of
 * shifting layout as each bitmap decodes mid-scroll. Runs on Dispatchers.IO
 * inside [loadChapterContent]; file reads stay header-only via the shared
 * bounds parser.
 */
internal fun applyReaderImageLoadingHints(
    document: org.jsoup.nodes.Document,
    chapterDirectory: File?,
    extractionBasePath: String
) {
    val images = document.select("img")
    if (images.isEmpty()) return
    val extractionRoot = runCatching { File(extractionBasePath).canonicalFile }.getOrNull()
    var hintedCount = 0
    var measuredCount = 0
    var prunedSrcsetCount = 0
    val missingSources = mutableListOf<String>()
    images.forEach { img ->
        // Eager on purpose: chunk virtualization already windows content, and
        // per-image lazy leaves decoded-but-never-laid-out (0x0) boxes on
        // affected WebViews. See withReaderImageLoadingHints.
        if (!img.hasAttr("loading")) {
            img.attr("loading", "eager")
            hintedCount++
        }
        if (!img.hasAttr("decoding")) img.attr("decoding", "async")
        if (!img.hasAttr("width") || !img.hasAttr("height")) {
            readerImageBytesForHints(img.attr("src"), chapterDirectory, extractionRoot)?.let { bytes ->
                com.aryan.reader.shared.reader.parseReaderImageBounds(bytes)?.let { (width, height) ->
                    if (!img.hasAttr("width")) img.attr("width", width.toString())
                    if (!img.hasAttr("height")) img.attr("height", height.toString())
                    measuredCount++
                }
            }
        }
        // A srcset candidate the WebView cannot resolve wins over a working
        // src on hidpi screens and renders as a broken image with no error
        // surfacing in most cases. Drop only the unresolvable candidates so a
        // valid fallback always remains.
        if (img.hasAttr("srcset")) {
            val pruned = pruneUnresolvableSrcset(img.attr("srcset"), chapterDirectory, extractionRoot)
            if (pruned != null) {
                prunedSrcsetCount++
                if (pruned.isBlank()) img.removeAttr("srcset") else img.attr("srcset", pruned)
            }
        }
        if (missingSources.size < 3) {
            readerImageFileForHints(img.attr("src"), chapterDirectory, extractionRoot)
                ?.takeUnless { it.isFile }
                ?.let { missingSources += img.attr("src") }
        }
    }
    if (hintedCount > 0 || measuredCount > 0 || prunedSrcsetCount > 0 || missingSources.isNotEmpty()) {
        Timber.tag(BLANK_PAGE_DIAG_TAG).d(
            "event=android_image_hints hinted=$hintedCount measured=$measuredCount " +
                "prunedSrcset=$prunedSrcsetCount missingSrc=${missingSources.size} total=${images.size} " +
                "missingPreview=${missingSources.joinToString("|").take(220)}"
        )
    }
}

/**
 * Returns the rewritten srcset with file-backed candidates that do not exist
 * on disk removed, or null when nothing changed. data:/http(s) candidates are
 * kept because they cannot be verified locally.
 */
internal fun pruneUnresolvableSrcset(
    srcset: String,
    chapterDirectory: File?,
    extractionRoot: File?
): String? {
    if (srcset.isBlank()) return null
    // Canonicalize once: TemporaryFolder-style symlinked roots (/var -> /private/var)
    // otherwise never prefix-match canonical candidates below.
    val canonicalRoot = extractionRoot?.let { runCatching { it.canonicalFile }.getOrNull() }
        ?: extractionRoot
    var changed = false
    val kept = srcset.split(',').mapNotNull { rawCandidate ->
        val candidate = rawCandidate.trim()
        if (candidate.isEmpty()) {
            changed = true
            return@mapNotNull null
        }
        // Candidate is "url [descriptor]"; the URL itself never contains an
        // unencoded space, so the descriptor (if any) follows the last space.
        val url = candidate.substringBeforeLast(' ', missingDelimiterValue = "").trim()
            .takeIf { it.isNotEmpty() } ?: candidate.substringBefore(' ').trim()
        val fullUrl = if (url.isEmpty()) candidate else url
        if (fullUrl.startsWith("data:", ignoreCase = true) ||
            fullUrl.startsWith("blob:", ignoreCase = true) ||
            fullUrl.startsWith("http://", ignoreCase = true) ||
            fullUrl.startsWith("https://", ignoreCase = true) ||
            fullUrl.startsWith("file://", ignoreCase = true)
        ) {
            return@mapNotNull candidate
        }
        val file = readerImageFileForHints(fullUrl, chapterDirectory, canonicalRoot)
        if (file != null && file.isFile) candidate else {
            changed = true
            null
        }
    }
    if (!changed) return null
    return kept.joinToString(", ")
}

private fun readerImageBytesForHints(
    src: String,
    chapterDirectory: File?,
    extractionRoot: File?
): ByteArray? {
    val candidate = readerImageFileForHints(src, chapterDirectory, extractionRoot) ?: return null
    if (!candidate.isFile || candidate.length() <= 0 || candidate.length() > 12 * 1024 * 1024) return null
    return runCatching { candidate.readBytes() }.getOrNull()
}

internal fun readerImageFileForHintsDiag(
    src: String,
    chapterDirectory: File?,
    extractionRoot: File?
): File? = readerImageFileForHints(src, chapterDirectory, extractionRoot)

private fun readerImageFileForHints(
    src: String,
    chapterDirectory: File?,
    extractionRoot: File?
): File? {
    val raw = src.substringBefore('#').substringBefore('?').trim()
    if (raw.isBlank() || raw.startsWith("data:", ignoreCase = true) || raw.startsWith("blob:", ignoreCase = true)) return null
    if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) return null
    if (raw.startsWith("file://", ignoreCase = true)) return null
    val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    val base = chapterDirectory ?: return null
    val candidate = runCatching { File(base, decoded).canonicalFile }.getOrNull() ?: return null
    if (extractionRoot != null) {
        val rootPath = extractionRoot.path.trimEnd(File.separatorChar) + File.separatorChar
        if (!candidate.path.startsWith(rootPath)) return null
    }
    return candidate
}

/**
 * loads the chapter HTML, splits it into chunks, and calculates
 * the initial chunk to display based on navigation state (CFI, overrides, etc.).
 */
suspend fun loadChapterContent(
    context: Context,
    epubBook: EpubBook,
    chapterIndex: Int,
    chunkTargetOverride: Int?,
    isInitialCfiLoad: Boolean,
    cfiToLoad: String?,
    locatorConverter: LocatorConverter,
    bookReplacementPreferences: ReaderBookReplacementPreferences = ReaderBookReplacementPreferences(),
    bookReplacementFileId: String? = null,
): ChapterLoadingResult = withContext(Dispatchers.IO) {
    val chapter =
        epubBook.chapters.getOrNull(chapterIndex) ?: return@withContext ChapterLoadingResult(
            "", emptyList(), 0, false, "Chapter index out of bounds"
        )

    try {
        val htmlFile = File(epubBook.extractionBasePath, chapter.contentFilePath())
        // Cache/file diagnostics: prove whether missing images are a cache eviction
        // (files gone) or a layout issue (files present but 0x0). Same EpubBlankDiag tag.
        run {
            val root = runCatching { File(epubBook.extractionBasePath).canonicalFile }.getOrNull()
            val rootExists = root?.isDirectory == true
            val rootEntries = if (rootExists) (root.list()?.size ?: -1) else -1
            val chapterExists = htmlFile.isFile
            val chapterLen = if (chapterExists) htmlFile.length() else -1L
            Timber.tag(BLANK_PAGE_DIAG_TAG).d(
                "event=android_cache_diag chapter=${chapterIndex + 1} file=${chapter.contentFilePath().txtFormatTracePreview()} " +
                    "base=${epubBook.extractionBasePath.takeLast(80)} rootExists=$rootExists entries=$rootEntries " +
                    "chapterExists=$chapterExists chapterBytes=$chapterLen"
            )
        }

        val (headContent, chunks, chunkElementStartIndices, chunkElementCounts) = if (htmlFile.exists()) {
            val doc = Jsoup.parse(htmlFile, "UTF-8")
            doc.outputSettings().prettyPrint(false)
            val head = doc.head().html()
            doc.select("script").remove()
            applyBookReplacementsToHtmlDocument(
                document = doc,
                preferences = bookReplacementPreferences,
                fileId = bookReplacementFileId,
            )
            applyReaderImageLoadingHints(doc, htmlFile.parentFile, epubBook.extractionBasePath)
            // Log which linked stylesheets and images resolve to real files on disk.
            run {
                val chapterDir = htmlFile.parentFile
                val root = runCatching { File(epubBook.extractionBasePath).canonicalFile }.getOrNull()
                val cssLinks = doc.head().select("link[rel=stylesheet]").map { it.attr("href") }
                val cssState = cssLinks.take(6).joinToString("|") { href ->
                    val f = readerImageFileForHintsDiag(href, chapterDir, root)
                    val ok = f?.isFile == true
                    val bytes = if (ok) (f?.length() ?: -1L) else -1L
                    "${href.takeLast(24)}:exists=$ok,bytes=$bytes"
                }
                val imgs = doc.select("img")
                val imgState = imgs.take(6).joinToString("|") { img ->
                    val src = img.attr("src")
                    val f = readerImageFileForHintsDiag(src, chapterDir, root)
                    val ok = f?.isFile == true
                    "${src.takeLast(24)}:exists=$ok,w=${img.attr("width").ifBlank { "?" }},h=${img.attr("height").ifBlank { "?" }},ss=${if (img.hasAttr("srcset")) "y" else "n"}"
                }
                val asideCount = doc.select("div.aside").size
                val figureCount = doc.select("figure").size
                val firstAside = doc.select("div.aside").firstOrNull()?.text()?.take(60).orEmpty()
                Timber.tag(BLANK_PAGE_DIAG_TAG).d(
                    "event=android_file_diag chapter=${chapterIndex + 1} cssLinks=${cssLinks.size} [$cssState] " +
                        "imgs=${imgs.size} [$imgState] asides=$asideCount figures=$figureCount firstAside='$firstAside'"
                )
            }
            val bodyNodes = doc.body().childNodes().toList()
            val htmlChunks = splitBodyNodesIntoReaderChunks(bodyNodes)
            val chunkPreviewSource = htmlChunks.firstOrNull()?.html.orEmpty()
            run {
                val firstChild = doc.body().children().firstOrNull()
                // Per-chunk content census so we can tell whether images/asides are in
                // inline vs placeholder chunks at load time.
                val chunkCensus = htmlChunks.take(12).mapIndexed { index, chunk ->
                    val lower = chunk.html.lowercase()
                    val imgs = "<img".toRegex().findAll(lower).count()
                    val asides = "class=\"aside\"".toRegex().findAll(lower).count() +
                        "class='aside'".toRegex().findAll(lower).count()
                    val figs = "<figure".toRegex().findAll(lower).count()
                    "$index:chars=${chunk.html.length},el=${chunk.elementCount},img=$imgs,aside=$asides,fig=$figs"
                }.joinToString(" | ")
                Timber.tag(BLANK_PAGE_DIAG_TAG).d(
                    "event=android_chunk_diag chapter=${chapterIndex + 1} file=${chapter.contentFilePath().txtFormatTracePreview()} " +
                        "headChars=${head.length} headHasViewportMeta=${head.contains("name=\"viewport\"")} " +
                        "bodyElementChildren=${doc.body().children().size} " +
                        "firstChildTag=${firstChild?.tagName()} firstChildDir=${firstChild?.attr("dir").orEmpty()} " +
                        "chunks=${htmlChunks.size} " +
                        "chunkChars=[${htmlChunks.joinToString(",") { it.html.length.toString() }}] " +
                        "elementCounts=[${htmlChunks.joinToString(",") { it.elementCount.toString() }}] " +
                        "census=[$chunkCensus]"
                )
            }
            Timber.tag(TXT_FORMAT_TRACE_TAG).d(
                "event=android_txt_chunk_split chapter=${chapterIndex + 1} file=${chapter.contentFilePath().txtFormatTracePreview()} " +
                    "headChars=${head.length} bodyNodes=${bodyNodes.size} chunks=${htmlChunks.size} " +
                    "headHasPreWrap=${head.contains("white-space: pre-wrap") || head.contains("white-space:pre-wrap")} " +
                    "chunkHasTxtClass=${chunkPreviewSource.contains("reader-txt-preformatted")} " +
                    "chunkHasInlinePreWrap=${chunkPreviewSource.contains("white-space: pre-wrap !important")} " +
                    "chunkHasAlbumMarker=${chunkPreviewSource.contains("===========CD 1=============")} " +
                    "chunkPreview=${chunkPreviewSource.txtFormatTracePreview()}"
            )
            if (htmlChunks.isEmpty()) {
                ChapterHtmlPayload(
                    head = head,
                    chunks = listOf("<body><p>${context.getString(R.string.chapter_empty)}</p></body>"),
                    chunkElementStartIndices = listOf(0),
                    chunkElementCounts = listOf(1)
                )
            } else {
                ChapterHtmlPayload(
                    head = head,
                    chunks = htmlChunks.map { it.html },
                    chunkElementStartIndices = htmlChunks.map { it.elementStartIndex },
                    chunkElementCounts = htmlChunks.map { it.elementCount }
                )
            }
        } else {
            ChapterHtmlPayload(
                head = "",
                chunks = listOf("<h1>${context.getString(R.string.chapter_not_found)}</h1>"),
                chunkElementStartIndices = listOf(0),
                chunkElementCounts = listOf(1)
            )
        }

        var targetChunk = 0

        if (chunkTargetOverride != null) {
            Timber.d("Applying chunk target override: $chunkTargetOverride")
            targetChunk = chunkTargetOverride
        }
        else if (isInitialCfiLoad && cfiToLoad != null) {
            Timber.d("Calculating target chunk for initial CFI: $cfiToLoad")
            val locator = locatorConverter.getLocatorFromCfi(epubBook, chapterIndex, cfiToLoad)
            val calculatedChunk = locator?.let { it.blockIndex / 20 }

            if (calculatedChunk != null) {
                targetChunk = calculatedChunk
            } else {
                Timber.w("Could not determine target chunk for CFI. Loading all (fallback to last).")
                targetChunk = if (chunks.isNotEmpty()) chunks.size - 1 else 0
            }
        }

        targetChunk = targetChunk.coerceIn(0, maxOf(0, chunks.size - 1))

        ChapterLoadingResult(
            head = headContent,
            chunks = chunks,
            startChunkIndex = targetChunk,
            isSuccess = true,
            chunkElementStartIndices = chunkElementStartIndices,
            chunkElementCounts = chunkElementCounts
        )

    } catch (e: Exception) {
        Timber.e(e, "Failed to parse chapter")
        ChapterLoadingResult(
            head = "",
            chunks = listOf("<h1>${context.getString(R.string.error_loading_chapter)}</h1><p>${e.message}</p>"),
            startChunkIndex = 0,
            isSuccess = false,
            errorMessage = e.message
        )
    }
}

private data class ChapterHtmlPayload(
    val head: String,
    val chunks: List<String>,
    val chunkElementStartIndices: List<Int>,
    val chunkElementCounts: List<Int>
)
