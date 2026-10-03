package com.aryan.reader.epubreader

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies that a highlight stored before the reader had a usable coordinate space is repaired and
 * written back while the book is open.
 *
 * The seed is deliberately shaped like real old data: offsets present, but computed in the space
 * where every block claimed to start at zero, so the stored range points at the top of the chapter
 * instead of at the selected text, and a WebView DOM position as its CFI, which no paginated page
 * can resolve. Asserting only that the offsets became non-null would pass for a repair that wrote
 * plausible numbers to the wrong place, which is the failure being fixed, so the block is checked too.
 */
@RunWith(AndroidJUnit4::class)
class EpubHighlightRepairInstrumentedTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val fixtureAssetName = "epub/reader_test_book.epub"
    private val sanitizedTitle = "ReaderAndroidUITestBook"
    private val targetContext: Context = ApplicationProvider.getApplicationContext()
    private val instrumentationContext: Context = InstrumentationRegistry.getInstrumentation().context
    private var scenario: ActivityScenario<MainActivity>? = null
    private var currentEpubFile: File? = null
    private var bookId: String = ""

    private val quote = "keeps the document tall enough for scroll and pagination tests"
    private val legacyId = "legacy_broken_offsets"

    @Before
    fun setup() {
        listOf("epub_reader_settings", "epub_reader_bookmarks", "reader_prefs").forEach { name ->
            targetContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        targetContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("render_mode", "PAGINATED")
            .commit()
        this.bookId = requireNotNull(
            runBlocking {
                FileHasher.calculateSha256 { instrumentationContext.assets.open(fixtureAssetName) }
            }
        )
        runBlocking {
            AppDatabase.getDatabase(targetContext).recentFileDao()
                .deleteFilePermanently(listOf(this@EpubHighlightRepairInstrumentedTest.bookId))
        }
        seedLegacyHighlight()
    }

    @After
    fun tearDown() {
        scenario?.close()
        currentEpubFile?.takeIf { it.exists() }?.delete()
    }

    @Test
    fun legacyHighlightIsRepairedAndWrittenBack() {
        val seeded = seededHighlight()
        assertThat(seeded).isNotNull()
        // Precondition, so the test cannot pass by doing nothing: the seed really is unplaceable.
        assertThat(seeded!!.locator.blockIndex).isNull()

        scenario = ActivityScenario.launch<MainActivity>(createEpubViewIntent())
        composeTestRule.waitUntil(timeoutMillis = 60_000) {
            composeTestRule.onAllNodesWithTag("ReaderContainer").fetchSemanticsNodes().isNotEmpty()
        }
        // Asserted against the database, not the shared preferences the seed went into: on open the
        // reader moves highlights into the database and clears the preference copy, so the preference
        // would read empty and the assertion could never pass.
        composeTestRule.waitUntil(timeoutMillis = 120_000) { persistedHighlight() != null }

        val repaired = requireNotNull(persistedHighlight())
        assertThat(repaired.id).isEqualTo(legacyId)
        assertThat(repaired.text).isEqualTo(quote)
        // Exactly the selected text, not a wider or narrower range.
        assertThat((repaired.locator.endOffset ?: 0) - (repaired.locator.startOffset ?: 0))
            .isEqualTo(quote.length)
        // The block holding the text, not the block the broken offsets happened to point at. The seed
        // pointed at offset 0, which is the chapter's first block, so this also proves the repair
        // relocated rather than merely filled in.
        assertThat(repaired.locator.blockIndex).isNotEqualTo(0)
        // A CFI a paginated page can resolve, replacing the WebView DOM path.
        assertThat(repaired.locator.cfi).startsWith("/4/")
    }

    private fun persistedHighlight(): UserHighlight? = runBlocking {
        AppDatabase.getDatabase(targetContext)
            .recentFileDao()
            .getFileByBookId(bookId)
            ?.highlights
    }?.let { EpubAnnotationSerializer.parseHighlightsJson(it) }
        ?.firstOrNull { it.id == legacyId }

    private fun seededHighlight(): UserHighlight? = EpubAnnotationSerializer
        .parseHighlightsJson(
            targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
                .getString("highlights_data_$sanitizedTitle", "[]")
        )
        .firstOrNull { it.id == legacyId }

    private fun seedLegacyHighlight() {
        val highlight = UserHighlight(
            id = legacyId,
            cfi = "0:2:1:5:3",
            text = quote,
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(
                chapterIndex = 0,
                startOffset = 0,
                endOffset = quote.length,
                textQuote = quote,
                cfi = "0:2:1:5:3"
            )
        )
        targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
            .edit()
            .putString(
                "highlights_data_$sanitizedTitle",
                EpubAnnotationSerializer.highlightsToJson(listOf(highlight))
            )
            .commit()
    }

    private fun createEpubViewIntent(): Intent =
        Intent(targetContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(copyAssetToCache(), "application/epub+zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun copyAssetToCache(): Uri {
        val file = copyAssetToShareableCache(instrumentationContext, targetContext, fixtureAssetName)
        currentEpubFile = file
        return shareableCacheUri(targetContext, file)
    }
}