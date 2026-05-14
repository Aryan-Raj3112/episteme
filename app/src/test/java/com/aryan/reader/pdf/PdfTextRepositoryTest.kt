package com.aryan.reader.pdf

import android.content.Context
import com.aryan.reader.SearchResult
import com.aryan.reader.pdf.data.PdfMetaDao
import com.aryan.reader.pdf.data.PdfMetadata
import com.aryan.reader.pdf.data.PdfSearchIndex
import com.aryan.reader.pdf.data.PdfSearchMatch
import com.aryan.reader.pdf.data.PdfTextDao
import com.aryan.reader.pdf.data.PdfTextDatabase
import com.aryan.reader.pdf.data.PdfTextRepository
import com.aryan.reader.pdf.data.SmartSearchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PdfTextRepositoryTest {

    private lateinit var dao: PdfTextDao
    private lateinit var metaDao: PdfMetaDao
    private lateinit var repository: PdfTextRepository

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        metaDao = mockk(relaxed = true)
        val db = mockk<PdfTextDatabase>()
        every { db.pdfTextDao() } returns dao
        every { db.pdfMetaDao() } returns metaDao
        mockkObject(PdfTextDatabase.Companion)
        every { PdfTextDatabase.getDatabase(any()) } returns db
        repository = PdfTextRepository(mockk<Context>(relaxed = true))
    }

    @After
    fun tearDown() {
        unmockkObject(PdfTextDatabase.Companion)
    }

    @Test
    fun `page ratios load parse failures and save preserves existing OCR language`() = runTest {
        coEvery { metaDao.getMetadata("missing") } returns null
        coEvery { metaDao.getMetadata("bad") } returns PdfMetadata("bad", 1, "not json", "LATIN")
        coEvery { metaDao.getMetadata("book") } returnsMany listOf(
            PdfMetadata("book", 2, "[1.25,0.75]", "DEVANAGARI"),
            PdfMetadata("book", 2, "[1.25,0.75]", "DEVANAGARI")
        )
        val inserted = slot<PdfMetadata>()
        coEvery { metaDao.insertMetadata(capture(inserted)) } returns Unit

        assertNull(repository.getPageRatios("missing"))
        assertNull(repository.getPageRatios("bad"))
        assertEquals(listOf(1.25f, 0.75f), repository.getPageRatios("book"))

        repository.savePageRatios("book", listOf(2f, 3.5f))

        assertEquals("book", inserted.captured.bookId)
        assertEquals(2, inserted.captured.totalPages)
        assertEquals("[2,3.5]", inserted.captured.ratiosJson)
        assertEquals("DEVANAGARI", inserted.captured.ocrLanguage)
    }

    @Test
    fun `searchBookFlow sanitizes FTS query and filters exact phrase punctuation`() = runTest {
        every { dao.searchBookFlow("book", "content:hello* content:world*") } returns flowOf(
            listOf(
                PdfSearchMatch(0, "", "hello, world appears here"),
                PdfSearchMatch(1, "", "hello world without comma")
            )
        )

        val results = repository.searchBookFlow("book", "hello, world").first()

        assertEquals(listOf(0), results.map { it.pageIndex })
    }

    @Test
    fun `smart search emits exact results with occurrence indexes and highlighted snippets`() = runTest {
        coEvery { dao.countMatches("book", "content:needle*") } returns 2
        coEvery { dao.getAllMatches("book", "content:needle*") } returns listOf(
            PdfSearchMatch(4, "", "needle one and needle two")
        )

        val result = repository.searchBookSmart("book", "needle").first()

        assertTrue(result is SmartSearchResult.Exact)
        val matches = (result as SmartSearchResult.Exact).matches
        assertEquals(2, matches.size)
        assertEquals(4, matches[0].locationInSource)
        assertEquals("Page 5", matches[0].locationTitle)
        assertEquals(0, matches[0].occurrenceIndexInLocation)
        assertEquals(1, matches[1].occurrenceIndexInLocation)
        assertEquals("needle", matches[0].query)
    }

    @Test
    fun `indexReaderPage replaces existing page text before inserting new text`() = runTest {
        val document = mockk<ReaderDocument>()
        val page = mockk<ReaderPage>(relaxed = true)
        val textPage = mockk<ReaderTextPage>(relaxed = true)

        coEvery { document.openPage(0) } returns page
        coEvery { page.openTextPage() } returns textPage
        coEvery { textPage.textPageCountChars() } returns 11
        coEvery { textPage.textPageGetText(0, 11) } returns "hello world"
        val insertedPageText = slot<PdfSearchIndex>()

        repository.indexReaderPage("book", document, 0)

        coVerifyOrder {
            dao.deletePageText("book", 0)
            dao.insertPageText(capture(insertedPageText))
        }
        assertEquals("book", insertedPageText.captured.bookId)
        assertEquals(0, insertedPageText.captured.pageIndex)
        assertEquals("hello world", insertedPageText.captured.content)
    }

    @Test
    fun `smart search emits paged result when page match count is large`() = runTest {
        coEvery { dao.countMatches("book", "content:common*") } returns 51
        every { dao.searchBookPagingSource("book", "content:common*") } returns mockk(relaxed = true)

        val result = repository.searchBookSmart("book", "common").first()

        assertTrue(result is SmartSearchResult.Paged)
        assertEquals(51, (result as SmartSearchResult.Paged).totalPageCount)
    }

    @Test
    fun `next and previous search result navigate within current page before querying adjacent pages`() = runTest {
        val current = SearchResult(
            locationInSource = 0,
            locationTitle = "Page 1",
            snippet = androidx.compose.ui.text.AnnotatedString("first"),
            query = "needle",
            occurrenceIndexInLocation = 0,
            chunkIndex = 0
        )
        coEvery { dao.getPageText("book", 0) } returns "needle then needle again"

        val next = repository.getNextResult("book", "needle", current)
        val prev = repository.getPrevResult("book", "needle", current.copy(occurrenceIndexInLocation = 1))

        assertEquals(1, next?.occurrenceIndexInLocation)
        assertEquals(0, prev?.occurrenceIndexInLocation)
        coVerify(exactly = 0) { dao.getNextPageWithMatch(any(), any(), any()) }
        coVerify(exactly = 0) { dao.getPrevPageWithMatch(any(), any(), any()) }
    }
}
