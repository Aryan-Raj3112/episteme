package com.aryan.reader.epubreader

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aryan.reader.FileHasher
import com.aryan.reader.MainActivity
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.pdf.copyAssetToShareableCache
import com.aryan.reader.pdf.shareableCacheUri
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A highlight created in the WebView carries only its text — no offsets, no block — and has to be
 * anchored before any other reading mode can place it.
 *
 * The captured logs show `indexed=false` for a chapter whose highlight was made in the WebView, and
 * with no chapter index there is nothing to resolve the quote against, so the highlight silently fails
 * to paint in pagination. This reproduces that exact shape — a locator with no offsets at all — to find
 * out whether the index is ever built for such a chapter.
 */
@RunWith(AndroidJUnit4::class)
class EpubQuoteOnlyHighlightPlacementTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val fixtureAssetName = "epub/reader_test_book.epub"
    private val sanitizedTitle = "ReaderAndroidUITestBook"
    private val targetContext: Context = ApplicationProvider.getApplicationContext()
    private val instrumentationContext: Context = InstrumentationRegistry.getInstrumentation().context
    private var scenario: ActivityScenario<MainActivity>? = null
    private var currentEpubFile: File? = null
    private var bookId: String = ""

    private val quote = "Chapter one filler paragraph 01 keeps the document tall enough for scroll and pagination tests"

    @Before
    fun setup() {
        listOf("epub_reader_settings", "epub_reader_bookmarks", "reader_prefs").forEach { name ->
            targetContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        targetContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
            .edit().putString("render_mode", "PAGINATED").commit()
        bookId = requireNotNull(
            runBlocking { FileHasher.calculateSha256 { instrumentationContext.assets.open(fixtureAssetName) } }
        )
        runBlocking {
            AppDatabase.getDatabase(targetContext).recentFileDao().deleteFilePermanently(listOf(bookId))
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        currentEpubFile?.takeIf { it.exists() }?.delete()
    }

    /** Shaped exactly like a highlight the WebView bridge reports: a CFI, the text, and no position. */
    private fun webViewCreated() = UserHighlight(
        id = "quote_only",
        cfi = "0:2:1:5:3",
        text = quote,
        color = HighlightColor.GREEN,
        chapterIndex = 0,
        colorArgb = 0xFF388E3C.toInt(),
        locator = ReaderLocator(chapterIndex = 0, textQuote = quote, cfi = "0:2:1:5:3")
    )

    @Test
    fun aQuoteOnlyHighlightIsAnchoredAndPlacedInPagination() {
        targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
            .edit()
            .putString(
                "highlights_data_$sanitizedTitle",
                EpubAnnotationSerializer.highlightsToJson(listOf(webViewCreated()))
            )
            .commit()

        scenario = ActivityScenario.launch<MainActivity>(createEpubViewIntent())
        composeTestRule.waitUntil(timeoutMillis = 60_000) {
            composeTestRule.onAllNodesWithTag("ReaderContainer").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitUntil(timeoutMillis = 90_000) { persisted().isNotEmpty() }
        Thread.sleep(8_000)

        val after = persisted().first()
        println(
            "QUOTE_ONLY_RESULT block=${after.locator.blockIndex} " +
                "offsets=${after.locator.startOffset}..${after.locator.endOffset} " +
                "charOffset=${after.locator.charOffset} cfi=${after.locator.cfi}"
        )
        // The captured logs said indexed=false, which is a placement failure, not a slow one.
        assert(after.locator.blockIndex != null) {
            "quote-only highlight was never anchored; pagination has no index to place it with"
        }
    }

    private fun persisted(): List<UserHighlight> = runBlocking {
        AppDatabase.getDatabase(targetContext).recentFileDao().getFileByBookId(bookId)?.highlights
    }?.let { json -> EpubAnnotationSerializer.parseHighlightsJson(json) } ?: emptyList()

    private fun createEpubViewIntent(): Intent =
        Intent(targetContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(copyAssetToCache(), "application/epub+zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun copyAssetToCache() = shareableCacheUri(
        targetContext,
        currentEpubFile ?: copyAssetToShareableCache(instrumentationContext, targetContext, fixtureAssetName)
            .also { currentEpubFile = it }
    )
}