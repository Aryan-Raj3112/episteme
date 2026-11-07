package com.aryan.reader.shared.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aryan.reader.shared.currentTimestamp

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
    /**
     * Where narration positions are kept between opens, or null when this surface does not keep them.
     *
     * Not optional by accident: the store is the difference between "reopening resumes at the last
     * sentence" and "reopening starts at the top of the section", so a surface without one narrates
     * exactly as it did before this existed.
     */
    private val narrationProgressStore: SharedMediaOverlayNarrationProgressStore? = null,
) {
    /** The chapter narration is currently in, or null. Follow compares this with the visible one. */
    var chapterIndex: Int? by mutableStateOf(null)
        private set

    /** How many clips the loaded chapter has, for the bar's position label. */
    var clipCount: Int by mutableStateOf(0)
        private set

    /**
     * The loaded chapter's playable clips in playback order.
     *
     * Read by Listen's transcript anchoring: each entry names the text element
     * it narrates, so the transcript highlight follows the exact line instead
     * of a fractional estimate. Updated wherever [clipCount] is, and empty
     * whenever nothing is loaded — never a stale chapter's clips.
     */
    var currentPlanEntries: List<SharedMediaOverlayClip> by mutableStateOf(emptyList())
        private set

    /**
     * The spine item those clips narrate, or null when nothing is loaded.
     *
     * Lets Listen verify a clip table belongs to the chapter it is showing:
     * entries alone do not say which chapter they came from.
     */
    var currentSpineItemIndex: Int? by mutableStateOf(null)
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

    /**
     * The spine item [chapterIndex] was projected from, so a chapter is never recorded against another
     * spine item's clip.
     *
     * Needed because a content document split at TOC fragments gives several chapters one spine item:
     * the spine item alone cannot say whether a held-over chapter still describes the loaded chapter.
     * Cleared by [start], because a new run's first clip must not inherit the previous run's chapter.
     */
    private var projectedSpineItemIndex: Int? = null

    init {
        // Running off the end of a chapter continues into the next narrated one. Installed here
        // rather than by the screen because the decision needs the book's reading order and the
        // overlay index, both of which the session already holds — and because it is the session,
        // not the screen, that owns the engine's lifetime.
        installPlaybackHooks()
    }

    /**
     * Resolves a playback position into reader coordinates and records the chapter it landed in.
     *
     * Fused because the two were always used together and separating them invited a screen that
     * projected without recording — which reads as "narration follows nowhere".
     *
     * @param playbackIndex the position within the loaded clip list, i.e.
     *   [SharedMediaOverlayPlaybackState.clipIndex] — **not** a document clip index. The two differ
     *   whenever [sharedMediaOverlayPlaybackPlan] dropped a clip (a `par` with no audio), and a caller
     *   that projected the position as a document index would paint the band one line early for every
     *   dropped clip and — worse — resolve the *chapter* from the wrong clip, so the position recorded
     *   for the next open would name a chapter playback is not in. The plan is the translation, and it
     *   is only valid for the chapter it was loaded for.
     */
    suspend fun project(spineItemIndex: Int, playbackIndex: Int): SharedMediaOverlayProjection? {
        // A stale position: the engine is still reporting the chapter the session has already replaced,
        // so this index describes nothing in the loaded plan. Projecting it anyway is what paints a
        // band for the wrong line (or freezes the last one) across a chapter change.
        if (spineItemIndex != currentSpineItemIndex) return null
        val documentClipIndex = currentPlanEntries.getOrNull(playbackIndex)?.clipIndex ?: return null
        val projection = projector.project(spineItemIndex, documentClipIndex)
        chapterIndex = projection?.chapterIndex
        projectedSpineItemIndex = spineItemIndex.takeIf { projection?.chapterIndex != null }
        return projection
    }

    /**
     * Starts narrating the chapter the reader is in, from where they are.
     *
     * Returns false when the chapter has nothing to narrate — which is a normal outcome, not an
     * error: a book can narrate some chapters and not others, and `epub:type` can mark a chapter
     * as skippable. The reader gets a message rather than a button that does nothing.
     *
     * @param resumeSavedNarration whether this book's stored narration position wins over
     *   [readerOffset]. True for a play press after opening the book, because the stored position is
     *   the last thing actually heard and the reading position may be a page — or a whole background
     *   session — behind it. False when the reader moved deliberately, where their position is the
     *   intent. See [sharedMediaOverlayStartClipIndex].
     */
    suspend fun start(
        chapterIndex: Int?,
        readerOffset: Int?,
        resumeSavedNarration: Boolean = true,
    ): Boolean {
        val spineItemIndex = chapterIndex?.let(spineItemIndexByChapter::get) ?: return false
        val document = projector.document(spineItemIndex) ?: return false
        val plan = sharedMediaOverlayPlaybackPlan(document)
        if (plan.isEmpty) return false
        val readerClipIndex = readerOffset
            ?.takeIf { it >= 0 }
            ?.let { projector.clipIndexForReaderPosition(spineItemIndex, it) }
        // A store that cannot answer must not take the play press down with it: the position is an
        // optimisation on top of "start where the reader is", and this runs inside the screen's click
        // coroutine, where an escaping exception is a fatal one.
        val savedProgress = if (resumeSavedNarration) {
            runCatching { narrationProgressStore?.load(bookId) }.getOrNull()
        } else {
            null
        }
        val startClipIndex = sharedMediaOverlayStartClipIndex(
            saved = savedProgress,
            spineItemIndex = spineItemIndex,
            chapterIndex = chapterIndex,
            readerClipIndex = readerClipIndex,
            resumeSavedNarration = resumeSavedNarration,
        )
        clipCount = plan.entries.size
        currentPlanEntries = plan.entries
        currentSpineItemIndex = spineItemIndex
        // The previous run's projection describes the chapter it resolved, which may be another chapter
        // of this very document (a split content document). Clearing it here is what keeps the first
        // clip of this run from recording a position labelled with a chapter it is not in.
        this.chapterIndex = null
        projectedSpineItemIndex = null
        bandSessionId = nextSharedPlaybackBandSessionId()
        // Reinstalled on every start, not just in init: sessions share engines
        // across surfaces now (reader and Listen narration), so the last
        // starter must own continuation. Whoever stopped last never fires —
        // only a genuine finish calls the hook — so reinstalling cannot
        // resurrect a session its owner ended.
        installPlaybackHooks()
        engine.play(requestFor(spineItemIndex, plan, sharedMediaOverlayStartPlaybackIndex(plan, startClipIndex)))
        return true
    }

    /**
     * Records where narration is, against the position that just started.
     *
     * Called from the engine's clip hook rather than from a screen's state collector so it keeps
     * firing while the reader is backgrounded — the case where the text position stops moving and the
     * narration does not. A session with no store records nothing.
     */
    private fun recordNarrationProgress(spineItemIndex: Int, playbackIndex: Int) {
        val store = narrationProgressStore ?: return
        // An engine that loads asynchronously can announce the previous request's first clip after this
        // session has already moved on; only the loaded chapter's own clips describe this position.
        if (spineItemIndex != currentSpineItemIndex) return
        val clip = currentPlanEntries.getOrNull(playbackIndex) ?: return
        // Only a chapter the projection resolved *for this spine item* describes this clip, and the
        // first clip of a run is reported before that projection lands. Recording a guessed chapter
        // there would be worse than recording nothing: a content document split at TOC fragments has
        // several chapters, the guess would name the document's first one, and the resume it produced
        // would be rejected on the next open as "another chapter" — which is the restart-at-the-top
        // this whole store exists to prevent. Leaving the previous, accurate row in place is correct:
        // a resume for a different chapter is rejected anyway.
        val confirmedChapter = chapterIndex.takeIf { projectedSpineItemIndex == spineItemIndex } ?: return
        store.record(
            SharedMediaOverlayNarrationProgress(
                bookId = bookId,
                spineItemIndex = spineItemIndex,
                chapterIndex = confirmedChapter,
                clipIndex = clip.clipIndex,
                updatedAt = currentTimestamp(),
            )
        )
    }

    /**
     * Installs the session's engine hooks.
     *
     * Both hooks are reinstalled on every start because sessions share engines across surfaces (reader
     * and Listen narration), so the last starter owns continuation and recording.
     */
    private fun installPlaybackHooks() {
        engine.onChapterFinished = { finished -> continueAfter(finished) }
        engine.onClipEntered = { spineItemIndex, playbackIndex ->
            recordNarrationProgress(spineItemIndex, playbackIndex)
        }
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
            currentPlanEntries = plan.entries
            currentSpineItemIndex = candidate
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
        projectedSpineItemIndex = null
        clipCount = 0
        currentPlanEntries = emptyList()
        currentSpineItemIndex = null
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