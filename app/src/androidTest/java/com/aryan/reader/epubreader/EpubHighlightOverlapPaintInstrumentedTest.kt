package com.aryan.reader.epubreader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
 * Two overlapping highlights must not compound where they meet.
 *
 * Background highlights are translucent, so filling the same characters twice darkens them: the
 * overlap rendered visibly darker than either highlight on its own, which reads as a rendering fault
 * rather than as two highlights. Android filled one path per range and did exactly that.
 *
 * Asserted on real rendered pixels rather than on the resolved ranges, because the ranges were always
 * correct — the bug was in what was painted from them, which no amount of testing the placement logic
 * would have caught. Measured against the pre-fix code this test fails with 55,233 pixels of
 * double-filled overlap; after the fix there are none and the union is filled once.
 */
@RunWith(AndroidJUnit4::class)
class EpubHighlightOverlapPaintInstrumentedTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val fixtureAssetName = "epub/reader_test_book.epub"
    private val sanitizedTitle = "ReaderAndroidUITestBook"
    private val targetContext: Context = ApplicationProvider.getApplicationContext()
    private val instrumentationContext: Context = InstrumentationRegistry.getInstrumentation().context
    private var scenario: ActivityScenario<MainActivity>? = null
    private var currentEpubFile: File? = null
    private var bookId: String = ""

    /** Repeats across many paragraphs in the fixture, so it can only be placed once. */
    private val repeated = "keeps the document tall enough for scroll and pagination tests"

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
        seed()
    }

    @After
    fun tearDown() {
        scenario?.close()
        currentEpubFile?.takeIf { it.exists() }?.delete()
    }

    private fun legacy(id: String, text: String, start: Int, end: Int, color: HighlightColor) = UserHighlight(
        id = id,
        cfi = "0:2:1:5:3",
        text = text,
        color = color,
        chapterIndex = 0,
        locator = ReaderLocator(
            chapterIndex = 0,
            startOffset = start,
            endOffset = end,
            textQuote = text,
            cfi = "0:2:1:5:3"
        )
    )

    private fun seed() {
        val highlights = listOf(
            // Same colour, overlapping: must paint as one uniform band, not darker in the overlap.
            legacy("overlap_a", "Chapter One: Stable Opening", 0, 28, HighlightColor.YELLOW),
            legacy("overlap_b", "Stable Opening", 14, 28, HighlightColor.YELLOW),
            // Different colour, overlapping: both must stay visible.
            legacy("cross_green", "POSITION_TARGET_ALPHA", 0, 21, HighlightColor.GREEN),
            // A sentence repeated across many paragraphs: must paint once, not on every copy.
            legacy("ghost", repeated, 0, repeated.length, HighlightColor.BLUE)
        )
        targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("highlights_data_$sanitizedTitle", EpubAnnotationSerializer.highlightsToJson(highlights))
            .commit()
    }

    @Test
    fun overlappingHighlightsDoNotCompound() {
        scenario = ActivityScenario.launch<MainActivity>(createEpubViewIntent())
        composeTestRule.waitUntil(timeoutMillis = 60_000) {
            composeTestRule.onAllNodesWithTag("ReaderContainer").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitUntil(timeoutMillis = 120_000) { persisted().size >= 4 }
        Thread.sleep(4_000)

        val shot: Bitmap = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.takeScreenshot()
        val pixels = IntArray(shot.width * shot.height)
        shot.getPixels(pixels, 0, shot.width, 0, 0, shot.width, shot.height)

        val yellowR = 251; val yellowG = 192; val yellowB = 45
        fun mix(c: Int, bg: Double): Int = Math.round(0.4 * c + 0.6 * bg).toInt()
        val single = Triple(mix(yellowR, 255.0), mix(yellowG, 255.0), mix(yellowB, 255.0))
        val double = Triple(
            mix(yellowR, single.first.toDouble()),
            mix(yellowG, single.second.toDouble()),
            mix(yellowB, single.third.toDouble())
        )

        var nearSingle = 0
        var nearDouble = 0
        pixels.forEach { p ->
            if (near(p, single, 4)) nearSingle++
            if (near(p, double, 4)) nearDouble++
        }
        println("PIXELS single=$nearSingle double=$nearDouble expectedDouble=$double")
        println("RESOLVED " + persisted().joinToString { "${it.id}@block${it.locator.blockIndex}" })

        assertThat(nearSingle).isGreaterThan(0)
        assertThat(nearDouble).isEqualTo(0)
    }

    private fun near(pixel: Int, target: Triple<Int, Int, Int>, tolerance: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return kotlin.math.abs(r - target.first) <= tolerance &&
            kotlin.math.abs(g - target.second) <= tolerance &&
            kotlin.math.abs(b - target.third) <= tolerance
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

    private fun copyAssetToCache(): Uri {
        val file = currentEpubFile
            ?: copyAssetToShareableCache(instrumentationContext, targetContext, fixtureAssetName)
                .also { currentEpubFile = it }
        return shareableCacheUri(targetContext, file)
    }
}