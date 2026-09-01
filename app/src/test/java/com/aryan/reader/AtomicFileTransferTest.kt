package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AtomicFileTransferTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writerFailureLeavesThePreviousFinalAndCleansTheStage() {
        val directory = temporaryFolder.newFolder("books")
        val destination = File(directory, "book.epub").apply { writeText("previous") }
        val staged = File(directory, ".book.stage")

        assertThrows(IllegalStateException::class.java) {
            writeAndReplaceAppOwnedFileAtomically(staged, destination) { output ->
                output.write("partial".toByteArray())
                throw IllegalStateException("simulated writer failure")
            }
        }

        assertEquals("previous", destination.readText())
        assertFalse(staged.exists())
    }

    @Test
    fun commitFailureLeavesThePreviousFinalAndCleansTheStage() {
        val directory = temporaryFolder.newFolder("books")
        val destination = File(directory, "book.epub").apply { writeText("previous") }
        val staged = File(directory, ".book.stage")

        assertThrows(IOException::class.java) {
            replaceAppOwnedFileAtomically(staged, destination)
        }

        assertEquals("previous", destination.readText())
        assertFalse(staged.exists())
    }

    @Test
    fun successfulCommitReplacesTheFinalOnlyAfterTheStageIsWritten() {
        val directory = temporaryFolder.newFolder("books")
        val destination = File(directory, "book.epub").apply { writeText("previous") }
        val staged = File(directory, ".book.stage")

        writeAndReplaceAppOwnedFileAtomically(staged, destination) { output ->
            output.write("new".toByteArray())
        }

        assertEquals("new", destination.readText())
        assertTrue(destination.isFile)
        assertFalse(staged.exists())
    }
}
