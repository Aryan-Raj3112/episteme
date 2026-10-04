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
 * A highlight must look the same in the WebView as it does in pagination.
 *
 * The WebView painted `#RRGGBB`, which has no alpha, so a highlight was a solid slab there and a 40%
 * tint everywhere else. Same hue, different tone, which is why it read as a rendering fault rather
 * than as two surfaces disagreeing. The payload now carries the fill the other surfaces use.
 */
@RunWith(AndroidJUnit4::class)
class EpubWebViewHighlightToneInstrumentedTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val fixtureAssetName = "epub/reader_test_book.epub"
    private val sanitizedTitle = "ReaderAndroidUITestBook"
    private val targetContext: Context = ApplicationProvider.getApplicationContext()
    private val instrumentationContext: Context = InstrumentationRegistry.getInstrumentation().context
    private var scenario: ActivityScenario<MainActivity>? = null
    private var currentEpubFile: File? = null
    private var bookId: String = ""

    @Before
    fun setup() {
        listOf("epub_reader_settings", "epub_reader_bookmarks", "reader_prefs").forEach { name ->
            targetContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        targetContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
            .edit().putString("render_mode", "VERTICAL_SCROLL").commit()
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

    private fun highlight(id: String, text: String, start: Int, end: Int) = UserHighlight(
        id = id,
        cfi = "0:2:1:5:3",
        text = text,
        color = HighlightColor.YELLOW,
        chapterIndex = 0,
        // A stored colour, which is what took the opaque branch. A palette-only highlight already
        // matched, so a regression here would only show with one of these.
        colorArgb = 0xFF3366CC.toInt(),
        locator = ReaderLocator(
            chapterIndex = 0,
            startOffset = start,
            endOffset = end,
            textQuote = text,
            cfi = "0:2:1:5:3"
        )
    )

    @Test
    fun webViewPayloadCarriesTheTintedFillNotTheOpaqueColour() {
        val highlights = listOf(
            highlight("stored", "Chapter One: Stable Opening", 0, 28),
            highlight("palette", "POSITION_TARGET_ALPHA", 0, 21)
                .copy(colorArgb = null)
        )
        val json = highlightsJsonForWebView(highlights)

        // The stored colour keeps its own hue but is filled at the legacy alpha, so it matches the
        // palette highlight's tone. Both are rgba, never #RRGGBB.
        val storedFill = Regex("\"fillCss\":\"([^\"]+)\"").findAll(json).first().groupValues[1]
        val paletteFill = Regex("\"fillCss\":\"([^\"]+)\"").findAll(json).last().groupValues[1]

        println("TONE stored=$storedFill palette=$paletteFill")
        assert(!json.contains("\"fillCss\":\"#")) { "fill colour must not be opaque hex" }
        assert(storedFill.endsWith(",0.4)")) { "stored colour should be tinted, was $storedFill" }
        assert(storedFill.substringBeforeLast(',') == "rgba(51,102,204")
        assert(paletteFill.endsWith(",0.4)")) { "palette fill should be tinted, was $paletteFill" }
    }

    @Test
    fun aHighlightSurvivesOpeningTheBookInWebViewMode() {
        val highlights = listOf(highlight("stored", "Chapter One: Stable Opening", 0, 28))
        targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("highlights_data_$sanitizedTitle", EpubAnnotationSerializer.highlightsToJson(highlights))
            .commit()

        scenario = ActivityScenario.launch<MainActivity>(createEpubViewIntent())
        composeTestRule.waitUntil(timeoutMillis = 60_000) {
            composeTestRule.onAllNodesWithTag("ReaderContainer").fetchSemanticsNodes().isNotEmpty()
        }
        Thread.sleep(6_000)

        val persisted = runBlocking {
            AppDatabase.getDatabase(targetContext).recentFileDao().getFileByBookId(bookId)?.highlights
        }?.let { EpubAnnotationSerializer.parseHighlightsJson(it) }.orEmpty()

        println("WEBVIEW_PERSISTED=" + persisted.joinToString { "${it.id}@block${it.locator.blockIndex}" })
        assert(persisted.any { it.id == "stored" }) { "highlight was lost opening the book" }
    }

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