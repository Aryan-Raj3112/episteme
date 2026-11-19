package com.aryan.reader.pdf.data

import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.room.RoomDatabase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * `pdf_text_cache_db` is a rebuildable cache, and SQLite reports corruption by closing it: Room's
 * `onCorruption` closes the database (and deletes the files), after which every later call on that
 * instance throws `IllegalStateException: attempt to re-open an already-closed object` — the crash
 * this suite guards (docs/crashlytics-triage.md#137).
 *
 * Two properties keep the reader alive: the singleton is rebuilt once it has been closed, and the
 * repository tolerates a cache failure instead of propagating it.
 */
@RunWith(RobolectricTestRunner::class)
class PdfTextRepositoryCorruptionTest {

    @Before
    fun setUp() {
        PdfTextDatabase.invalidateInstance()
    }

    @After
    fun tearDown() {
        PdfTextDatabase.invalidateInstance()
    }

    @Test
    fun `a closed cache database is rebuilt on the next access`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val first = PdfTextDatabase.getDatabase(context)
        // Opening is lazy; this query is what marks the instance as opened.
        assertNull(first.pdfMetaDao().getMetadata("book"))
        assertTrue(first.isOpen)

        first.close()
        assertTrue("closing must make the instance unusable", !first.isOpen)

        val rebuilt = PdfTextDatabase.getDatabase(context)
        assertNotSame(first, rebuilt)
        // The rebuilt instance is usable, not a closed handle.
        assertNull(rebuilt.pdfMetaDao().getMetadata("book"))
    }

    @Test
    fun `a never-used instance is reused instead of rebuilt`() {
        val context = RuntimeEnvironment.getApplication()
        val first = PdfTextDatabase.getDatabase(context)
        // Lazy open means isOpen is false; that must not be read as "closed".
        assertEquals(first, PdfTextDatabase.getDatabase(context))
    }

    @Test
    fun `cache writes and reads survive a corrupt cache database`() = runTest {
        val database = mockk<PdfTextDatabase>(relaxed = true)
        val metaDao = mockk<PdfMetaDao>()
        val corrupt = SQLiteDatabaseCorruptException("database disk image is malformed")
        every { database.pdfMetaDao() } returns metaDao
        coEvery { metaDao.getMetadata(any()) } throws corrupt
        coEvery { metaDao.insertMetadata(any()) } throws corrupt
        coEvery { metaDao.updateLanguage(any(), any()) } throws corrupt
        mockkObject(PdfTextDatabase.Companion)
        every { PdfTextDatabase.getDatabase(any()) } returns database
        every { PdfTextDatabase.invalidateInstance() } just runs

        try {
            val repository = PdfTextRepository(RuntimeEnvironment.getApplication())

            repository.setBookLanguage("book", "LATIN")
            repository.savePageRatios("book", listOf(1f, 2f))
            assertNull(repository.getPageRatios("book"))
            assertNull(repository.getBookLanguage("book"))
        } finally {
            unmockkObject(PdfTextDatabase.Companion)
        }
    }

    @Test
    fun `non-cache failures still propagate`() = runTest {
        val database = mockk<PdfTextDatabase>(relaxed = true)
        val metaDao = mockk<PdfMetaDao>()
        every { database.pdfMetaDao() } returns metaDao
        coEvery { metaDao.getMetadata(any()) } throws IllegalArgumentException("bug, not a cache failure")
        mockkObject(PdfTextDatabase.Companion)
        every { PdfTextDatabase.getDatabase(any()) } returns database

        try {
            val repository = PdfTextRepository(RuntimeEnvironment.getApplication())
            try {
                repository.setBookLanguage("book", "LATIN")
                throw AssertionError("expected the failure to propagate")
            } catch (expected: IllegalArgumentException) {
                assertEquals("bug, not a cache failure", expected.message)
            }
        } finally {
            unmockkObject(PdfTextDatabase.Companion)
        }
    }
}
