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
 * A highlight made in the paginated reader has to end up pointing at its own words.
 *
 * Making one wrote its position as `startCharOffsetInSource + localOffset`. That base is
 * element-relative — the parser restarts its counter for every element — so the stored "absolute"
 * offset was neither element-relative nor chapter-absolute. In the field it produced a 71-character
 * selection stored as a 315-character span, and because the span was still well formed, placement
 * accepted a range over every block it touched and painted most of the page.
 *
 * The span is the invariant worth asserting. A correct highlight's offsets cover exactly its text, so
 * `end - start` has to agree with the quote; when it does not, the offsets are in some other space and
 * nothing downstream can tell. Only a real reader run produces them, so this has to be measured here
 * rather than in a unit test.
 */
@RunWith(AndroidJUnit4::class)
class EpubPaginationMadeHighlightAnchoringTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val fixtureAssetName = "epub/reader_test_book.epub"
    private val sanitizedTitle = "ReaderAndroidUITestBook"
    private val targetContext: Context = ApplicationProvider.getApplicationContext()
    private val instrumentationContext: Context = InstrumentationRegistry.getInstrumentation().context
    private var scenario: ActivityScenario<MainActivity>? = null
    private var currentEpubFile: File? = null
    private var bookId: String = ""

    private val quote =
        "Chapter one filler paragraph 01 keeps the document tall enough for scroll and pagination tests"

    @Before
    fun setup() {
        listOf("epub_reader_settings", "epub_reader_bookmarks", "reader_prefs").forEach { name ->
            targetContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        targetContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE).edit()
            .putString("render_mode", "PAGINATED").commit()
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

    /**
     * The stored shape a paginated-made highlight actually had: a small start added to an
     * element-relative base, and an end that ran on into unrelated blocks.
     */
    private fun madeInPagination() = UserHighlight(
        id = "made_in_pagination",
        cfi = "/4/2/6/4/2/8:300",
        text = quote,
        color = HighlightColor.BLUE,
        chapterIndex = 0,
        colorArgb = 0xFF1976D2.toInt(),
        locator = ReaderLocator(
            chapterIndex = 0,
            startOffset = 3,
            endOffset = 300,
            textQuote = quote,
            cfi = "/4/2/6/4/2/8:300"
        )
    )

    @Test
    fun aPaginationMadeHighlightIsRewrittenToCoverExactlyItsOwnText() {
        val seededSpan = 300 - 3
        targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE).edit()
            .putString(
                "highlights_data_$sanitizedTitle",
                EpubAnnotationSerializer.highlightsToJson(listOf(madeInPagination()))
            ).commit()

        scenario = ActivityScenario.launch<MainActivity>(createEpubViewIntent())
        composeTestRule.waitUntil(timeoutMillis = 60_000) {
            composeTestRule.onAllNodesWithTag("ReaderContainer").fetchSemanticsNodes().isNotEmpty()
        }

        // The chapter index waits for the paginator, which takes seconds to parse a chapter on first
        // entry, so this polls rather than assuming a fixed delay.
        val anchored = awaitAnchor()
        println(
            "PAGINATION_MADE_RESULT seededSpan=$seededSpan quoteChars=${quote.length} " +
                "offsets=${anchored?.locator?.startOffset}..${anchored?.locator?.endOffset} " +
                "block=${anchored?.locator?.blockIndex}"
        )

        check(anchored != null) { "the highlight was dropped from the book entirely" }
        val start = anchored.locator.startOffset
        val end = anchored.locator.endOffset
        check(start != null && end != null) {
            "still unanchored after repair: block=${anchored.locator.blockIndex} offsets=$start..$end"
        }
        check(anchored.locator.blockIndex != null) { "anchored to an offset range but to no block" }

        val span = end - start
        // A highlight's offsets cover exactly its text. A span far larger than the quote is the
        // signature of offsets in the wrong space, which is what made placement paint whole blocks.
        check(kotlin.math.abs(span - quote.length) <= 2) {
            "offsets span $span characters for a ${quote.length}-character quote, so they are not " +
                "describing this highlight"
        }
    }

    /** Polls the persisted highlight until it has been rewritten, or gives up. */
    private fun awaitAnchor(): UserHighlight? {
        val deadline = System.currentTimeMillis() + 90_000
        var latest: UserHighlight? = null
        while (System.currentTimeMillis() < deadline) {
            latest = persisted().firstOrNull { it.id == "made_in_pagination" }
            if (latest?.locator?.blockIndex != null) return latest
            Thread.sleep(500)
        }
        return latest
    }

    /** The database is authoritative; an open reader clears the preferences copy. */
    private fun persisted(): List<UserHighlight> = runBlocking {
        AppDatabase.getDatabase(targetContext).recentFileDao().getFileByBookId(bookId)?.highlights
    }?.let { EpubAnnotationSerializer.parseHighlightsJson(it) } ?: emptyList()

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