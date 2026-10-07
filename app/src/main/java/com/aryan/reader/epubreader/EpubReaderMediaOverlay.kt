package com.aryan.reader.epubreader

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.R
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.mediaoverlay.AndroidSharedMediaOverlayPlayback
import com.aryan.reader.paginatedreader.ContentBlock
import com.aryan.reader.paginatedreader.resolveSharedMediaOverlayFragmentsInBlocks
import com.aryan.reader.shared.reader.SharedMediaOverlayDocument
import com.aryan.reader.shared.reader.SharedMediaOverlayDocumentCache
import com.aryan.reader.shared.reader.SharedMediaOverlayIndex
import com.aryan.reader.shared.reader.SharedMediaOverlayPlaybackPlan
import com.aryan.reader.shared.reader.SharedMediaOverlayPlaybackRequest
import com.aryan.reader.shared.reader.SharedMediaOverlayPlaybackState
import com.aryan.reader.shared.reader.SharedMediaOverlaySpeeds
import com.aryan.reader.shared.reader.SharedPlaybackFragment
import com.aryan.reader.shared.reader.rememberSharedMediaOverlaySmilReader
import com.aryan.reader.shared.reader.sharedMediaOverlayPlaybackPlan
import com.aryan.reader.shared.reader.sharedMediaOverlaySpeedLabel
import com.aryan.reader.shared.reader.sharedMediaOverlaySpineItemsAfter
import com.aryan.reader.shared.reader.sharedMediaOverlayStartPlaybackIndex
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

/** One narration position, resolved into reader coordinates. */
internal data class EpubMediaOverlayProjection(
    val spineItemIndex: Int,
    /** The chapter holding the narrated element, or null when it could not be placed. */
    val chapterIndex: Int?,
    /** The SMIL element id of the active clip, or null. The WebView anchors on it directly. */
    val elementId: String?,
    /** Where the narrated text is, in chapter-absolute offsets. Null when nothing is anchored. */
    val fragment: SharedPlaybackFragment?,
)

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
) {
    /** How many chapters have had their anchors resolved. Diagnostics; the cost the cache hides. */
    var anchorResolutionCount: Int = 0
        private set

    private val anchorsByChapter = HashMap<Int, Map<Int, SharedPlaybackFragment>>()

    fun document(spineItemIndex: Int): SharedMediaOverlayDocument? =
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
    suspend fun project(spineItemIndex: Int, clipIndex: Int): EpubMediaOverlayProjection? {
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
        return EpubMediaOverlayProjection(
            spineItemIndex = spineItemIndex,
            chapterIndex = chapterIndex,
            elementId = clip.elementId?.takeIf(String::isNotBlank),
            fragment = fragment
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
    suspend fun clipIndexForReaderPosition(spineItemIndex: Int, readerOffset: Int): Int? {
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
        val target = normalizeOverlayPath(textHref)
        if (target.isEmpty()) return emptyList()
        return chapters.indices.filter { index ->
            normalizeOverlayPath(chapters[index].absPath) == target
        }
    }

    private fun normalizeOverlayPath(path: String): String =
        path.substringBefore('#').substringBefore('?').trim().trimStart('/')
}

/**
 * Which spine item narrates each reader chapter.
 *
 * The reader's chapter list is its own re-flow — split at TOC fragments, so the reference book has
 * 159 chapters for 156 spine items — while overlays are linked to *spine items*. Matching by
 * content path is the only identity the two share, and the index's
 * [SharedMediaOverlayIndex.contentPathBySpineItem] is what makes it exact rather than a guess at
 * the overlay's own `epub:textref`.
 *
 * A chapter whose path matches no spine item is absent, which is the correct answer: it has no
 * source document and therefore no narration.
 */
internal fun spineItemIndexByChapter(
    chapters: List<EpubChapter>,
    overlayIndex: SharedMediaOverlayIndex
): Map<Int, Int> {
    if (chapters.isEmpty() || overlayIndex.contentPathBySpineItem.isEmpty()) return emptyMap()
    val spineByPath = HashMap<String, Int>(overlayIndex.contentPathBySpineItem.size)
    overlayIndex.contentPathBySpineItem.forEach { (spineItemIndex, path) ->
        val key = normalizeMediaOverlayPath(path)
        if (key.isNotEmpty()) spineByPath.putIfAbsent(key, spineItemIndex)
    }
    if (spineByPath.isEmpty()) return emptyMap()
    val byChapter = HashMap<Int, Int>(chapters.size)
    chapters.forEachIndexed { chapterIndex, chapter ->
        spineByPath[normalizeMediaOverlayPath(chapter.absPath)]?.let { byChapter[chapterIndex] = it }
    }
    return byChapter
}

internal fun normalizeMediaOverlayPath(path: String): String =
    path.substringBefore('#').substringBefore('?').trim().trimStart('/')

/**
 * The reader's media overlay session: one engine, one projector, and the decisions that need both.
 *
 * Deliberately not a ViewModel. Narration is reader-screen state — it starts when the reader does
 * and ends when they leave — and keeping it here means the screen owns the engine's lifetime
 * exactly as it already owns read-aloud's.
 */
internal class EpubMediaOverlaySession(
    val engine: AndroidSharedMediaOverlayPlayback,
    private val projector: EpubMediaOverlayProjector,
    private val bookId: String,
    private val bookTitle: String,
    private val narrator: String?,
    private val totalDurationMs: Long?,
    private val overlayIndex: SharedMediaOverlayIndex,
    private val spineItemIndexByChapter: Map<Int, Int>,
    /**
     * The book's spine items in the order the reader reads them, without repeats.
     *
     * The reader's chapter list is its own re-flow — a spine document may have been split at several
     * TOC fragments — so the reading order is the chapter sequence, not the spine array, and the same
     * spine item appears once however many chapters reference it.
     */
    private val spineItemsInReadingOrder: List<Int>,
) {
    /** The chapter narration is currently in, or null. Follow compares this with the visible one. */
    var chapterIndex: Int? by mutableStateOf(null)
        private set

    init {
        // Running off the end of a chapter continues into the next narrated one. Installed here rather
        // than by the screen because the decision needs the book's reading order and the overlay
        // index, both of which the session already holds — and because it is the session, not the
        // screen, that owns the engine's lifetime.
        engine.onChapterFinished = { finished -> continueAfter(finished) }
    }

    /** How many clips the loaded chapter has, for the bar's position label. */
    var clipCount: Int by mutableStateOf(0)
        private set

    fun onProjected(projection: EpubMediaOverlayProjection?) {
        chapterIndex = projection?.chapterIndex
    }

    suspend fun project(spineItemIndex: Int, clipIndex: Int): EpubMediaOverlayProjection? =
        projector.project(spineItemIndex, clipIndex)

    /**
     * Starts narrating the chapter the reader is in, from where they are.
     *
     * Returns false when the chapter has nothing to narrate — which is a normal outcome, not an
     * error: a book can narrate some chapters and not others, and `epub:type` can mark a chapter
     * as skippable. The reader gets a message rather than a button that does nothing.
     */
    suspend fun start(chapterIndex: Int?, readerOffset: Int?): Boolean {
        val spineItemIndex = chapterIndex?.let(spineItemIndexByChapter::get) ?: return false
        val document = projector.document(spineItemIndex) ?: return false
        val plan = sharedMediaOverlayPlaybackPlan(document)
        if (plan.isEmpty) return false
        val readerClipIndex = readerOffset
            ?.takeIf { it >= 0 }
            ?.let { projector.clipIndexForReaderPosition(spineItemIndex, it) }
        clipCount = plan.entries.size
        engine.play(requestFor(spineItemIndex, plan, sharedMediaOverlayStartPlaybackIndex(plan, readerClipIndex)))
        return true
    }

    /**
     * Carries narration into the next narrated chapter, or ends it when the book runs out.
     *
     * Called by the engine when a chapter's clips play out. A chapter in between that has no overlay
     * is skipped rather than ending the run, and so is one whose SMIL is declared but unusable — the
     * OPF saying an overlay exists is a promise about intent, not about the file, and stopping there
     * would strand the listener on a chapter they cannot hear.
     *
     * This is synchronous on purpose. Continuation must start before the player goes idle for long
     * enough to be noticed as a gap between chapters, and nothing here needs the anchor resolution a
     * `par` seek would: chapter playback starts at its first clip.
     *
     * @return true when another chapter was loaded; false ends playback, which is what the end of a
     *   narrated run looks like.
     */
    private fun continueAfter(finishedSpineItemIndex: Int): Boolean {
        val candidates = sharedMediaOverlaySpineItemsAfter(
            index = overlayIndex,
            spineItemsInReadingOrder = spineItemsInReadingOrder,
            finishedSpineItemIndex = finishedSpineItemIndex
        )
        for (candidate in candidates) {
            val document = projector.document(candidate) ?: continue
            val plan = sharedMediaOverlayPlaybackPlan(document)
            if (plan.isEmpty) continue
            clipCount = plan.entries.size
            engine.play(requestFor(candidate, plan, startPlaybackIndex = 0))
            return true
        }
        return false
    }

    private fun requestFor(
        spineItemIndex: Int,
        plan: SharedMediaOverlayPlaybackPlan,
        startPlaybackIndex: Int
    ) = SharedMediaOverlayPlaybackRequest(
        sourceId = bookId,
        bookTitle = bookTitle,
        spineItemIndex = spineItemIndex,
        clips = plan.entries,
        startPlaybackIndex = startPlaybackIndex,
        playWhenReady = true,
        narrator = narrator,
        totalDurationMs = totalDurationMs
    )

    fun togglePlayPause() {
        if (engine.state.value.isPlaying) engine.pause() else engine.resume()
    }

    fun previousClip() = engine.skipPrevious()

    fun nextClip() = engine.skipNext()

    fun setSpeed(speed: Float) = engine.setSpeed(speed)

    fun stop() {
        chapterIndex = null
        clipCount = 0
        engine.stop()
    }
}

/**
 * The book's spine items in reading order, deduplicated.
 *
 * Reads the chapter list rather than the spine so a document split into several reader chapters
 * contributes one entry, and a chapter with no spine item — a cover page the reader synthesises —
 * contributes none. This is what `sharedMediaOverlaySpineItemsAfter` walks to find the next narrated
 * chapter, so getting the order wrong is heard as narration that skips or repeats a chapter.
 */
internal fun spineItemsInReadingOrder(
    chapters: List<EpubChapter>,
    spineItemIndexByChapter: Map<Int, Int>
): List<Int> {
    if (chapters.isEmpty()) return emptyList()
    val ordered = LinkedHashSet<Int>(chapters.size)
    chapters.indices.forEach { chapterIndex ->
        spineItemIndexByChapter[chapterIndex]?.let(ordered::add)
    }
    return ordered.toList()
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
): EpubMediaOverlaySession? {
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
        spineItemIndexByChapter(chapters, overlayIndex)
    }
    val readingOrder = remember(chapterSpineItems) {
        spineItemsInReadingOrder(chapters, chapterSpineItems)
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
            EpubMediaOverlaySession(
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

/**
 * The narration bar: title, position, transport and stop.
 *
 * Modelled on the read-aloud overlay's compact row, and deliberately a separate composable rather
 * than a mode of it. The two bars show different things — a spoken chunk's progress against a
 * synthesized session, a narrated clip's position in a publisher's recording — and coupling them
 * would mean one growing conditionals for the other's state.
 *
 * It carries the speed control because narration is the one playback surface where the recording's
 * pace is the publisher's choice rather than the reader's: a 2× listener has no other way to say so,
 * and `SharedMediaOverlaySpeeds` is small enough to be a menu rather than a screen.
 */
@Composable
internal fun EpubMediaOverlayBar(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    isLoading: Boolean,
    speed: Float,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onTogglePlayPause: () -> Unit,
    onPreviousClip: () -> Unit,
    onNextClip: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speedDescription = stringResource(R.string.content_desc_media_overlay_speed)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            IconButton(
                enabled = canSkipPrevious,
                onClick = onPreviousClip,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.content_desc_media_overlay_previous)
                )
            }

            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.content_desc_media_overlay_play_pause)
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        strokeWidth = 2.dp
                    )
                }
            }

            IconButton(
                enabled = canSkipNext,
                onClick = onNextClip,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.content_desc_media_overlay_next)
                )
            }

            var speedMenuExpanded by remember { mutableStateOf(false) }
            Box {
                TextButton(
                    onClick = { speedMenuExpanded = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .size(width = 48.dp, height = 40.dp)
                        .semantics {
                            contentDescription = speedDescription
                        }
                ) {
                    Text(
                        text = sharedMediaOverlaySpeedLabel(speed),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = speedMenuExpanded,
                    onDismissRequest = { speedMenuExpanded = false }
                ) {
                    SharedMediaOverlaySpeeds.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sharedMediaOverlaySpeedLabel(option),
                                    fontWeight = if (option == speed) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            onClick = {
                                speedMenuExpanded = false
                                onSpeedSelected(option)
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.content_desc_media_overlay_stop)
                )
            }
        }
    }
}
