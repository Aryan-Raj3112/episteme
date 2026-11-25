package com.aryan.reader.epubreader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
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
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The note mark, measured in the document that draws it.
 *
 * The mark is an overlay the WebView positions itself, so "the payload carried `hasNote`" is not the
 * same claim as "there is a visible mark on screen". The first version of this shipped sized by a
 * plain inline style, which the reader's own `body svg { max-width: … !important; height: auto
 * !important }` rule beat: the element existed, sat at the right place, and was 0x0. Only reading its
 * laid-out geometry catches that.
 */
@RunWith(AndroidJUnit4::class)
class EpubNoteBadgeWebViewInstrumentedTest {

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
        // The WebView surface is the one under test; the native renderer would stand in for it.
        targetContext.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("use_native_vertical_renderer", false).commit()
        bookId = requireNotNull(
            runBlocking { FileHasher.calculateSha256 { instrumentationContext.assets.open(fixtureAssetName) } }
        )
        runBlocking {
            AppDatabase.getDatabase(targetContext).recentFileDao().deleteFilePermanently(listOf(bookId))
        }
        clearBookPrefs()
        seed(listOf(singleHighlight()))
    }

    @After
    fun tearDown() {
        scenario?.close()
        currentEpubFile?.takeIf { it.exists() }?.delete()
    }

    @Test
    fun noteMarkIsDrawnAtTheEndOfItsHighlight() {
        openReader()

        val report = awaitMark()
        println("NOTE_BADGE_REPORT $report")

        val badges = requireNotNull(report).optInt("badges")
        assert(badges == 1) { "expected one note mark, found $badges in $report" }

        val mark = requireNotNull(report.optJSONArray("items")).getJSONObject(0)
        val rect = mark.getJSONArray("rect")
        val spanRect = requireNotNull(report.optJSONArray("spans")).getJSONObject(0).getJSONArray("rect")

        // Sized, not merely present. A mark collapsed by page CSS is 0x0 and invisible.
        assert(rect.getInt(2) >= 4 && rect.getInt(3) >= 4) {
            "the mark has no size (${rect.getInt(2)}x${rect.getInt(3)}), so nothing is painted"
        }
        // In the highlight's own colour at full opacity, not the tinted fill.
        // One fixed gold for every mark, with a hairline outline: a mark taking the
        // highlight's colour is invisible on a yellow highlight.
        assert(mark.getString("fill") == "#FFB400") {
            "mark colour should be the fixed gold, was ${mark.getString("fill")}"
        }
        assert(mark.getString("stroke").isNotBlank()) { "the mark has no outline to separate it" }
        // Attached to the highlight: inside its box, with its trailing edge on the highlight's end.
        // It hangs a fraction of its own width past that end by design, far enough to clear the last
        // character's shoulder and no further than its own width.
        val spanLeft = spanRect.getInt(0)
        val spanRight = spanLeft + spanRect.getInt(2)
        val markLeft = rect.getInt(0)
        val markRight = markLeft + rect.getInt(2)
        assert(markLeft >= spanLeft && markRight >= spanRight - 1) {
            "mark at $markLeft..$markRight does not end at the highlight span $spanLeft..$spanRight"
        }
        assert(markRight <= spanRight + rect.getInt(2)) {
            "mark at $markLeft..$markRight overhangs the highlight end $spanRight by more than itself"
        }
        // Near the vertical end of the highlight rather than at the top of the passage.
        val spanTop = spanRect.getInt(1)
        val spanBottom = spanTop + spanRect.getInt(3)
        assert(rect.getInt(1) > spanTop) { "mark sits above its own highlight" }
        assert(rect.getInt(1) < spanBottom) { "mark sits below its own highlight" }
    }

    @Test
    fun aRepairedHighlightIsStillPaintedInTheWebView() {
        // The state a highlight is in after pagination has placed it: it keeps the WebView CFI it was
        // created with, and gains offsets plus a structural CFI for its paragraph that names the
        // character it starts at.
        //
        // The passage is deliberately one that starts *after* an inline `<span>`, so the paragraph's
        // text is split across nodes: "HIGHLIGHT_TARGET_BRAVO" is its own element and the selection
        // begins 22 characters into the paragraph, in the text node that follows it. A CFI naming
        // only the paragraph would leave the document to guess those characters, and guessing wrong
        // paints the paragraph's first words instead of the passage.
        val repairedQuote = "is a short highlight target."
        clearBookPrefs()
        seed(listOf(
            UserHighlight(
                id = "repaired_in_webview",
                cfi = "0:9:1:6:3",
                text = repairedQuote,
                color = HighlightColor.GREEN,
                chapterIndex = 0,
                colorArgb = 0xFF388E3C.toInt(),
                note = "a thought about this passage",
                locator = ReaderLocator(
                    chapterIndex = 0,
                    startOffset = 292,
                    endOffset = 320,
                    textQuote = repairedQuote,
                    cfi = "/4/2/8:22"
                )
            )
        ))
        openReader()

        val report = awaitMark()
        println("REPAIRED_REPORT $report")
        val spanText = requireNotNull(report?.optJSONArray("spans")).getJSONObject(0).optString("text").trim()
        assert(spanText == repairedQuote) {
            "the CFI placed the highlight over '$spanText' instead of '$repairedQuote'"
        }
        assert(report.optInt("badges") == 1) { "the repaired highlight lost its note mark: $report" }
    }

    @Test
    fun highlightMadeInTheWebViewIsPaintedInPagination() {
        targetContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE).edit()
            .putString("render_mode", "PAGINATED").commit()
        openReader()

        val painted = awaitPaintedContent()
        val paint = awaitGreenPaint()
        println(
            "PAGINATED_REPORT painted_pixels=$painted " +
                "green_pixels=${paint?.total} saturated=${paint?.saturated} persisted=${persistedSummary()}"
        )
        assert(painted != null) { "the paginated page never painted any content, so nothing could be measured" }
        assert(paint != null) { "the highlight made in the WebView is missing in pagination" }
    }

    private fun openReader() {
        scenario = ActivityScenario.launch<MainActivity>(createEpubViewIntent())
        awaitReaderContainer()
    }

    /**
     * Waits for the reader to compose.
     *
     * `waitUntil` throws when the activity has not published a compose hierarchy yet, which is a race
     * right after launch rather than a failure, so the probe is wrapped instead of retried by hand.
     */
    private fun awaitReaderContainer() {
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            val ready = runCatching {
                composeTestRule.onAllNodesWithTag("ReaderContainer").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
            if (ready) return
            Thread.sleep(250)
        }
        throw AssertionError("the reader never composed within 60s")
    }

    private fun awaitMark(): JSONObject? {
        val deadline = System.currentTimeMillis() + 60_000
        var latest: JSONObject? = null
        while (System.currentTimeMillis() < deadline) {
            val raw = evaluate(probeScript())
            val parsed = runCatching { JSONObject(raw.trim().removeSurrounding("\"", "\"").replace("\\\"", "\"")) }
                .getOrNull()
            if (parsed != null) {
                latest = parsed
                if (parsed.optInt("badges") == 1 && markIsPlaced(parsed)) return parsed
            }
            Thread.sleep(500)
        }
        return latest
    }

    /**
     * A mark that exists but has not been positioned yet is not the state under test: an svg with a
     * viewBox and no width lays out at the width of its container, so an unplaced mark is a giant
     * glyph over the page. Waiting for geometry is what keeps this from passing on that state.
     */
    private fun markIsPlaced(report: JSONObject): Boolean = runCatching {
        val mark = requireNotNull(report.optJSONArray("items")).getJSONObject(0)
        val rect = mark.getJSONArray("rect")
        rect.getInt(2) in 4..80 &&
            rect.getInt(3) in 4..80 &&
            mark.optString("transform").isNotBlank()
    }.getOrDefault(false)

    private fun probeScript(): String = """
        (function () {
          var badges = document.querySelectorAll('.reader-note-badge');
          var out = {
            badges: badges.length,
            markers: document.querySelectorAll('span[data-cfi]').length,
            highlightSpans: document.querySelectorAll('span[class*="user-highlight-"]').length,
            layer: !!document.getElementById('reader-note-badge-layer'),
            items: [],
            spans: []
          };
          for (var i = 0; i < badges.length; i++) {
            var b = badges[i];
            var r = b.getBoundingClientRect();
            var path = b.querySelector('path');
            out.items.push({
              display: b.style.display,
              transform: b.style.transform,
              fill: path ? path.getAttribute('fill') : null,
              stroke: path ? path.getAttribute('stroke') : null,
              paths: b.querySelectorAll('path').length,
              rect: [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)]
            });
          }
          var spans = document.querySelectorAll('span[class*="user-highlight-"]');
          for (var s = 0; s < spans.length && s < 3; s++) {
            var sr = spans[s].getBoundingClientRect();
            out.spans.push({
              cfi: spans[s].getAttribute('data-cfi'),
              text: (spans[s].textContent || '').slice(0, 40),
              rect: [Math.round(sr.left), Math.round(sr.top), Math.round(sr.width), Math.round(sr.height)]
            });
          }
          return JSON.stringify(out);
        })()
    """.trimIndent()

    private fun evaluate(script: String): String {
        var result = ""
        val latch = CountDownLatch(1)
        scenario?.onActivity { activity ->
            val webView = findWebView(activity.window.decorView)
            if (webView == null) {
                latch.countDown()
            } else {
                webView.evaluateJavascript(script) { value ->
                    result = value ?: ""
                    latch.countDown()
                }
            }
        }
        latch.await(20, TimeUnit.SECONDS)
        return result
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findWebView(view.getChildAt(index))
                if (found != null) return found
            }
        }
        return null
    }

    private data class GreenPaint(val total: Int, val saturated: Int)

    /**
     * Waits until the surface under test has painted content.
     *
     * Measured as non-background pixels: a paginated page that has not laid out yet is a blank sheet
     * with a spinner, and reading a highlight out of that state says nothing about whether it is
     * placed.
     */
    private fun awaitPaintedContent(): Int? {
        val deadline = System.currentTimeMillis() + 45_000
        while (System.currentTimeMillis() < deadline) {
            val painted = measurePaintedContent()
            if (painted > 2_000) return painted
            Thread.sleep(500)
        }
        return null
    }

    /**
     * Captures the window on the main thread.
     *
     * Compose observes layout and draw through a snapshot observer that refuses to be used from two
     * threads, so drawing the decor view from the instrumentation thread while the app is drawing is
     * a crash waiting to happen.
     */
    private fun captureScreen(): Bitmap? {
        var bitmap: Bitmap? = null
        scenario?.onActivity { activity ->
            val root = activity.window.decorView
            val shot = Bitmap.createBitmap(
                root.width.coerceAtLeast(1),
                root.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            Canvas(shot).let { root.draw(it) }
            bitmap = shot
        }
        return bitmap
    }

    /**
     * Pixels that differ from the page's own background, i.e. text and marks.
     *
     * The background is sampled from the page corners rather than assumed, because the reader has a
     * light and a dark theme and a fixed threshold passes or fails on that alone.
     */
    private fun measurePaintedContent(): Int {
        val bitmap = captureScreen() ?: return 0
        var background = 0
        run {
            var r = 0
            var g = 0
            var b = 0
            val corners = listOf(
                0 to 0,
                (bitmap.width - 1) to 0,
                0 to (bitmap.height - 1),
                (bitmap.width - 1) to (bitmap.height - 1)
            )
            corners.forEach { (x, y) ->
                val pixel = bitmap.getPixel(x, y)
                r += Color.red(pixel)
                g += Color.green(pixel)
                b += Color.blue(pixel)
            }
            background = Color.rgb(r / corners.size, g / corners.size, b / corners.size)
        }
        var painted = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                val distance = kotlin.math.abs(Color.red(pixel) - Color.red(background)) +
                    kotlin.math.abs(Color.green(pixel) - Color.green(background)) +
                    kotlin.math.abs(Color.blue(pixel) - Color.blue(background))
                if (distance > 60) painted++
            }
        }
        return painted
    }

    /** Waits until the highlight's own green is visibly on screen, whatever surface is drawing it. */
    private fun awaitGreenPaint(): GreenPaint? {
        val deadline = System.currentTimeMillis() + 45_000
        var latest: GreenPaint? = null
        while (System.currentTimeMillis() < deadline) {
            val measured = measureGreenPaint()
            latest = measured
            if (measured.total > 2_000) return measured
            Thread.sleep(500)
        }
        return latest?.takeIf { it.total > 2_000 }
    }

    private fun measureGreenPaint(): GreenPaint {
        val bitmap = captureScreen() ?: return GreenPaint(0, 0)
        var total = 0
        var saturated = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel == Color.WHITE || pixel == Color.BLACK) continue
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                if (g <= r + 12 || g <= b + 12) continue
                total++
                if (minOf(g - r, g - b) > 70) saturated++
            }
        }
        return GreenPaint(total, saturated)
    }

    private fun persistedSummary(): String = runBlocking {
        AppDatabase.getDatabase(targetContext).recentFileDao().getFileByBookId(bookId)?.highlights
    }?.let { EpubAnnotationSerializer.parseHighlightsJson(it) }
        ?.joinToString { "${it.id}:cfi=${it.cfi}:offsets=${it.locator.startOffset}..${it.locator.endOffset}:locCfi=${it.locator.cfi}:note=${!it.note.isNullOrBlank()}" }
        ?: "none"

    private fun clearBookPrefs() {
        targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE).edit()
            .remove("highlights_data_$sanitizedTitle")
            .commit()
    }

    private fun seed(highlights: List<UserHighlight>) {
        targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE).edit()
            .putString("highlights_data_$sanitizedTitle", EpubAnnotationSerializer.highlightsToJson(highlights))
            .commit()
    }

    /** The shape a highlight has when it is made in the WebView: text and CFI, no offsets. */
    private fun singleHighlight() = UserHighlight(
        id = "noted_in_webview",
        cfi = "0:2:1:6:3",
        text = quote,
        color = HighlightColor.GREEN,
        chapterIndex = 0,
        colorArgb = 0xFF388E3C.toInt(),
        note = "a thought about this passage"
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
