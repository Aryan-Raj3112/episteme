package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudiobookModelsTest {
    @Test
    fun `listening handoff preserves one active Android playback source`() {
        assertEquals(
            SharedListeningHandoff.STOP_TTS,
            sharedListeningHandoff(SharedListeningTarget.IMPORTED_AUDIOBOOK),
        )
        assertEquals(
            SharedListeningHandoff.STOP_AUDIOBOOK,
            sharedListeningHandoff(SharedListeningTarget.GENERATED_BOOK_TTS),
        )
    }

    @Test
    fun `supported imports match Android formats case insensitively`() {
        assertTrue(SharedAudiobookFormats.supportsFileName("Book.M4B"))
        assertTrue(SharedAudiobookFormats.supportsFileName("Book.opus"))
        assertFalse(SharedAudiobookFormats.supportsFileName("Book.wav"))
        assertFalse(SharedAudiobookFormats.supportsFileName("Book"))
    }

    @Test
    fun `imported progress clamps position to the available duration`() {
        val base = SharedAudiobook("book", "/book.m4b", "M4B", "Book", durationMs = 100L, addedAt = 1L)
        assertEquals(0f, base.progressFraction)
        assertEquals(0.5f, base.copy(positionMs = 50L).progressFraction)
        assertEquals(1f, base.copy(positionMs = 150L).progressFraction)
        assertEquals(0f, base.copy(durationMs = 0L, positionMs = 50L).progressFraction)
    }

    @Test
    fun `status filters preserve Android progress boundaries`() {
        val items = listOf(item("negative", -1f), item("new", 0f), item("reading", .4f), item("done", 1f), item("over", 2f))
        assertEquals(listOf("negative", "new"), filterSharedAudiobooks(items, SharedAudiobookStatus.NOT_STARTED).map { it.id })
        assertEquals(listOf("reading"), filterSharedAudiobooks(items, SharedAudiobookStatus.IN_PROGRESS).map { it.id })
        assertEquals(listOf("done", "over"), filterSharedAudiobooks(items, SharedAudiobookStatus.COMPLETED).map { it.id })
        assertEquals(items, filterSharedAudiobooks(items, SharedAudiobookStatus.ALL))
    }

    @Test
    fun `continue selection matches Android and prefers freshest unfinished TTS item`() {
        val imported = listOf(item("audio", .5f), item("almost-zero", .001f), item("done", 1f))
        val tts = listOf(item("old-tts", .3f, isTts = true, updatedAt = 5L), item("new-tts", .4f, isTts = true, updatedAt = 9L))
        assertEquals("new-tts", sharedAudiobookContinueItem(imported, tts)?.id)
        assertEquals("audio", sharedAudiobookContinueItem(imported, emptyList())?.id)
        assertNull(sharedAudiobookContinueItem(listOf(item("new", 0f), item("done", 1f)), emptyList()))
    }

    @Test
    fun `tts progress matches Android chapter and chunk calculation`() {
        assertEquals(0f, calculateSharedTtsAudiobookProgress(0, 0, 0, 0))
        assertEquals(.25f, calculateSharedTtsAudiobookProgress(0, 2, 0, 2))
        assertEquals(.75f, calculateSharedTtsAudiobookProgress(1, 2, 0, 2))
        assertEquals(1f, calculateSharedTtsAudiobookProgress(9, 2, 9, 2))
    }

    @Test
    fun `tts listen chunking groups sentences within the chunk limit`() {
        val text = "First sentence here. Second sentence here. Third sentence here."
        val chunks = splitSharedTtsListenChunks(text, maxLength = 30)
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.length <= 30 })
        assertEquals(text, chunks.joinToString(" "))
        assertEquals(3, chunks.size)
        assertEquals(20, chunks.first().length)
    }

    @Test
    fun `tts listen chunking hard splits oversized sentences at word boundaries`() {
        val sentence = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau upsilon phi chi psi omega"
        val chunks = splitSharedTtsListenChunks(sentence, maxLength = 40)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks.all { it.length <= 40 })
        assertEquals(sentence, chunks.joinToString(" "))
    }

    @Test
    fun `tts listen chunking terminates on unbroken long input and blank text`() {
        val unbroken = "x".repeat(10_000)
        val chunks = splitSharedTtsListenChunks(unbroken, maxLength = 250)
        assertEquals(40, chunks.size)
        assertTrue(chunks.all { it.length == 250 })
        assertEquals(emptyList(), splitSharedTtsListenChunks("   \n  "))
        assertEquals(emptyList(), splitSharedTtsListenChunks(""))
    }

    @Test
    fun `resume rewinds ten seconds without seeking before the file`() {
        assertEquals(0L, sharedAudiobookResumePosition(5_000L))
        assertEquals(15_000L, sharedAudiobookResumePosition(25_000L))
        assertEquals(23_000L, sharedAudiobookResumePosition(25_000L, rewindMs = 2_000L))
    }

    @Test
    fun `sleep timer formatting matches Android`() {
        assertEquals("30:00", formatSharedAudiobookSleepTimer(1_800))
        assertEquals("0:09", formatSharedAudiobookSleepTimer(9))
        assertEquals("0:00", formatSharedAudiobookSleepTimer(-1))
    }

    @Test
    fun `sleep timer ms label matches M colon SS countdown`() {
        assertEquals("30:00", formatSharedSleepTimerLabel(1_800_000L))
        assertEquals("0:09", formatSharedSleepTimerLabel(9_000L))
        assertEquals("0:00", formatSharedSleepTimerLabel(-1L))
    }

    @Test
    fun `custom sleep timers validate deduplicate and keep three choices`() {
        assertEquals(listOf(62), addCustomSleepTimer(emptyList(), hours = 1, minutes = 2))
        assertEquals(listOf(62), addCustomSleepTimer(listOf(62), hours = 1, minutes = 2))
        assertEquals(listOf(20, 30, 40), addCustomSleepTimer(listOf(10, 20, 30), hours = 0, minutes = 40))
        assertEquals(listOf(10), addCustomSleepTimer(listOf(10), hours = 0, minutes = 60))
        assertEquals(listOf(10), addCustomSleepTimer(listOf(10), hours = 0, minutes = 0))
    }

    @Test
    fun `custom sleep timer persistence values are sanitized and removable`() {
        assertEquals(listOf(15, 62, 90), sanitizeCustomSleepTimerMinutes(listOf(0, 15, 15, 62, 90, 120, 1500)))
        assertEquals(listOf(15, 90), removeCustomSleepTimer(listOf(15, 62, 90), 62))
    }

    @Test
    fun `sleep timer advances only during active playback`() {
        assertEquals(120, advanceSharedSleepTimer(120, isPlaying = false))
        assertEquals(119, advanceSharedSleepTimer(120, isPlaying = true))
        assertEquals(0, advanceSharedSleepTimer(0, isPlaying = true))
    }

    @Test
    fun `sleep timer preserves paused time across repeated ticks`() {
        var remaining = 3
        remaining = advanceSharedSleepTimer(remaining, isPlaying = false)
        assertEquals(3, remaining)
        remaining = advanceSharedSleepTimer(remaining, isPlaying = true)
        assertEquals(2, remaining)
        remaining = advanceSharedSleepTimer(remaining, isPlaying = false)
        assertEquals(2, remaining)
        remaining = advanceSharedSleepTimer(remaining, isPlaying = true)
        remaining = advanceSharedSleepTimer(remaining, isPlaying = true)
        assertEquals(0, remaining)
    }

    @Test
    fun `remaining label matches Android listen row text`() {
        assertEquals("Duration unavailable", sharedAudiobookRemainingLabel(0L, 0L))
        assertEquals("1 hr 40 min", sharedAudiobookRemainingLabel(6_000_000L, 0L))
        assertEquals("45 min", sharedAudiobookRemainingLabel(6_000_000L, 3_300_000L))
        assertEquals("0 min", sharedAudiobookRemainingLabel(100_000L, 200_000L))
    }

    @Test
    fun `sort matches Android listen sort orders`() {
        fun ab(id: String, addedAt: Long, lastListenedAt: Long, title: String, author: String?, durationMs: Long, positionMs: Long) =
            SharedAudiobook(
                bookId = id, filePath = "/$id", format = "m4b", title = title, author = author,
                durationMs = durationMs, positionMs = positionMs, addedAt = addedAt, lastListenedAt = lastListenedAt,
            )
        val recent = ab("recent", 1L, 100L, "Beta", "Zed", 100L, 0L)
        val added = ab("added", 50L, 0L, "Alpha", "Ann", 100L, 0L)
        val title = ab("title", 20L, 0L, "Middle", "Bob", 100L, 0L)
        val half = ab("half", 2L, 30L, "Halfway", null, 100L, 50L)
        val books = listOf(recent, added, title, half)

        assertEquals(listOf("recent", "half", "added", "title"),
            sortSharedAudiobooks(books, SharedAudiobookSort.RECENTLY_LISTENED).map { it.bookId })
        assertEquals(listOf("added", "title", "half", "recent"),
            sortSharedAudiobooks(books, SharedAudiobookSort.RECENTLY_ADDED).map { it.bookId })
        assertEquals(listOf("added", "recent", "half", "title"),
            sortSharedAudiobooks(books, SharedAudiobookSort.TITLE).map { it.bookId })
        assertEquals(listOf("half", "added", "title", "recent"),
            sortSharedAudiobooks(books, SharedAudiobookSort.AUTHOR).map { it.bookId })
        assertEquals(listOf("half", "recent", "added", "title"),
            sortSharedAudiobooks(books, SharedAudiobookSort.PROGRESS).map { it.bookId })
    }

    @Test
    fun `query matches title author album and narrator`() {
        val book = SharedAudiobook(
            bookId = "id", filePath = "/id", format = "m4b", title = "The Hobbit",
            author = "Tolkien", album = "Middle Earth", narrator = "Reader", addedAt = 1L,
        )
        assertTrue(book.matchesSharedAudiobookQuery("hob"))
        assertTrue(book.matchesSharedAudiobookQuery("  TOLKIEN  "))
        assertTrue(book.matchesSharedAudiobookQuery("middle"))
        assertTrue(book.matchesSharedAudiobookQuery("reader"))
        assertFalse(book.matchesSharedAudiobookQuery("sauron"))
        assertTrue(book.matchesSharedAudiobookQuery(""))
    }

    @Test
    fun `library item mirrors fractional progress and last listened time`() {
        val book = SharedAudiobook(
            bookId = "id", filePath = "/id", format = "m4b", title = "Book",
            durationMs = 100L, positionMs = 40L, addedAt = 1L, lastListenedAt = 9L,
        )
        assertEquals(SharedAudiobookLibraryItem("id", 0.4f, false, 9L), book.toSharedAudiobookLibraryItem())
    }

    @Test
    fun `playback time formatting matches clock expectations`() {
        assertEquals("0:05", formatSharedPlaybackTime(5_000L))
        assertEquals("3:02", formatSharedPlaybackTime(182_000L))
        assertEquals("1:02:03", formatSharedPlaybackTime(3_723_000L))
        assertEquals("0:00", formatSharedPlaybackTime(-1L))
    }

    @Test
    fun `listen source prefers narration when present and respects explicit choice`() {
        assertEquals(
            SharedListenAudioSource.MEDIA_OVERLAY,
            resolveSharedListenAudioSource(hasNarration = true, savedPreference = null),
        )
        assertEquals(
            SharedListenAudioSource.TTS,
            resolveSharedListenAudioSource(hasNarration = false, savedPreference = null),
        )
        // No silent fallback: an explicit TTS pick stays TTS even with narration.
        assertEquals(
            SharedListenAudioSource.TTS,
            resolveSharedListenAudioSource(hasNarration = true, savedPreference = SharedListenAudioSource.TTS),
        )
        assertEquals(
            SharedListenAudioSource.MEDIA_OVERLAY,
            resolveSharedListenAudioSource(hasNarration = false, savedPreference = SharedListenAudioSource.MEDIA_OVERLAY),
        )
    }

    @Test
    fun `listen items carry overlay info by book`() {
        val book = BookItem(
            id = "b1", path = null, type = FileType.EPUB, displayName = "b1.epub",
            timestamp = 1L,
        )
        val overlay = SharedListenMediaOverlayInfo(hasNarration = true, narrator = "Reader", narratedChapterIndices = setOf(0, 2))
        val items = buildSharedTtsListenItems(listOf(book), emptyList(), mapOf("b1" to overlay))
        assertEquals(1, items.size)
        assertTrue(items.first().hasMediaOverlayNarration)
        assertEquals("Reader", items.first().mediaOverlay.narrator)
        assertEquals(setOf(0, 2), items.first().mediaOverlay.narratedChapterIndices)
        val plain = buildSharedTtsListenItems(listOf(book), emptyList())
        assertTrue(plain.first().mediaOverlay.narratedChapterIndices.isEmpty())
    }

    @Test
    fun `listen narrated chapters map spine overlays onto listen chapters`() {
        val index = com.aryan.reader.shared.reader.SharedMediaOverlayIndex(
            smilPathBySpineItem = mapOf(0 to "OEBPS/p0.smil", 2 to "OEBPS/p2.smil"),
            contentPathBySpineItem = mapOf(0 to "OEBPS/p0.xhtml", 1 to "OEBPS/p1.xhtml", 2 to "OEBPS/p2.xhtml"),
            smilIdBySpineItem = mapOf(0 to "s0", 2 to "s2"),
            totalDurationMs = null,
            narrator = "Narrator",
            activeClass = null,
            playbackActiveClass = null,
            declaredDurationMsBySpineItem = emptyMap(),
        )
        val paths = listOf("OEBPS/p0.xhtml", "OEBPS/p1.xhtml", "OEBPS/p2.xhtml")
        assertEquals(setOf(0, 2), sharedListenNarratedChapterIndices(paths, index))
        val info = sharedListenMediaOverlayInfo(paths, index)
        assertTrue(info.hasNarration)
        assertEquals("Narrator", info.narrator)
        assertEquals(setOf(0, 2), info.narratedChapterIndices)
        val empty = sharedListenMediaOverlayInfo(
            paths,
            com.aryan.reader.shared.reader.SharedMediaOverlayIndex.EMPTY,
        )
        assertTrue(!empty.hasNarration)
    }

    @Test
    fun `overlay transcript index maps clip progress onto chunks without word sync claims`() {
        assertEquals(-1, sharedOverlayTranscriptChunkIndex(0, 10, 0))
        assertEquals(0, sharedOverlayTranscriptChunkIndex(0, 0, 5))
        assertEquals(0, sharedOverlayTranscriptChunkIndex(0, 10, 5))
        assertEquals(4, sharedOverlayTranscriptChunkIndex(9, 10, 5))
        assertEquals(2, sharedOverlayTranscriptChunkIndex(5, 10, 5))
        assertEquals(0, sharedOverlayTranscriptChunkIndex(-3, 10, 5))
        assertEquals(4, sharedOverlayTranscriptChunkIndex(99, 10, 5))
        // Single-clip chapter still resolves to the only chunk.
        assertEquals(0, sharedOverlayTranscriptChunkIndex(0, 1, 1))
    }

    @Test
    fun `overlay transcript index interpolates within a clip so long pars do not pin the highlight`() {
        // One clip spanning five chunks: the highlight must travel with the
        // voice instead of sitting on the clip's first chunk.
        assertEquals(0, sharedOverlayTranscriptChunkIndex(0, 1, 5, clipFraction = 0f))
        assertEquals(2, sharedOverlayTranscriptChunkIndex(0, 1, 5, clipFraction = 0.5f))
        assertEquals(4, sharedOverlayTranscriptChunkIndex(0, 1, 5, clipFraction = 1f))
        // Clip boundary behavior is unchanged by the fraction default.
        assertEquals(
            sharedOverlayTranscriptChunkIndex(2, 10, 5),
            sharedOverlayTranscriptChunkIndex(2, 10, 5, clipFraction = 0f),
        )
        // Mid-clip progress reaches into the next chunk rather than trailing it.
        assertEquals(1, sharedOverlayTranscriptChunkIndex(2, 10, 5, clipFraction = 0.5f))
        assertEquals(1, sharedOverlayTranscriptChunkIndex(2, 10, 5, clipFraction = 1f))
        // Out-of-range fractions clamp rather than escaping the chapter.
        assertEquals(0, sharedOverlayTranscriptChunkIndex(0, 4, 4, clipFraction = -1f))
        assertEquals(3, sharedOverlayTranscriptChunkIndex(3, 4, 4, clipFraction = 9f))
        assertEquals(-1, sharedOverlayTranscriptChunkIndex(0, 4, 0, clipFraction = 0.5f))
    }

    private fun item(id: String, progress: Float, isTts: Boolean = false, updatedAt: Long = 0L) =
        SharedAudiobookLibraryItem(id, progress, isTts, updatedAt)
}
