package com.aryan.reader.shared

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderTtsFileCacheManagerTest {

    @Test
    fun `cache files are stable for book chapter text and speaker`() {
        val root = Files.createTempDirectory("reader-tts-cache").toFile()
        try {
            val cache = ReaderTtsFileCacheManager(root)

            val first = cache.getCacheFile("Book: One", "Chapter/One", "Hello world.", "Aoede")
            val second = cache.getCacheFile("Book: One", "Chapter/One", "Hello world.", "Aoede")
            val otherSpeaker = cache.getCacheFile("Book: One", "Chapter/One", "Hello world.", "Kore")

            assertEquals(first.absolutePath, second.absolutePath)
            assertFalse(first.absolutePath == otherSpeaker.absolutePath)
            assertTrue(first.parentFile.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `cache summary filters current speaker`() {
        val root = Files.createTempDirectory("reader-tts-cache").toFile()
        try {
            val cache = ReaderTtsFileCacheManager(root)
            cache.saveTotalChunks("Book", "One", 3)
            cache.getCacheFile("Book", "One", "Hello.", "Aoede").writeBytes(ByteArray(144))
            cache.getCacheFile("Book", "One", "World.", "Kore").writeBytes(ByteArray(244))

            val summary = cache.getCacheSummary("Book", "Aoede")

            assertEquals(2, summary.cachedChunkCount)
            assertEquals(1, summary.currentVoiceChunkCount)
            assertEquals(388, summary.totalSizeBytes)
            assertEquals(144, summary.currentVoiceSizeBytes)
        } finally {
            root.deleteRecursively()
        }
    }
}
