package com.aryan.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GoogleDriveRepositoryTransferTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun failedDriveWriterPreservesExistingFileAndCleansUniqueStage() {
        val directory = temporaryFolder.newFolder("drive")
        val destination = File(directory, "book.epub").apply { writeText("previous") }

        assertThrows(IllegalStateException::class.java) {
            writeGoogleDriveDownloadAtomically(
                destination = destination,
                transferId = "failed-transfer",
                write = { output ->
                    output.write("partial".toByteArray())
                    throw IllegalStateException("simulated Drive response failure")
                },
            )
        }

        assertEquals("previous", destination.readText())
        assertFalse(File(directory, ".book.epub.failed-transfer.stage").exists())
    }

    @Test
    fun successfulDriveDownloadReplacesExistingFileAfterStaging() {
        val directory = temporaryFolder.newFolder("drive")
        val destination = File(directory, "book.epub").apply { writeText("previous") }

        assertTrue(
            writeGoogleDriveDownloadAtomically(
                destination = destination,
                transferId = "successful-transfer",
                write = { output ->
                    output.write("downloaded".toByteArray())
                },
            ),
        )

        assertEquals("downloaded", destination.readText())
        assertFalse(File(directory, ".book.epub.successful-transfer.stage").exists())
    }
}
