package com.aryan.reader.shared.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The reader-side half of media overlay narration: one engine, one projector, and the decisions that
 * need both.
 *
 * Promoted out of the Android reader so iOS runs the same sequence rather than a second copy of it.
 * Everything a user would *hear* a difference in lives here — which chapter starts, what plays next,
 * when the run ends — while everything platform-specific stays behind two seams:
 *
 * - [SharedMediaOverlayPlayback] moves the audio. ExoPlayer clips per `MediaItem` on Android, an
 *   `AVPlayer` that seeks per clip on iOS; both are handed the same plan and both report the same
 *   state, so the sequence above is the one thing that decides what happens next.
 * - [SharedMediaOverlayProjectionSource] resolves a clip to reader coordinates. Android's blocks are
 *   the paginator's `ContentBlock`s, iOS's are the shared semantic IR, and neither can see the
 *   other's — but both answer the same three questions, which is all this session asks.
 *
 * Deliberately not a ViewModel on either platform. Narration is reader-screen state: it starts when
 * the reader does and ends when the reader leaves.
 */
class SharedMediaOverlaySession(
    val engine: SharedMediaOverlayPlayback,
    private val projector: SharedMediaOverlayProjectionSource,
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

    /** How many clips the loaded chapter has, for the bar's position label. */
    var clipCount: Int by mutableStateOf(0)
        private set

    /**
     * Identifies the current run, so one run's band is never read as another's.
     *
     * Only the shared reader screen reads this. Android hands its surfaces the raw
     * [SharedPlaybackFragment] and paints it through the read-aloud parameter, which carries no band
     * id at all — so the two platforms reach the same paint path by different routes, and this is
     * the shared reader's half of that.
     *
     * Run-owned rather than process-global, because a *run* is the unit whose bands must not be
     * confused, and the reader that would confuse them is a second one open at the same time.
     * Minting it here also means a new run gets a new id without the screen knowing one exists: the
     * caller passes this straight to [sharedMediaOverlayPlaybackBand].
     */
    var bandSessionId: Long by mutableStateOf(nextSharedPlaybackBandSessionId())
        private set

    init {
        // Running off the end of a chapter continues into the next narrated one. Installed here
        // rather than by the screen because the decision needs the book's reading order and the
        // overlay index, both of which the session already holds — and because it is the session,
        // not the screen, that owns the engine's lifetime.
        engine.onChapterFinished = { finished -> continueAfter(finished) }
    }

    /**
     * Resolves a playback position into reader coordinates and records the chapter it landed in.
     *
     * Fused because the two were always used together and separating them invited a screen that
     * projected without recording — which reads as "narration follows nowhere".
     */
    suspend fun project(spineItemIndex: Int, clipIndex: Int): SharedMediaOverlayProjection? {
        val projection = projector.project(spineItemIndex, clipIndex)
        chapterIndex = projection?.chapterIndex
        return projection
    }

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
        bandSessionId = nextSharedPlaybackBandSessionId()
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
            // A continuation is the same run, not a new one: the reader pressed play once and is
            // listening to a book, so the band keeps the id it started with. Only a fresh `start`
            // gets a new one.
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
 * A playback-band session id that differs from every id this process has handed out.
 *
 * Process-wide rather than per-session because the id's only job is to keep one run's bands from
 * being read as another's, and two runs can paint into the same reader — a split view, or a reader
 * opened while another is still composed. A counter each session owned would start both at 1 and
 * collide on exactly the case the id exists for.
 *
 * Plain and unsynchronized because it is only ever read and written on the composition thread that
 * called [SharedMediaOverlaySession.start]; a concurrent collision would need two compositions
 * racing inside one frame, and the cost of being wrong is a band id, not corrupted state.
 */
private var sharedPlaybackBandSessionCounter = 0L

internal fun nextSharedPlaybackBandSessionId(): Long {
    sharedPlaybackBandSessionCounter += 1L
    return sharedPlaybackBandSessionCounter
}

/**
 * Which spine item narrates which reader chapter.
 *
 * The reader's chapter list is its own re-flow — split at TOC fragments, so the reference book has
 * 159 chapters for 156 spine items — while overlays are linked to *spine items*. Matching by content
 * path is the only identity the two share, and the index's
 * [SharedMediaOverlayIndex.contentPathBySpineItem] is what makes it exact rather than a guess at
 * the overlay's own `epub:textref`.
 *
 * A chapter whose path matches no spine item is absent, which is the correct answer: it has no
 * source document and therefore no narration.
 *
 * Takes content paths rather than chapters because the two platforms' chapter types share no shape,
 * and this is the one fact about a chapter that both have.
 */
fun sharedMediaOverlaySpineItemIndexByChapter(
    chapterContentPaths: List<String>,
    overlayIndex: SharedMediaOverlayIndex
): Map<Int, Int> {
    if (chapterContentPaths.isEmpty() || overlayIndex.contentPathBySpineItem.isEmpty()) return emptyMap()
    val spineByPath = HashMap<String, Int>(overlayIndex.contentPathBySpineItem.size)
    overlayIndex.contentPathBySpineItem.forEach { (spineItemIndex, path) ->
        val key = normalizeSharedMediaOverlayPath(path)
        // First writer wins, so a book whose spine names the same document twice still resolves to
        // the *earlier* item: that is the one a reader's re-flow started from.
        if (key.isNotEmpty() && !spineByPath.containsKey(key)) spineByPath[key] = spineItemIndex
    }
    if (spineByPath.isEmpty()) return emptyMap()
    val byChapter = HashMap<Int, Int>(chapterContentPaths.size)
    chapterContentPaths.forEachIndexed { chapterIndex, path ->
        spineByPath[normalizeSharedMediaOverlayPath(path)]?.let { byChapter[chapterIndex] = it }
    }
    return byChapter
}

/**
 * The book's spine items in reading order, deduplicated.
 *
 * Reads the chapter list rather than the spine so a document split into several reader chapters
 * contributes one entry, and a chapter with no spine item — a cover page the reader synthesises —
 * contributes none. This is what [sharedMediaOverlaySpineItemsAfter] walks to find the next narrated
 * chapter, so getting the order wrong is heard as narration that skips or repeats a chapter.
 */
fun sharedMediaOverlaySpineItemsInReadingOrder(
    chapterCount: Int,
    spineItemIndexByChapter: Map<Int, Int>
): List<Int> {
    if (chapterCount <= 0) return emptyList()
    val ordered = LinkedHashSet<Int>(chapterCount)
    for (chapterIndex in 0 until chapterCount) {
        spineItemIndexByChapter[chapterIndex]?.let(ordered::add)
    }
    return ordered.toList()
}

/**
 * Two paths that name the same document compare equal here.
 *
 * A spine item's `absPath` and a chapter's own path routinely differ by a leading slash, a fragment,
 * or a query string, and a mapping that missed on those would drop a chapter's narration while still
 * looking correct for every other chapter in the book.
 */
fun normalizeSharedMediaOverlayPath(path: String): String =
    path.substringBefore('#').substringBefore('?').trim().trimStart('/')