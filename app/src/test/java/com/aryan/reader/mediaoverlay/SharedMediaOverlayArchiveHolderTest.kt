package com.aryan.reader.mediaoverlay

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Which archive the media player is pointed at.
 *
 * Separated from the archive reader's own tests because the thing worth proving here is the *swap*:
 * the player is built once, at session creation, so the book it streams from is a field that changes
 * underneath it. A failure to swap is silent and audibly wrong — narration from the wrong book — and
 * a failure to swap on *error* is worse, because it would take down audio that was already playing.
 */
class SharedMediaOverlayArchiveHolderTest {

    private lateinit var first: File
    private lateinit var second: File

    @Before
    fun setUp() {
        first = book("a", 64)
        second = book("b", 128)
    }

    @After
    fun tearDown() {
        first.delete()
        second.delete()
    }

    private fun book(name: String, size: Int): File = File.createTempFile("mo-$name", ".epub").also { file ->
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("OEBPS/Audio/$name.mp3"))
            zip.write(ByteArray(size))
            zip.closeEntry()
        }
    }

    @Test
    fun `nothing is attached before the first book`() {
        val holder = SharedMediaOverlayArchiveHolder()
        assertNull(holder.currentOrNull())
        assertNull(holder.attachedBook)
        holder.close()
    }

    @Test
    fun `attaching reports success and remembers the book`() {
        val holder = SharedMediaOverlayArchiveHolder()
        assertNull(holder.attach(first))
        assertNotNull(holder.currentOrNull())
        assertEquals(first, holder.attachedBook)
        holder.close()
    }

    /**
     * A book that cannot be opened must not take the working one down with it.
     *
     * Attaching replaces the archive, so if a failure also cleared it, a reader who tried a second
     * unreadable book would lose narration that was already playing — the failure would be worse than
     * the mistake that caused it.
     */
    @Test
    fun `a failed attach leaves the previous archive in place`() {
        val holder = SharedMediaOverlayArchiveHolder()
        holder.attach(first)
        val working = holder.currentOrNull()

        val error = holder.attach(File("/definitely/not/a/book.epub"))

        assertNotNull(error)
        assertEquals(working, holder.currentOrNull())
        assertEquals(first, holder.attachedBook)
        holder.close()
    }

    /**
     * The swap itself.
     *
     * A factory still holding the previous book's archive would make the next clip open the wrong
     * book — silent, and sounding like narration from the wrong place entirely, which is the worst
     * way for this feature to fail.
     */
    @Test
    fun `attaching a second book swaps the archive`() {
        val holder = SharedMediaOverlayArchiveHolder()
        holder.attach(first)
        val firstReader = holder.currentOrNull()

        assertNull(holder.attach(second))

        assertTrue(firstReader !== holder.currentOrNull())
        assertEquals(second, holder.attachedBook)
        holder.close()
    }

    @Test
    fun `a directory is not a book`() {
        val holder = SharedMediaOverlayArchiveHolder()
        assertNotNull(holder.attach(File("/tmp")))
        assertNull(holder.currentOrNull())
        holder.close()
    }

    @Test
    fun `a file that is not an archive is refused`() {
        val notAZip = File.createTempFile("mo-plain", ".epub")
        notAZip.writeText("not a zip")
        try {
            val holder = SharedMediaOverlayArchiveHolder()
            assertNotNull(holder.attach(notAZip))
            assertNull(holder.currentOrNull())
            holder.close()
        } finally {
            notAZip.delete()
        }
    }

    @Test
    fun `closing releases the archive and forgets the book`() {
        val holder = SharedMediaOverlayArchiveHolder()
        holder.attach(first)
        holder.close()
        assertNull(holder.currentOrNull())
        assertNull(holder.attachedBook)
        // And closing twice, which is what a disposed-then-released reader does, is not an error.
        holder.close()
    }
}