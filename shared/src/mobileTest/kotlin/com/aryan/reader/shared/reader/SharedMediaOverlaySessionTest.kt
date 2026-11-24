package com.aryan.reader.shared.reader

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The narration session: which chapter starts, what plays next, and when the run ends.
 *
 * Everything here is the part two platforms would otherwise each decide for themselves, and the
 * failure is a highlight that drifts from the voice — so the cases are chosen for where the *sequence*
 * can go wrong rather than for what any one engine does with audio.
 *
 * The engine is a fake that records requests. That is deliberate: the question here is never "what
 * does ExoPlayer do with this list" but "which list does the session hand over, and when does it hand
 * over another", and a fake is what makes the second question answerable at all.
 */
class SharedMediaOverlaySessionTest {

    // --- the chapter <-> spine mapping ------------------------------------------------------

    /**
     * The reader's chapter list is its own re-flow, so the two indexes do not line up: the reference
     * book has 159 chapters for 156 spine items. Matching by content path rather than by position is
     * what stops every chapter after the first split narrating the wrong thing.
     */
    @Test
    fun `chapters map to spine items by content path and not by position`() {
        val mapping = sharedMediaOverlaySpineItemIndexByChapter(
            chapterContentPaths = listOf(
                "OEBPS/Text/cover.xhtml",
                "OEBPS/Text/p009.xhtml",
                "OEBPS/Text/p009.xhtml",
                "OEBPS/Text/p010.xhtml"
            ),
            overlayIndex = index(
                0 to "OEBPS/Text/cover.xhtml",
                4 to "OEBPS/Text/p009.xhtml",
                5 to "OEBPS/Text/p010.xhtml"
            )
        )
        assertEquals(mapOf(0 to 0, 1 to 4, 2 to 4, 3 to 5), mapping)
    }

    /**
     * A chapter whose path matches no spine item is absent rather than guessed. A wrong mapping here
     * narrates one chapter's audio under another chapter's highlight, which is worse than no narration.
     */
    @Test
    fun `a chapter with no spine item is absent`() {
        val mapping = sharedMediaOverlaySpineItemIndexByChapter(
            chapterContentPaths = listOf("OEBPS/Text/p009.xhtml", "OEBPS/Text/synthesised.xhtml"),
            overlayIndex = index(2 to "OEBPS/Text/p009.xhtml")
        )
        assertEquals(mapOf(0 to 2), mapping)
    }

    /**
     * A spine item's `absPath` and a chapter's own path routinely disagree by a leading slash, a
     * fragment or a query. Missing on those would silently drop a chapter's narration while every
     * other chapter in the book still worked.
     */
    @Test
    fun `path spelling differences do not lose the link`() {
        val mapping = sharedMediaOverlaySpineItemIndexByChapter(
            chapterContentPaths = listOf("/OEBPS/Text/p009.xhtml#f2", "OEBPS/Text/p010.xhtml?x=1"),
            overlayIndex = index(
                1 to "OEBPS/Text/p009.xhtml",
                2 to "OEBPS/Text/p010.xhtml"
            )
        )
        assertEquals(mapOf(0 to 1, 1 to 2), mapping)
    }

    @Test
    fun `a book without overlays maps nothing`() {
        assertTrue(
            sharedMediaOverlaySpineItemIndexByChapter(
                chapterContentPaths = listOf("OEBPS/Text/p009.xhtml"),
                overlayIndex = SharedMediaOverlayIndex.EMPTY
            ).isEmpty()
        )
    }

    /**
     * Reading order is the *chapter* sequence, deduplicated by spine item. A spine array would put a
     * book whose documents were split at TOC fragments in the wrong order, which is heard as narration
     * that skips or repeats a chapter.
     */
    @Test
    fun `reading order follows the chapters and names each spine item once`() {
        val order = sharedMediaOverlaySpineItemsInReadingOrder(
            chapterCount = 4,
            spineItemIndexByChapter = mapOf(0 to 0, 1 to 4, 2 to 4, 3 to 5)
        )
        assertEquals(listOf(0, 4, 5), order)
    }

    // --- starting ---------------------------------------------------------------------------

    /**
     * Pressing play mid-chapter narrates from where the reader is, not from the top of the chapter.
     * The clip chosen is the first one that has not finished at the reader's offset.
     */
    @Test
    fun `starting mid-chapter begins at the clip covering the reader`() = runTest {
        val projector = projector(clip(0, 0, 1_000), clip(1, 1_000, 2_000), clip(2, 2_000, 3_000))
        val session = session(projector = projector)

        assertTrue(session.start(chapterIndex = 1, readerOffset = 1_200))
        assertEquals(1, engine.played.last().startPlaybackIndex)
    }

    /**
     * A null offset means "from the beginning", and a chapter with no overlay means "nothing to
     * narrate" — reported, never thrown, because a book narrating only some of its chapters is the
     * normal shape and the reader gets a message rather than a button that does nothing.
     */
    @Test
    fun `an un-narrated chapter reports false rather than failing`() = runTest {
        val session = session(projector = projector(clip(0, 0, 1_000)))

        assertFalse(session.start(chapterIndex = 7, readerOffset = null))
        assertFalse(session.start(chapterIndex = null, readerOffset = null))
        assertTrue(engine.played.isEmpty())
    }

    /**
     * A chapter whose every clip is a TTS `par` has no audio to play. The plan is empty, so the
     * session declines rather than handing the engine a chapter that cannot make a sound.
     */
    @Test
    fun `a chapter with no playable clips declines to start`() = runTest {
        val session = session(projector = projector(clip(0, 0, 1_000, audioPath = null)))

        assertFalse(session.start(chapterIndex = 1, readerOffset = null))
        assertTrue(engine.played.isEmpty())
    }

    @Test
    fun `starting publishes the clip count for the bar`() = runTest {
        val session = session(projector = projector(clip(0, 0, 1_000), clip(1, 1_000, 2_000)))

        session.start(chapterIndex = 1, readerOffset = null)

        assertEquals(2, session.clipCount)
    }

    // --- resuming ---------------------------------------------------------------------------

    /**
     * Reopening a book resumes the narration at the sentence that was last heard, not at the page the
     * reader restored to.
     *
     * The saved reading position is page-granular — and a backgrounded reader observes no pages at
     * all — so on a chapter of several hundred sentences the clip it resolves to can be the chapter's
     * first. The stored narration position is the only record of what was actually played.
     */
    @Test
    fun `a stored narration position wins over the reader's page`() = runTest {
        val store = RecordingProgressStore(saved = progress(chapterIndex = 1, clipIndex = 2))
        val session = session(
            projector = projector(clip(0, 0, 1_000), clip(1, 1_000, 2_000), clip(2, 2_000, 3_000)),
            store = store
        )

        assertTrue(session.start(chapterIndex = 1, readerOffset = 200))

        assertEquals(2, engine.played.last().startPlaybackIndex)
    }

    /**
     * A chapter the reader moved to deliberately narrates from *their* position. Resuming the stored
     * position there would rewind a reader who tapped a chapter.
     */
    @Test
    fun `an explicit move narrates the reader's position over the stored one`() = runTest {
        val store = RecordingProgressStore(saved = progress(chapterIndex = 1, clipIndex = 2))
        val session = session(
            projector = projector(clip(0, 0, 1_000), clip(1, 1_000, 2_000), clip(2, 2_000, 3_000)),
            store = store
        )

        assertTrue(session.start(chapterIndex = 1, readerOffset = 200, resumeSavedNarration = false))

        assertEquals(0, engine.played.last().startPlaybackIndex)
    }

    /**
     * Reading on within the chapter narration stopped in is the reader reading on: the *later* of the
     * two positions wins, so they hear from where they are looking.
     */
    @Test
    fun `a reader positioned past the stored clip starts from the reader`() = runTest {
        val store = RecordingProgressStore(saved = progress(chapterIndex = 1, clipIndex = 1))
        val session = session(
            projector = projector(clip(0, 0, 1_000), clip(1, 1_000, 2_000), clip(2, 2_000, 3_000)),
            store = store
        )

        assertTrue(session.start(chapterIndex = 1, readerOffset = 2_400))

        assertEquals(2, engine.played.last().startPlaybackIndex)
    }

    /**
     * A TOC-split book has several reader chapters on one spine item, so a stored position there is
     * not automatically this chapter's: narration must not jump into a different chapter's text.
     */
    @Test
    fun `a stored position in another chapter of the same spine item is ignored`() = runTest {
        val store = RecordingProgressStore(saved = progress(chapterIndex = 2, clipIndex = 2))
        val session = session(
            projector = projector(clip(0, 0, 1_000), clip(1, 1_000, 2_000), clip(2, 2_000, 3_000)),
            store = store
        )

        assertTrue(session.start(chapterIndex = 1, readerOffset = null))

        assertEquals(0, engine.played.last().startPlaybackIndex)
    }

    /**
     * Every clip announces itself to the store, which is what makes the position survive a close: the
     * save happens on the clip boundary, not on a page turn the reader may never make.
     */
    @Test
    fun `each clip entered is recorded`() = runTest {
        val store = RecordingProgressStore()
        val session = session(
            projector = projector(clip(0, 0, 1_000), clip(1, 1_000, 2_000), clip(2, 2_000, 3_000)),
            store = store
        )

        session.start(chapterIndex = 1, readerOffset = null)
        // The chapter label comes from the projection, so a clip is only recorded once one has
        // resolved it — see `the first clip of a run is not recorded before its chapter is known`.
        session.project(1, 0)
        session.nextClip()
        session.nextClip()

        assertEquals(listOf(1, 2), store.recorded.map { it.clipIndex })
        assertEquals("book", store.recorded.first().bookId)
        assertEquals(1, store.recorded.last().spineItemIndex)
    }

    /**
     * The first clip of a run plays before the projection that names its chapter has landed. Recording
     * it anyway would mean guessing a chapter, and the only guess available — the document's first
     * chapter — is a different chapter on a TOC-split book, so the next open would reject the position
     * as "another chapter" and restart at the top of the section, which is the bug this store exists to
     * prevent. The previous, accurate row is the better answer.
     */
    @Test
    fun `the first clip of a run is not recorded before its chapter is known`() = runTest {
        val store = RecordingProgressStore()
        val session = session(projector = projector(clip(0, 0, 1_000)), store = store)

        session.start(chapterIndex = 1, readerOffset = null)

        assertEquals(emptyList(), store.recorded.map { it.clipIndex })
        assertNull(session.chapterIndex)
    }

    /**
     * A start clears the projection the previous run left behind. Two runs in the same chapter would
     * otherwise look "already projected" and the new run's first clip would be recorded against the
     * previous run's chapter — on a split document, specifically the wrong one.
     */
    @Test
    fun `a restart does not inherit the previous run's chapter`() = runTest {
        val store = RecordingProgressStore()
        val session = session(projector = projector(clip(0, 0, 1_000)), store = store)

        session.start(chapterIndex = 1, readerOffset = null)
        session.project(1, 0)
        session.stop()
        store.recorded.clear()

        session.start(chapterIndex = 1, readerOffset = null)

        assertEquals(emptyList(), store.recorded.map { it.clipIndex })
        assertNull(session.chapterIndex)
    }

    // --- continuation -----------------------------------------------------------------------

    /**
     * Running off the end of a chapter carries the narration into the next narrated one. This is the
     * behaviour a listener expects from a recording and the reason the engine reports the finish at
     * all — without it every chapter would stop dead at its last line.
     */
    @Test
    fun `a finished chapter continues into the next narrated one`() = runTest {
        val projector = projector(
            clip(0, 0, 1_000),
            spineItemIndex = 0,
            alsoAt = mapOf(4 to listOf(clip(0, 0, 1_000), clip(1, 1_000, 2_000)))
        )
        val session = narratedRun(projector)
        session.start(chapterIndex = 0, readerOffset = null)

        engine.onChapterFinished?.invoke(0)

        assertEquals(listOf(0, 4), engine.played.map { it.spineItemIndex })
        assertEquals(0, engine.played.last().startPlaybackIndex)
    }

    /**
     * A narrated book has a cover, a colophon and a playlist between its chapters. Stopping at the
     * first un-narrated item would make continuation almost never fire, so those are stepped over.
     */
    @Test
    fun `an un-narrated chapter between two narrated ones is stepped over`() = runTest {
        // Spine item 3 is declared as narrated but its SMIL cannot be read, which is the same case as
        // one with no overlay at all: the OPF promises intent, and the file may not deliver.
        val projector = projector(
            clip(0, 0, 1_000),
            spineItemIndex = 0,
            alsoAt = mapOf(5 to listOf(clip(0, 0, 1_000), clip(1, 1_000, 2_000)))
        )
        val session = narratedRun(projector, declaredNarrated = listOf(0, 3, 5))
        session.start(chapterIndex = 0, readerOffset = null)

        engine.onChapterFinished?.invoke(0)

        assertEquals(listOf(0, 5), engine.played.map { it.spineItemIndex })
    }

    /**
     * The end of a narrated run ends playback. There is nothing to carry into, and pretending
     * otherwise would loop the book.
     */
    @Test
    fun `the end of a narrated run ends playback`() = runTest {
        val projector = projector(clip(0, 0, 1_000), spineItemIndex = 0)
        val session = narratedRun(projector)
        session.start(chapterIndex = 0, readerOffset = null)

        engine.onChapterFinished?.invoke(0)

        assertEquals(listOf(0), engine.played.map { it.spineItemIndex })
    }

    /**
     * The callback fires *after* the state is reset, so a session that starts the next chapter
     * immediately is not overwritten by the chapter that just finished. Pinned because the ordering is
     * load-bearing and invisible in a happy-path test.
     */
    @Test
    fun `the state is already cleared when continuation is offered the next chapter`() = runTest {
        val projector = projector(
            clip(0, 0, 1_000),
            spineItemIndex = 0,
            alsoAt = mapOf(4 to listOf(clip(0, 0, 1_000), clip(1, 1_000, 2_000)))
        )
        val session = narratedRun(projector)
        session.start(chapterIndex = 0, readerOffset = null)
        var chapterAtCallback: Int? = null
        var spineAtCallback: Int? = null
        session.engine.onChapterFinished = {
            chapterAtCallback = session.chapterIndex
            spineAtCallback = engine.state.value.spineItemIndex
        }

        // Skipping past the chapter's only clip is the real route to a finish, and the one that
        // exercises the ordering: `publishFinished` resets the state *before* it calls back.
        session.nextClip()

        assertNotNull(session.engine.onChapterFinished)
        assertNull(chapterAtCallback)
        assertNull(spineAtCallback)
    }

    /**
     * A stop must not be read as a finish. The two end in the same engine state and are not the same
     * event: a reader who pressed stop would otherwise be narrated at again by the next chapter.
     */
    @Test
    fun `stopping does not offer continuation`() = runTest {
        val projector = projector(
            clip(0, 0, 1_000),
            spineItemIndex = 0,
            alsoAt = mapOf(4 to listOf(clip(0, 0, 1_000), clip(1, 1_000, 2_000)))
        )
        val session = narratedRun(projector)
        session.start(chapterIndex = 0, readerOffset = null)
        var finished = false
        session.engine.onChapterFinished = { finished = true }

        session.stop()

        assertFalse(finished)
        assertEquals(listOf(0), engine.played.map { it.spineItemIndex })
        assertEquals(0, session.clipCount)
        assertNull(session.chapterIndex)
    }

    // --- transport --------------------------------------------------------------------------

    @Test
    fun `stopping clears the chapter and clip count`() {
        val session = session(projector = projector(clip(0, 0, 1_000)))
        runTest { session.start(chapterIndex = 1, readerOffset = null) }

        session.stop()

        assertNull(session.chapterIndex)
        assertEquals(0, session.clipCount)
    }

    // --- projection -------------------------------------------------------------------------

    /**
     * The session records the chapter a projection landed in. Fusing the two is deliberate — a screen
     * that projected without recording is the one that follows nowhere, and the symptom is invisible
     * in a screenshot.
     */
    @Test
    fun `projecting records the chapter the narration is in`() = runTest {
        val projector = projector(clip(0, 0, 1_000), spineItemIndex = 1, chapterIndex = 1)
        val session = session(projector = projector)
        session.start(chapterIndex = 1, readerOffset = null)

        val projection = session.project(1, 0)

        assertEquals(1, session.chapterIndex)
        assertEquals(1, projection?.chapterIndex)
    }

    /** A chapter with no overlay in the book projects to null and records nothing. */
    @Test
    fun `a position outside the loaded document projects to null`() = runTest {
        val session = session(projector = projector(clip(0, 0, 1_000)))

        assertNull(session.project(9, 0))
        assertNull(session.chapterIndex)
    }

    /**
     * The engine reports a position in the *loaded clip list*, which loses its relationship with the
     * document's clip indices as soon as the plan drops one (a `par` with no audio). Projecting the play
     * index as a document index would then paint the band one line early for every dropped clip — and,
     * because the projection also names the chapter a position is recorded against, persist the wrong
     * chapter too. The plan is the translation.
     */
    @Test
    fun `projecting translates the play index back to the document clip`() = runTest {
        val projector = projector(
            clip(0, 0, 1_000),
            // Clip 1 is a TTS-shaped `par`: the plan drops it, so play index 1 is document clip 2.
            clip(1, 1_000, 2_000, audioPath = null),
            clip(2, 2_000, 3_000)
        )
        val session = session(projector = projector)
        session.start(chapterIndex = 1, readerOffset = null)

        val projection = session.project(1, 1)

        assertEquals(2, projection?.clipIndex)
    }

    /**
     * A position from the chapter the session has already replaced describes nothing in the loaded plan,
     * so projecting it would paint a band for the wrong line. Null is the honest answer, and it clears
     * nothing the new chapter's own projection will not set a frame later.
     */
    @Test
    fun `a position from a replaced chapter projects to null`() = runTest {
        val session = session(projector = projector(clip(0, 0, 1_000)))
        session.start(chapterIndex = 1, readerOffset = null)

        assertNull(session.project(2, 0))
    }

    // --- fixtures ---------------------------------------------------------------------------

    /**
     * A recording engine.
     *
     * Deliberately thin, and it publishes through the same [SharedMediaOverlayPlaybackBase] state
     * machine the real engines do, so a session under test sees the same state a real one would.
     */
    private class RecordingEngine : SharedMediaOverlayPlaybackBase() {
        val played = mutableListOf<SharedMediaOverlayPlaybackRequest>()

        override fun play(request: SharedMediaOverlayPlaybackRequest) {
            val start = adopt(request) ?: return
            played += request
            publishClip(request.clips[start], start, isPlaying = true)
        }

        override fun onPlaybackFinished() = publishFinished()
        override fun onClipChanged(playbackIndex: Int) {
            clips.getOrNull(playbackIndex)?.let { publishClip(it, playbackIndex, isPlaying = true) }
        }

        override fun onPauseRequested() = publish { it.copy(isPlaying = false) }
        override fun onResumeRequested() = publish { it.copy(isPlaying = true) }
        override fun onRestartRequested() = Unit
        override fun onSpeedChanged(speed: Float) = publish { it.copy(speed = speed) }
        override fun onStopRequested() = publish { it.copy(isPlaying = false, spineItemIndex = null) }
        override fun seekToClip(clipIndex: Int) {
            val playbackIndex = clips.playbackIndexOfSourceClip(clipIndex)
            if (playbackIndex >= 0) advance(playbackIndex)
        }
    }

    private fun clip(
        index: Int,
        beginMs: Long,
        endMs: Long?,
        audioPath: String? = "OEBPS/Audio/a.mp3"
    ) = SharedMediaOverlayClip(
        parId = "p$index",
        clipIndex = index,
        textHref = "OEBPS/Text/c.xhtml",
        elementId = "p$index",
        audioPath = audioPath,
        clipBeginMs = beginMs,
        clipEndMs = endMs,
        epubTypes = emptySet(),
        seqDepth = 1
    )

    private fun document(spineItemIndex: Int, clips: List<SharedMediaOverlayClip>) =
        SharedMediaOverlayDocument(
            spineItemIndex = spineItemIndex,
            textHref = "OEBPS/Text/c.xhtml",
            clips = clips,
            declaredDurationMs = null
        )

    private fun index(vararg entries: Pair<Int, String>) = SharedMediaOverlayIndex(
        smilPathBySpineItem = entries.associate { (spine, path) -> spine to "OEBPS/mo/$spine.smil" },
        contentPathBySpineItem = entries.toMap(),
        smilIdBySpineItem = entries.associate { (spine, _) -> spine to "smil-$spine" },
        totalDurationMs = null,
        narrator = "Fixture Narrator",
        activeClass = null,
        playbackActiveClass = null,
        declaredDurationMsBySpineItem = emptyMap()
    )

    /** A projector whose documents are supplied per spine item, or absent where none was given. */
    private fun projector(
        vararg clips: SharedMediaOverlayClip,
        spineItemIndex: Int = 1,
        alsoAt: Map<Int, List<SharedMediaOverlayClip>> = emptyMap(),
        chapterIndex: Int = 1
    ): SharedMediaOverlayProjectionSource {
        val documents = buildMap {
            put(spineItemIndex, document(spineItemIndex, clips.toList()))
            alsoAt.forEach { (spine, more) -> put(spine, document(spine, more)) }
        }
        return object : SharedMediaOverlayProjectionSource {
            override fun document(spineItemIndex: Int) = documents[spineItemIndex]
            override suspend fun project(
                spineItemIndex: Int,
                clipIndex: Int
            ): SharedMediaOverlayProjection? {
                val document = documents[spineItemIndex] ?: return null
                if (document.clips.getOrNull(clipIndex) == null) return null
                return SharedMediaOverlayProjection(
                    spineItemIndex = spineItemIndex,
                    clipIndex = clipIndex,
                    chapterIndex = chapterIndex,
                    fragment = SharedPlaybackFragment(blockCfi = "/2/c1/b$clipIndex", startAbs = 0, endAbs = 1)
                )
            }

            override suspend fun clipIndexForReaderPosition(spineItemIndex: Int, readerOffset: Int): Int? =
                documents[spineItemIndex]?.clips?.lastOrNull { it.clipBeginMs <= readerOffset }?.clipIndex
        }
    }

    private lateinit var engine: RecordingEngine

    /**
     * A session whose chapters each name one spine item, for the start/transport cases.
     *
     * Chapter 1 -> spine 1 is the arrangement every other test uses, because it is the one a reader
     * pressing play in the middle of a chapter actually produces.
     */
    private fun session(
        projector: SharedMediaOverlayProjectionSource,
        store: SharedMediaOverlayNarrationProgressStore? = null,
    ): SharedMediaOverlaySession {
        engine = RecordingEngine()
        return SharedMediaOverlaySession(
            engine = engine,
            projector = projector,
            bookId = "book",
            bookTitle = "Fixture",
            narrator = "Fixture Narrator",
            totalDurationMs = null,
            overlayIndex = index(1 to "OEBPS/Text/c.xhtml"),
            spineItemIndexByChapter = mapOf(1 to 1),
            spineItemsInReadingOrder = listOf(1),
            narrationProgressStore = store
        )
    }

    /** The book's stored narration position, for the resume cases. */
    private fun progress(
        chapterIndex: Int?,
        clipIndex: Int,
        spineItemIndex: Int = 1
    ) = SharedMediaOverlayNarrationProgress(
        bookId = "book",
        spineItemIndex = spineItemIndex,
        chapterIndex = chapterIndex,
        clipIndex = clipIndex
    )

    /** Records what the session persists, and answers with a fixed stored position. */
    private class RecordingProgressStore(
        private val saved: SharedMediaOverlayNarrationProgress? = null
    ) : SharedMediaOverlayNarrationProgressStore {
        val recorded = mutableListOf<SharedMediaOverlayNarrationProgress>()

        override suspend fun load(bookId: String): SharedMediaOverlayNarrationProgress? =
            saved?.takeIf { it.bookId == bookId }

        override fun record(progress: SharedMediaOverlayNarrationProgress) {
            recorded += progress
        }
    }

    /**
     * A session for the continuation cases, where the book is a multi-chapter narrated run.
     *
     * Every spine item maps to chapter 0 and every chapter has a distinct path, because continuation
     * walks the *reading order* and the OPF's overlay declarations rather than the chapter mapping —
     * a session wired the other way would pass these cases without ever exercising that walk.
     *
     * @param declaredNarrated spine items the OPF claims have an overlay, which is a superset of the
     *   ones the projector can actually produce a document for: the gap between the two is exactly the
     *   "declared but unusable" case.
     */
    private fun narratedRun(
        projector: SharedMediaOverlayProjectionSource,
        declaredNarrated: List<Int> = projectorDeclaredNarrated(projector)
    ): SharedMediaOverlaySession {
        engine = RecordingEngine()
        return SharedMediaOverlaySession(
            engine = engine,
            projector = projector,
            bookId = "book",
            bookTitle = "Fixture",
            narrator = "Fixture Narrator",
            totalDurationMs = null,
            overlayIndex = index(*declaredNarrated.map { it to "OEBPS/Text/c$it.xhtml" }.toTypedArray()),
            spineItemIndexByChapter = declaredNarrated.mapIndexed { position, spine -> position to spine }.toMap(),
            spineItemsInReadingOrder = declaredNarrated
        )
    }

    /** Which spine items the [projector] fixture can produce a document for. */
    private fun projectorDeclaredNarrated(projector: SharedMediaOverlayProjectionSource): List<Int> =
        (0..9).filter { projector.document(it) != null }
}