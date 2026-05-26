package com.aryan.reader.epubreader

import android.content.Context
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubChapter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EpubReaderLogicTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var testBook: EpubBook

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "test_epub_search").apply {
            deleteRecursively()
            mkdirs()
        }

        val chapter1File = File(testDir, "chapter1.html").apply {
            writeText("<html><body><p>A simple Test case.</p></body></html>")
        }
        val chapter2File = File(testDir, "chapter2.html").apply {
            writeText("<html><body><p>Another test case here.</p><p>The word Test appears twice.</p></body></html>")
        }

        testBook = EpubBook(
            fileName = "test.epub",
            title = "Test Book",
            author = "Tester",
            language = "en",
            coverImage = null,
            extractionBasePath = testDir.absolutePath,
            chapters = listOf(
                EpubChapter(
                    chapterId = "ch1",
                    absPath = chapter1File.absolutePath,
                    title = "Chapter 1",
                    htmlFilePath = "chapter1.html",
                    plainTextContent = "",
                    htmlContent = ""
                ),
                EpubChapter(
                    chapterId = "ch2",
                    absPath = chapter2File.absolutePath,
                    title = "Chapter 2",
                    htmlFilePath = "chapter2.html",
                    plainTextContent = "",
                    htmlContent = ""
                )
            )
        )
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun search_findsCorrectResults() = runTest {
        val results = createEpubSearcher(testBook)("case")

        assertThat(results).hasSize(2)
        assertThat(results.count { it.locationTitle == "Chapter 1" }).isEqualTo(1)
        assertThat(results.count { it.locationTitle == "Chapter 2" }).isEqualTo(1)
    }

    @Test
    fun search_isCaseInsensitive() = runTest {
        val results = createEpubSearcher(testBook)("test")

        assertThat(results).hasSize(3)
        assertThat(results[0].locationTitle).isEqualTo("Chapter 1")
        assertThat(results[1].locationTitle).isEqualTo("Chapter 2")
        assertThat(results[2].locationTitle).isEqualTo("Chapter 2")
    }

    @Test
    fun search_noResultsFound() = runTest {
        val results = createEpubSearcher(testBook)("nonexistent")

        assertThat(results).isEmpty()
    }

    @Test
    fun search_createsCorrectSnippetHighlight() = runTest {
        val result = createEpubSearcher(testBook)("Test").first()
        val boldRanges = result.snippet.spanStyles.filter { it.item == SpanStyle(fontWeight = FontWeight.Bold) }

        assertThat(boldRanges).hasSize(1)
        val highlight = boldRanges.first()
        assertThat(result.snippet.text.substring(highlight.start, highlight.end)).isEqualTo("Test")
    }
}
