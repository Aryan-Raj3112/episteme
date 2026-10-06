package com.aryan.reader.epubreader

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.aryan.reader.shared.reader.SharedMediaOverlayPlaybackRequest
import com.aryan.reader.shared.reader.SharedMediaOverlayPlaybackState
import com.aryan.reader.shared.reader.SharedPlaybackFragment
import com.aryan.reader.shared.reader.rememberSharedMediaOverlaySmilReader
import com.aryan.reader.shared.reader.sharedMediaOverlayPlaybackPlan
import com.aryan.reader.shared.reader.sharedMediaOverlayStartPlaybackIndex
import kotlinx.coroutines.flow.MutableStateFlow
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
     * Resolves one playback position, or null when nothing there can be painted.
     *
     * Null is the normal outcome for a clip with no resolvable text anchor — an unresolvable anchor
     * is a fact about the book, not a failure, and playback must keep going.
     */
    suspend fun project(spineItemIndex: Int, clipIndex: Int): EpubMediaOverlayProjection? {
        val document = cache.document(spineItemIndex) ?: return null
        val clip = document.clips.getOrNull(clipIndex) ?: return null
        var chapterIndex: Int? = null
        var fragment: SharedPlaybackFragment? = null
        // A plain loop rather than `firstOrNull`: resolving anchors suspends, and a split chapter
        // that does not carry the element must be skipped rather than assumed to hold it.
        for (candidate in candidateChapters(document)) {
            val resolved = anchorsFor(document, candidate)[clipIndex] ?: continue
            chapterIndex = candidate
            fragment = resolved
            break
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
            val anchors = anchorsFor(document, chapterIndex)
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
     * The chapter's resolved anchors, computed once and reused.
     *
     * Only non-empty results are cached. An empty map is a legitimate answer (the chapter does not
     * carry the document's ids), and caching it would be wrong when the reason was a chapter whose
     * blocks were not loadable yet — the paginator builds them on demand, so retrying is both
     * possible and cheap, while a stuck empty map would silence a chapter for the rest of the
     * session.
     */
    private suspend fun anchorsFor(
        document: SharedMediaOverlayDocument,
        chapterIndex: Int
    ): Map<Int, SharedPlaybackFragment> {
        anchorsByChapter[chapterIndex]?.let { return it }
        val blocks = blocksForChapter(chapterIndex).orEmpty()
        val anchors = if (blocks.isEmpty()) {
            emptyMap()
        } else {
            resolveSharedMediaOverlayFragmentsInBlocks(document, blocks)
        }
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
    private val spineItemIndexByChapter: Map<Int, Int>,
) {
    /** The chapter narration is currently in, or null. Follow compares this with the visible one. */
    var chapterIndex: Int? by mutableStateOf(null)
        private set

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
        engine.play(
            SharedMediaOverlayPlaybackRequest(
                sourceId = bookId,
                bookTitle = bookTitle,
                spineItemIndex = spineItemIndex,
                clips = plan.entries,
                startPlaybackIndex = sharedMediaOverlayStartPlaybackIndex(plan, readerClipIndex),
                playWhenReady = true,
                narrator = narrator,
                totalDurationMs = totalDurationMs
            )
        )
        return true
    }

    fun togglePlayPause() {
        if (engine.state.value.isPlaying) engine.pause() else engine.resume()
    }

    fun previousClip() = engine.skipPrevious()

    fun nextClip() = engine.skipNext()

    fun stop() {
        chapterIndex = null
        clipCount = 0
        engine.stop()
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
                spineItemIndexByChapter = spineItemIndexByChapter(chapters, overlayIndex)
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
 */
@Composable
internal fun EpubMediaOverlayBar(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onTogglePlayPause: () -> Unit,
    onPreviousClip: () -> Unit,
    onNextClip: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

            IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.content_desc_media_overlay_stop)
                )
            }
        }
    }
}
