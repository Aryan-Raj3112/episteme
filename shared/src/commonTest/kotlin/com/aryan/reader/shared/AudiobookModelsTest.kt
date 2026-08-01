package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudiobookModelsTest {
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

    private fun item(id: String, progress: Float, isTts: Boolean = false, updatedAt: Long = 0L) =
        SharedAudiobookLibraryItem(id, progress, isTts, updatedAt)
}
