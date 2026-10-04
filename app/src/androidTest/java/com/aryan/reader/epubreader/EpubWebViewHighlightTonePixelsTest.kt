package com.aryan.reader.epubreader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
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
 * A highlight painted in the WebView has to be a tint, not a slab.
 *
 * The tone reached the document in the payload and was still lost: the palette passed the colour to the
 * marker as `#RRGGBB`, and the marker applies whatever it is handed as an inline `background-color`
 * marked `!important`. That beat the stylesheet's tinted rule, so a highlight made from the palette
 * covered its text in solid colour while the same highlight in pagination was a soft tint. It read as
 * a rendering fault because the hue was right and only the tone differed.
 *
 * Asserted from the pixels rather than the payload, because the payload was already correct while the
 * screen was not — that gap is the whole bug. Green at 40% over a light page keeps the green channel
 * dominant but only slightly; an opaque fill keeps the full saturation. Counting how much of the
 * painted green is saturated separates the two without judging a colour by eye.
 */
@RunWith(AndroidJUnit4::class)
class EpubWebViewHighlightTonePixelsTest {

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
            .putString("render_mode", "VERTICAL_SCROLL").commit()
        // The native renderer would stand in for the surface under test.
        targetContext.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("use_native_vertical_renderer", false).commit()
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

    @Test
    fun webViewHighlightIsATintRatherThanAnOpaqueSlab() {
        targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE).edit()
            .putString(
                "highlights_data_$sanitizedTitle",
                EpubAnnotationSerializer.highlightsToJson(listOf(madeInTheWebView()))
            ).commit()

        scenario = ActivityScenario.launch<MainActivity>(createEpubViewIntent())
        composeTestRule.waitUntil(timeoutMillis = 60_000) {
            composeTestRule.onAllNodesWithTag("ReaderContainer").fetchSemanticsNodes().isNotEmpty()
        }
        // The document loads, runs the annotation script and paints asynchronously, so poll for the
        // highlight rather than sleeping a fixed interval and measuring whatever happened to be there.
        val paint = awaitHighlightPaint() ?: run {
            throw AssertionError("no green highlight was ever painted in the WebView, so the tone was never measured")
        }
        println(
            "PAINT_REPORT greenish=${paint.total} saturated=${paint.saturated} " +
                "mostSaturated=${paint.worst}"
        )

        // The highlight really is on screen, or the saturation figure below would be vacuously small.
        assert(paint.total > 2_000) {
            "no green highlight was painted at all, so the tone was never measured: ${paint.total} pixels"
        }
        // An opaque fill was ~90% saturated here; a 40% tint is ~2%, the rest being anti-aliased edges.
        assert(paint.saturated * 20 < paint.total) {
            "highlight painted mostly opaque (${paint.saturated}/${paint.total} saturated, " +
                "worst pixel ${paint.worst}) — expected a 40% tint"
        }
    }

    private data class GreenPaint(val total: Int, val saturated: Int, val worst: String)

    /**
     * Waits until a green highlight is actually on screen and returns how it was painted.
     *
     * Bounded rather than infinite: the point is to measure a real paint, and a reader that never paints
     * one is a failure worth reporting rather than waiting on.
     */
    private fun awaitHighlightPaint(): GreenPaint? {
        val deadline = System.currentTimeMillis() + 60_000
        var latest = measureGreenPaint()
        while (System.currentTimeMillis() < deadline) {
            latest = measureGreenPaint()
            if (latest.total > 2_000) return latest
            Thread.sleep(500)
        }
        return if (latest.total > 2_000) latest else null
    }

    /**
     * Counts green-dominant pixels and how many of them are opaque-looking.
     *
     * Sampled on a grid: a full-resolution pass over a 1080x2400 screen is slow enough to matter in a
     * test that runs on every build, and the fill covers thousands of pixels either way.
     */
    private fun measureGreenPaint(): GreenPaint {
        val root = decorRoot()
        val bitmap = Bitmap.createBitmap(
            root.width.coerceAtLeast(1),
            root.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        Canvas(bitmap).let { root.draw(it) }

        var total = 0
        var saturated = 0
        var worst = "none"
        var worstGap = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel == Color.WHITE || pixel == Color.BLACK) continue
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Green channel clearly dominant: the highlight, or the edge of it. A stray green UI
                // pixel is pure and far from the tint, so it is counted but cannot pass as a paint.
                if (g <= r + 12 || g <= b + 12) continue
                total++
                val gap = minOf(g - r, g - b)
                if (gap > 70) {
                    saturated++
                    if (gap > worstGap) {
                        worstGap = gap
                        worst = "r=$r,g=$g,b=$b"
                    }
                }
            }
        }
        return GreenPaint(total, saturated, worst)
    }

    private fun decorRoot(): View {
        var root: View? = null
        scenario?.onActivity { root = it.window.decorView }
        return requireNotNull(root)
    }

    /**
     * The shape a highlight has when it is made in the WebView: its text and its CFI, no offsets.
     *
     * That is the shape the document can place on its own, by finding the text, so it is the one that
     * reaches the marker this test is about.
     */
    private fun madeInTheWebView() = UserHighlight(
        id = "made_in_webview",
        cfi = "0:2:1:6:3",
        text = quote,
        color = HighlightColor.GREEN,
        chapterIndex = 0,
        colorArgb = 0xFF388E3C.toInt(),
        locator = ReaderLocator(chapterIndex = 0, textQuote = quote, cfi = "0:2:1:6:3")
    )

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