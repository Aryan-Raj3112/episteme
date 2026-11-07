@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ui

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.dataWithLength
import platform.Foundation.writeToFile
import platform.posix.memcpy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs against the real iOS primitives (`NSFileManager` stat + Skia decode), which
 * is where the cover regression actually surfaced.
 */
class BookCoverImageCacheIosTest {

    @Test
    fun coverCacheReloadsWhenTheFileIsRewrittenUnderTheSamePath() = runTest {
        withTempCoverFile("cover.png") { path ->
            BookCoverImageCache.clearForTests()
            writeCover(path, width = 64, height = 64)

            val first = BookCoverImageCache.load(path)
            assertNotNull(first)
            assertEquals(64, first.width)

            // iOS persists covers to a stable `Covers/<bookId>.<ext>` path and
            // rewrites them in place, so the path never changes when the cover does.
            // A cache keyed on the path alone kept serving the pre-rewrite bitmap
            // until the app was restarted.
            writeCover(path, width = 96, height = 48)

            val second = BookCoverImageCache.load(path)
            assertNotNull(second)
            assertEquals(96, second.width)
            assertEquals(48, second.height)
        }
    }

    @Test
    fun largeCoversAreCachedAtThumbnailSize() = runTest {
        withTempCoverFile("large-cover.png") { path ->
            BookCoverImageCache.clearForTests()
            writeCover(path, width = 1200, height = 800)

            val bitmap = BookCoverImageCache.load(path)

            assertNotNull(bitmap)
            assertTrue(
                bitmap.width <= BookCoverMaxDimensionPx,
                "width ${bitmap.width} exceeded $BookCoverMaxDimensionPx",
            )
            assertTrue(
                bitmap.height <= BookCoverMaxDimensionPx,
                "height ${bitmap.height} exceeded $BookCoverMaxDimensionPx",
            )
        }
    }

    @Test
    fun peekServesAnAlreadyDecodedCover() = runTest {
        withTempCoverFile("peeked-cover.png") { path ->
            BookCoverImageCache.clearForTests()
            writeCover(path, width = 40, height = 40)

            val loaded = BookCoverImageCache.load(path)

            assertNotNull(loaded)
            assertEquals(loaded, BookCoverImageCache.peek(path))
        }
    }

    @Test
    fun aCoverThatLandsAfterTheFirstLookupIsStillPickedUp() = runTest {
        withTempCoverFile("late-cover.png") { path ->
            BookCoverImageCache.clearForTests()
            // Extraction writes the file after the grid has already asked for it.
            assertNull(BookCoverImageCache.load(path))
            assertNull(BookCoverImageCache.peek(path))

            writeCover(path, width = 32, height = 32)

            assertEquals(32, BookCoverImageCache.load(path)?.width)
        }
    }

    @Test
    fun invalidatingACachedCoverBumpsTheRevisionMountedCoversObserve() = runTest {
        withTempCoverFile("invalidated-cover.png") { path ->
            BookCoverImageCache.clearForTests()
            writeCover(path, width = 40, height = 40)
            BookCoverImageCache.load(path)
            val before = BookCoverImageCache.revision

            BookCoverImageCache.invalidate(path)

            assertNotEquals(before, BookCoverImageCache.revision)
            assertNull(BookCoverImageCache.peek(path))
        }
    }

    @Test
    fun invalidatingACoverThatWasNeverDisplayedLeavesTheRevisionAlone() = runTest {
        withTempCoverFile("never-displayed-cover.png") { path ->
            BookCoverImageCache.clearForTests()
            writeCover(path, width = 40, height = 40)
            val before = BookCoverImageCache.revision

            // Bulk extraction writes a cover per imported book. Bumping the revision
            // for each would make every mounted cover re-read once per imported book.
            BookCoverImageCache.invalidate(path)

            assertEquals(before, BookCoverImageCache.revision)
        }
    }

    private inline fun withTempCoverFile(name: String, block: (String) -> Unit) {
        val directory = NSTemporaryDirectory().trimEnd('/') + "/reader-cover-cache-tests"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        val path = "$directory/$name"
        try {
            block(path)
        } finally {
            BookCoverImageCache.clearForTests()
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }

    private fun writeCover(path: String, width: Int, height: Int) {
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.clear(0xFF2A5C88.toInt())
        val bytes = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: return
        bytes.toNSData().writeToFile(path, atomically = true)
    }

    private fun ByteArray.toNSData(): NSData {
        val data = NSMutableData.dataWithLength(size.toULong()) ?: return NSData()
        usePinned { pinned ->
            memcpy(data.mutableBytes, pinned.addressOf(0), size.toULong())
        }
        return data
    }
}