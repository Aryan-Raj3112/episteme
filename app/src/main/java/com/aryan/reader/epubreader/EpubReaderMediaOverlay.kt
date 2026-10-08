package com.aryan.reader.epubreader

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.aryan.reader.R
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.mediaoverlay.AndroidSharedMediaOverlayPlayback
import com.aryan.reader.paginatedreader.ContentBlock
import com.aryan.reader.paginatedreader.resolveSharedMediaOverlayFragmentsInBlocks
import com.aryan.reader.shared.reader.SharedMediaOverlayDocument
import com.aryan.reader.shared.reader.SharedMediaOverlayDocumentCache
import com.aryan.reader.shared.reader.SharedMediaOverlayIndex
import com.aryan.reader.shared.reader.SharedMediaOverlayPlaybackState
import com.aryan.reader.shared.reader.SharedMediaOverlayProjection
import com.aryan.reader.shared.reader.SharedMediaOverlayProjectionSource
import com.aryan.reader.shared.reader.SharedMediaOverlaySession
import com.aryan.reader.shared.reader.SharedPlaybackFragment
import com.aryan.reader.shared.reader.normalizeSharedMediaOverlayPath
import com.aryan.reader.shared.reader.rememberSharedMediaOverlaySmilReader
import com.aryan.reader.shared.reader.sharedMediaOverlaySpineItemIndexByChapter
import com.aryan.reader.shared.reader.sharedMediaOverlaySpineItemsInReadingOrder
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import java.io.File

/**
 * EPUB 3 media overlays on the Android reader screen.
 *
 * The shape mirrors `EpubReaderTts.kt`, because the two features share one contract: a playback
 * engine the screen owns, a projection from playback position to `SharedPlaybackFragment`, and
 * per-surface follow. Everything sequencing- or parsing-related is shared code (`shared/reader`);
 * this file only decides *when* things happen on Android.
 *
 * The narration paints through the surfaces' existing `ttsHighlightInfo: SharedPlaybackFragment?`
 * parameter rather than through a highlight in the user's list. Arbitration guarantees read-aloud
 * and an overlay never play at once, so the single parameter always serves the one active engine,
 * and the band inherits read-aloud's paint-plus-hit-test semantics unchanged.
 */

/**
 * The archive a narrated book streams from, or null when narration cannot work.
 *
 * Only real files qualify. Library imports live as files under `filesDir/books/`; a `content://`
 * URI (an external file opened in place) cannot be handed to `java.util.zip.ZipFile`, which needs
 * a seekable file. Narration is simply absent for those books — the reader still opens them —
 * rather than failing later at the first press of play.
 */
internal fun mediaOverlayArchiveFile(uriString: String?): File? {
    val value = uriString?.takeIf { it.isNotBlank() } ?: return null
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
    val path = when (uri.scheme?.lowercase()) {
        null, "", "file" -> uri.path
        else -> return null
    } ?: return null
    return File(path).takeIf { it.isFile }
}

/**
 * The state a screen that cannot narrate still reads.
 *
 * A screen collects its session's state flow whenever a session exists, so the no-session case
 * needs a flow of the same shape to collect instead of a branch inside the collect call — one code
 * path whether or not the book has narration.
 */
internal val EmptyMediaOverlayPlaybackState = MutableStateFlow(SharedMediaOverlayPlaybackState())

/**
 * Resolves playback positions against the reader's chapters, remembering each chapter's anchors.
 *
 * Android's blocks are the paginator's styled `ContentBlock`s, not the semantic IR the shared
 * projector wants, so this walks those via [resolveSharedMediaOverlayFragmentsInBlocks]. The
 * per-chapter anchor map is resolved once and reused while playback stays in the chapter — a clip
 * advances every couple of seconds, and re-walking the chapter per clip would stutter the
 * narration.
 *
 * A spine document may have been split into several reader chapters at TOC fragments (the
 * reference book: 159 chapters for 156 spine items), so candidates are searched in order and the
 * first chapter whose blocks actually contain the clip's element id wins.
 *
 * Not synchronized: it belongs to the reader's single-threaded UI state.
 */
internal class EpubMediaOverlayProjector(
    private val cache: SharedMediaOverlayDocumentCache,
    private val chapters: List<EpubChapter>,
    private val blocksForChapter: suspend (Int) -> List<ContentBlock>?,
) : SharedMediaOverlayProjectionSource {
    /** How many chapters have had their anchors resolved. Diagnostics; the cost the cache hides. */
    var anchorResolutionCount: Int = 0
        private set

    private val anchorsByChapter = HashMap<Int, Map<Int, SharedPlaybackFragment>>()

    override fun document(spineItemIndex: Int): SharedMediaOverlayDocument? =
        cache.document(spineItemIndex)

    /**
     * Resolves one playback position, or null when the position names no clip at all.
     *
     * The result is deliberately partial rather than all-or-nothing: the *chapter* comes from the
     * overlay's `epub:textref` whenever the blocks needed to place the offsets are unavailable, the
     * *element id* comes from the clip, and the *fragment* — the only part the Compose surfaces need
     * — is present only when an anchor really resolved. A position with a chapter but no fragment is
     * the WebView's normal case, and it is enough to follow the narration and to paint the band
     * there. Failing the whole projection instead would take the follow with it.
     */
    override suspend fun project(
        spineItemIndex: Int,
        clipIndex: Int
    ): SharedMediaOverlayProjection? {
        val document = cache.document(spineItemIndex) ?: return null
        val clip = document.clips.getOrNull(clipIndex) ?: return null
        val candidates = candidateChapters(document)
        var chapterIndex: Int? = null
        var fragment: SharedPlaybackFragment? = null
        var inspectedAnyBlocks = false
        // A plain loop rather than `firstOrNull`: resolving anchors suspends, and a split chapter
        // that does not carry the element must be skipped rather than assumed to hold it.
        for (candidate in candidates) {
            val anchors = anchorsFor(document, candidate) ?: continue
            inspectedAnyBlocks = true
            val resolved = anchors[clipIndex] ?: continue
            chapterIndex = candidate
            fragment = resolved
            break
        }
        if (chapterIndex == null && !inspectedAnyBlocks && clip.elementId != null) {
            // Not one candidate had blocks to inspect, so the *chapter* is still known even though the
            // offsets are not: the document's `epub:textref` names the content document, and the reader's
            // chapter list is matched to it by path. This is the WebView surface's normal case — it has
            // no `ContentBlock`s at all, it anchors by element id in the DOM — so without this fallback
            // narration never follows across a chapter boundary there, and the band is the only thing
            // that works. A fragment stays null: the Compose surfaces need offsets this cannot invent.
            chapterIndex = candidates.firstOrNull()
        }
        return SharedMediaOverlayProjection(
            spineItemIndex = spineItemIndex,
            clipIndex = clipIndex,
            chapterIndex = chapterIndex,
            fragment = fragment,
            elementId = clip.elementId?.takeIf(String::isNotBlank)
        )
    }

    /**
     * The document clip narrating [readerOffset] in the chapter, or null.
     *
     * Used to start narration from where the reader is. The first clip whose resolved fragment
     * reaches or passes the offset wins, so tapping play mid-chapter narrates from there rather
     * than from the top. A clip with no resolvable fragment cannot be placed and is skipped,
     * which matches how playback will skip it audibly.
     */
    override suspend fun clipIndexForReaderPosition(
        spineItemIndex: Int,
        readerOffset: Int
    ): Int? {
        val document = cache.document(spineItemIndex) ?: return null
        if (readerOffset < 0) return null
        for (chapterIndex in candidateChapters(document)) {
            val anchors = anchorsFor(document, chapterIndex) ?: continue
            if (anchors.isEmpty()) continue
            val hit = document.clips.firstOrNull { clip ->
                val fragment = anchors[clip.clipIndex]
                fragment != null && fragment.endAbs > readerOffset
            }
            if (hit != null) return hit.clipIndex
        }
        return null
    }

    /**
     * The chapter's resolved anchors, computed once and reused, or null when the chapter's blocks
     * could not be read at all.
     *
     * The distinction between "no blocks" and "blocks, none of which carry this document's ids" is
     * load-bearing. An empty map is a legitimate answer about the book, and callers may act on it; a
     * null means the surface has nothing to resolve against — a WebView chapter the paginator never
     * built — and a caller must not read that as evidence about the book.
     *
     * Only non-empty results are cached. Caching an empty map would be wrong when the reason was a
     * chapter whose blocks were not loadable yet — the paginator builds them on demand, so retrying is
     * both possible and cheap, while a stuck empty map would silence a chapter for the rest of the
     * session.
     */
    private suspend fun anchorsFor(
        document: SharedMediaOverlayDocument,
        chapterIndex: Int
    ): Map<Int, SharedPlaybackFragment>? {
        anchorsByChapter[chapterIndex]?.let { return it }
        val blocks = blocksForChapter(chapterIndex)
        if (blocks.isNullOrEmpty()) return null
        val anchors = resolveSharedMediaOverlayFragmentsInBlocks(document, blocks)
        if (anchors.isNotEmpty()) {
            anchorResolutionCount++
            anchorsByChapter[chapterIndex] = anchors
        }
        return anchors
    }

    /**
     * The chapters that could hold this document's fragments, best first.
     *
     * Path matching on the document's `epub:textref` against each chapter's source path, then every
     * chapter as a fallback. Chapters are never *assumed* to contain the element —
     * [resolveSharedMediaOverlayFragmentsInBlocks] returns nothing for an id the chapter does not
     * carry, and the next candidate is tried. The fallback exists because `epub:textref` is only a
     * hint: a book republished from a different reflow routinely disagrees with its own overlay.
     */
    private fun candidateChapters(document: SharedMediaOverlayDocument): List<Int> {
        val preferred = document.textHref
            ?.takeIf(String::isNotBlank)
            ?.let(::chaptersMatchingContentPath)
            .orEmpty()
        if (preferred.isNotEmpty()) return preferred
        return chapters.indices.toList()
    }

    private fun chaptersMatchingContentPath(textHref: String): List<Int> {
        val target = normalizeSharedMediaOverlayPath(textHref)
        if (target.isEmpty()) return emptyList()
        return chapters.indices.filter { index ->
            normalizeSharedMediaOverlayPath(chapters[index].absPath) == target
        }
    }
}

/**
 * Remembers an overlay session for one book, or null when the book cannot narrate itself.
 *
 * Null for three separate reasons, all of them legitimate: no local archive file to stream from,
 * no overlays in the OPF, or no SMIL reader on this platform. All three degrade to "this book has
 * no narration button", which is exactly what a reader that ignores overlays does (`RS §9`).
 *
 * The hook order is deliberately unconditional — the archive path is always passed to the reader
 * seam, as an empty string when there is no file — so a screen that recomposes around a book that
 * cannot narrate does not change how many composables it calls.
 */
@Composable
internal fun rememberEpubMediaOverlaySession(
    bookId: String,
    bookTitle: String,
    narrator: String?,
    totalDurationMs: Long?,
    overlayIndex: SharedMediaOverlayIndex,
    chapters: List<EpubChapter>,
    archiveFile: File?,
    blocksForChapter: suspend (Int) -> List<ContentBlock>?,
): SharedMediaOverlaySession? {
    val context = LocalContext.current
    val archivePath = archiveFile?.absolutePath.orEmpty()
    val smilReader = rememberSharedMediaOverlaySmilReader(archivePath)
    val engine = remember(context) {
        AndroidSharedMediaOverlayPlayback(context.applicationContext)
    }
    DisposableEffect(engine) {
        onDispose { engine.release() }
    }
    DisposableEffect(archiveFile) {
        archiveFile?.let(engine::attachBook)
        onDispose { }
    }
    val cache = remember(overlayIndex, smilReader, bookId) {
        smilReader?.takeIf { overlayIndex.hasOverlays }
            ?.let { readSmil -> SharedMediaOverlayDocumentCache(overlayIndex, readSmil) }
    }
    // Narration's failure mode is an absence, and three unrelated causes produce it: no local archive,
    // no overlays in the OPF, no SMIL reader on this platform — plus a fourth the user cannot see,
    // chapters whose paths match no spine item, which leaves a working button that narrates nothing.
    // They are one line apart on the screen and indistinguishable from a bug report, so the state is
    // logged once per book. This is the line that would have explained the reported "it does not
    // work" without a device in hand.
    val chapterSpineItems = remember(chapters, overlayIndex) {
        sharedMediaOverlaySpineItemIndexByChapter(
            chapterContentPaths = chapters.map { it.absPath },
            overlayIndex = overlayIndex
        )
    }
    val readingOrder = remember(chapterSpineItems) {
        sharedMediaOverlaySpineItemsInReadingOrder(chapters.size, chapterSpineItems)
    }
    LaunchedEffect(archivePath, cache, overlayIndex.hasOverlays, chapters.size) {
        Timber.tag("MediaOverlayDiag").d(
            "session archive=${archivePath.ifBlank { "none" }} smilReader=${smilReader != null} " +
                "overlays=${overlayIndex.hasOverlays} narratedSpineItems=${overlayIndex.smilPathBySpineItem.size} " +
                "chapters=${chapters.size} mappedChapters=${chapterSpineItems.size} narratedChapters=${
                    readingOrder.count { overlayIndex.smilPathBySpineItem.containsKey(it) }
                }"
        )
    }
    // The session is remembered even when there is nothing to narrate. Returning before this call
    // would make the hook count depend on whether the archive resolved, which is exactly the
    // inconsistency Compose's slot table cannot recover from — the null is carried as a value
    // instead, so every composition calls the same things.
    return remember(cache, chapters, bookId) {
        cache?.let { documentCache ->
            SharedMediaOverlaySession(
                engine = engine,
                projector = EpubMediaOverlayProjector(
                    cache = documentCache,
                    chapters = chapters,
                    blocksForChapter = blocksForChapter
                ),
                bookId = bookId,
                bookTitle = bookTitle,
                narrator = narrator,
                totalDurationMs = totalDurationMs,
                overlayIndex = overlayIndex,
                spineItemIndexByChapter = chapterSpineItems,
                spineItemsInReadingOrder = readingOrder
            )
        }
    }
}
