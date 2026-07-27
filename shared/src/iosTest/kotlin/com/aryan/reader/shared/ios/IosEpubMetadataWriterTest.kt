@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class IosEpubMetadataWriterTest {
    @Test
    fun rewritesMetadataAndCoverIntoReadableEpub() {
        val directory = "${NSTemporaryDirectory().trimEnd('/')}/reader-epub-${Random.nextInt()}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        val source = "$directory/source.epub"
        val output = "$directory/output.epub"
        val cover = "$directory/new-cover.jpg"
        val coverBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        try {
            val entries = linkedMapOf(
                "mimetype" to "application/epub+zip".encodeToByteArray(),
                "META-INF/container.xml" to """
                    <container><rootfiles>
                      <rootfile full-path="OEBPS/content.opf"/>
                    </rootfiles></container>
                """.trimIndent().encodeToByteArray(),
                "OEBPS/content.opf" to """
                    <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <metadata>
                        <dc:title>Old title</dc:title>
                        <dc:creator>Old author</dc:creator>
                        <meta name="cover" content="cover"/>
                      </metadata>
                      <manifest>
                        <item id="cover" href="Images/old.jpg" media-type="image/jpeg"/>
                        <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                    </package>
                """.trimIndent().encodeToByteArray(),
                "OEBPS/Images/old.jpg" to byteArrayOf(9, 8, 7),
                "OEBPS/chapter.xhtml" to "<p>Keep me</p>".encodeToByteArray(),
            )
            writeIosZipArchive(source, entries.keys.toList(), entries::get)
            writeBytes(cover, coverBytes)

            val result = rewriteIosEpubMetadata(
                sourcePath = source,
                destinationPath = output,
                title = "New & improved",
                author = "Ada",
                description = "Summary",
                seriesName = "Series",
                seriesIndex = 2.5,
                coverPath = cover,
            )

            assertEquals("New & improved", result.title)
            assertEquals("Ada", result.author)
            assertEquals("Summary", result.description)
            assertEquals("Series", result.seriesName)
            assertEquals(2.5, result.seriesIndex)
            assertContentEquals(coverBytes, result.coverBytes)

            val secondResult = rewriteIosEpubMetadata(
                sourcePath = output,
                destinationPath = "$directory/second.epub",
                title = "Final",
                author = null,
                description = null,
                seriesName = null,
                seriesIndex = null,
                coverPath = null,
                restoreCoverFromPath = source,
            )
            assertEquals("Final", secondResult.title)
            assertEquals(null, secondResult.author)
            assertEquals(null, secondResult.description)
            assertContentEquals(byteArrayOf(9, 8, 7), secondResult.coverBytes)
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
        }
    }
}

private fun writeBytes(path: String, bytes: ByteArray) {
    val file = fopen(path, "wb") ?: error("Could not create test file")
    try {
        val written = bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
        }
        assertEquals(bytes.size.toULong(), written)
    } finally {
        fclose(file)
    }
}
