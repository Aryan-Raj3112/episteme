@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosComicInfoPresentationTest {
    @Test
    fun readsComicInfoMetadataAndKeepsTheFirstPageAsCover() {
        val directory = "${NSTemporaryDirectory().trimEnd('/')}/reader-comicinfo-${Random.nextInt()}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        val archivePath = "$directory/batman-05.cbz"
        val coverBytes = byteArrayOf(1, 2, 3, 4)
        try {
            val entries = linkedMapOf(
                "ComicInfo.xml" to """
                    <ComicInfo>
                      <Title>Court of Owls</Title>
                      <Series>Batman</Series>
                      <Number>5</Number>
                      <Summary>Owls everywhere.</Summary>
                      <Writer>Scott Snyder</Writer>
                    </ComicInfo>
                """.trimIndent().encodeToByteArray(),
                "001.png" to coverBytes,
            )
            writeIosZipArchive(archivePath, entries.keys.toList(), entries::get)

            val presentation = extractIosBookPresentation(
                BookItem(
                    id = "comicinfo-cbz",
                    path = archivePath,
                    type = FileType.CBZ,
                    displayName = "batman-05.cbz",
                    timestamp = 1L,
                ),
            )

            assertEquals("Court of Owls", presentation.title)
            assertEquals("Scott Snyder", presentation.author)
            assertEquals("Batman", presentation.seriesName)
            assertEquals(5.0, presentation.seriesIndex)
            assertEquals("Owls everywhere.", presentation.description)
            assertTrue(presentation.coverBytes?.isNotEmpty() == true)
            assertContentEquals(coverBytes, presentation.coverBytes)
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(archivePath, error = null)
            NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
        }
    }
}
