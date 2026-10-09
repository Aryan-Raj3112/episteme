package com.aryan.reader.epubreader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The reported failures, reproduced against the book they happened in.
 *
 * The fixture EPUB is deliberately plain: uniform paragraphs, ordinary spaces, no editorial apparatus.
 * That is exactly why it never reproduced the real thing. This book has the features that broke it —
 * an editor's note in brackets, an en dash used as punctuation, a name interpolated mid-sentence — and
 * the quote below is a sentence from the page in the screenshot, taken from the file itself rather
 * than retyped, so the test compares the reader's idea of that text against the source.
 *
 * The offsets are seeded in the shape the paginated reader used to write: an element-relative base plus
 * a local offset, spanning far past the quote. Nothing but a real book and a real paginator can say
 * whether the reader puts that back where it belongs.
 */
@RunWith(AndroidJUnit4::class)
class EpubHobbitHighlightPlacementTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val fixtureAssetName = "epub/the_hobbit.epub"

    /** Whether the book is present. It is a commercial title and so cannot be committed. */
    private val assetPresent: Boolean by lazy {
        runCatching { instrumentationContext.assets.open(fixtureAssetName).close() }.isSuccess
    }
    private val targetContext: Context = ApplicationProvider.getApplicationContext()
    private val instrumentationContext: Context = InstrumentationRegistry.getInstrumentation().context
    private var scenario: ActivityScenario<MainActivity>? = null
    private var currentEpubFile: File? = null
    private var bookId: String = ""
    private var quoteChapterIndex: Int = -1

    /** Which highlight the running case seeded. */
    private var seededId: String = ""

    /** Verbatim from the book, including the bracketed editorial note and the en dashes. */
    private val quote =
        "without a mighty warrior; even a hero. I tried to find one, but I had to fall back " +
            "(I beg your pardon, but I am sure you will understand – dragon-slaying is not I " +
            "believe your speciality) – to fall back on little Bilbo"

    private companion object {
        const val LEGACY_ID = "hobbit_legacy_offsets"
        const val NO_OFFSETS_ID = "hobbit_no_offsets"
    }

    @Before
    fun setup() {
        if (!assetPresent) return
        quoteChapterIndex = locateQuoteChapter()
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
     * A highlight stored with offsets from the coordinate space the reader no longer uses.
     *
     * The offsets are the shape older builds wrote: an element-relative base plus a character offset
     * within the block, spanning far past the quote. Nothing but a real book and a real paginator can
     * say whether such a highlight is put back where it belongs, and every synthetic book said yes.
     */
    @Test
    fun aRealBookHighlightIsRewrittenToCoverExactlyItsOwnText() {
        requireBook()
        seedHighlight(id = LEGACY_ID, startOffset = 32, endOffset = 347)

        assertPlacedOverItsOwnWords(LEGACY_ID, "hobbit_legacy_offsets")
    }

    /**
     * A highlight stored with no offsets at all, which is what a highlight created by this build carries.
     *
     * Creation no longer computes a chapter position from an element-relative start, because that number
     * was never a position in the chapter. So the shape is now "text, block, DOM position and nothing
     * else", and the first thing the reader has to do with it is find it. This asserts it does, on the one
     * book whose text does not match its own markup tidily.
     */
    @Test
    fun aHighlightCreatedWithoutOffsetsIsPlacedOnItsOwnWords() {
        requireBook()
        seedHighlight(id = NO_OFFSETS_ID, startOffset = null, endOffset = null)

        assertPlacedOverItsOwnWords(NO_OFFSETS_ID, "hobbit_no_offsets")
    }

    private fun requireBook() {
        /*
         * This book is not in the repository. It is a commercial title, so committing it would be
         * distributing copyrighted material, and the test is skipped rather than shipped with one when
         * it is absent. It exists here because every synthetic book hid the bug this found: the fixture
         * EPUB's chapters settle their paginator immediately, and this book's do not, which is what made
         * the difference between the two visible.
         */
        assumeTrue("the test book is not available locally", assetPresent)
    }

    private fun assertPlacedOverItsOwnWords(id: String, captureName: String) {
        scenario = ActivityScenario.launch<MainActivity>(createEpubViewIntent())
        composeTestRule.waitUntil(timeoutMillis = 90_000) {
            composeTestRule.onAllNodesWithTag("ReaderContainer").fetchSemanticsNodes().isNotEmpty()
        }

        val anchored = awaitAnchor()
        val start = anchored?.locator?.startOffset
        val end = anchored?.locator?.endOffset
        println(
            "HOBBIT_RESULT id=$id found=${anchored != null} quoteChars=${quote.length} " +
                "offsets=$start..$end span=${if (start != null && end != null) end - start else null} " +
                "block=${anchored?.locator?.blockIndex} chapter=${anchored?.chapterIndex} " +
                "locatorCfi=${anchored?.locator?.cfi} topLevelCfi=${anchored?.cfi}"
        )
        capture(captureName)

        check(anchored != null) { "the highlight was dropped from the book entirely" }
        check(start != null && end != null) {
            "never anchored: block=${anchored.locator.blockIndex} offsets=$start..$end " +
                "— the chapter index never located the quote in this book"
        }
        check(anchored.locator.blockIndex != null) { "anchored to a range but to no block" }

        // The invariant that matters: a highlight's offsets cover exactly its own words. A span much
        // larger than the quote means the offsets are in some other space, which is what made placement
        // paint whole blocks across the page.
        val span = end - start
        check(kotlin.math.abs(span - quote.length) <= 4) {
            "offsets span $span characters for a ${quote.length}-character quote, so they do not " +
                "describe this highlight"
        }

        /*
         * Open the book again at the position the repair found, which is what a reader does after closing a
         * book. The repaired locator is the only thing that knows which page this highlight belongs on, so
         * landing on it is the only way to see the highlight painted rather than merely stored correctly —
         * and a stored offset is not evidence of a painted one.
         */
        scenario?.close()
        scenario = null
        // The reader saves its position as it shuts down, which would overwrite a position written too
        // eagerly. This is the harness waiting for the book to be closed, not a product behaviour.
        Thread.sleep(2_000)
        runBlocking {
            AppDatabase.getDatabase(targetContext).recentFileDao().updateEpubReadingPosition(
                bookId = bookId,
                // No CFI: the stored one is a DOM position from the seeding, which cannot be converted
                // back to a chapter, and a CFI the reader can read takes precedence over the locator.
                cfi = null,
                chapterIndex = anchored.locator.chapterIndex ?: quoteChapterIndex,
                blockIndex = anchored.locator.blockIndex ?: 0,
                charOffset = anchored.locator.startOffset ?: 0,
                progress = 0.05f,
                timestamp = System.currentTimeMillis()
            )
        }
        scenario = ActivityScenario.launch<MainActivity>(createEpubViewIntent())
        composeTestRule.waitUntil(timeoutMillis = 60_000) {
            composeTestRule.onAllNodesWithTag("ReaderContainer").fetchSemanticsNodes().isNotEmpty()
        }
        // Give the paginator a moment to lay out the landing page, and let the reader report where it
        // actually landed: a reader that ignored the stored position and opened the cover would make the
        // pixel check below meaningless, because this cover is largely green.
        Thread.sleep(5_000)
        val landedIn = landedChapterIndex()
        println("HOBBIT_LANDED id=$id chapter=$landedIn expected=$quoteChapterIndex")
        check(landedIn == quoteChapterIndex) {
            "the book reopened at chapter $landedIn rather than $quoteChapterIndex, so this case would " +
                "measure the cover instead of the highlight — a harness problem, not a placement result"
        }
        capture(captureName)
        val (green, opaque) = countHighlightPixels(captureName)
        println("HOBBIT_PIXELS id=$id green=$green any=$opaque")
        check(green > 2_000) {
            "on the page holding the quote, only $green sampled pixels are the highlight's colour, so " +
                "nothing was painted there"
        }
        // The failure being guarded against is a highlight painted over whole blocks across many pages,
        // which floods the page with its colour. Four lines of text over a full page is a small fraction;
        // a page-swallowing paint is not. Both bounds matter, because the first alone is satisfied by this
        // book's cover art and the second alone by a single stray pixel.
        check(green.toDouble() < opaque * 0.25) {
            "$green of $opaque sampled pixels are the highlight's colour, so it is painted over much of " +
                "the page rather than over its own words"
        }
    }

    /** The chapter the reader last recorded itself to be in, or null if it has not saved yet. */
    private fun landedChapterIndex(): Int? = runBlocking {
        AppDatabase.getDatabase(targetContext).recentFileDao().getFileByBookId(bookId)?.lastChapterIndex
    }

    /**
     * Counts pixels that are the highlight's own colour.
     *
     * Counted rather than asserted as a ratio because the failure being guarded against is a highlight
     * painted over whole blocks across many pages, which floods the page with its colour; the count
     * separates "painted over the words" from "painted over everything" without depending on the text
     * layout of a book that may be re-typeset at any time.
     */
    private fun countHighlightPixels(name: String): Pair<Int, Int> {
        val file = File(targetContext.getExternalFilesDir(null), "$name.png")
        if (!file.exists()) return 0 to 0
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        var green = 0
        var any = 0
        val target = android.graphics.Color.argb(255, 0x38, 0x8E, 0x3C)
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) < 40) continue
                any++
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                // Close to the fill, allowing for the alpha blend the reader applies over the page.
                if (kotlin.math.abs(r - android.graphics.Color.red(target)) < 60 &&
                    kotlin.math.abs(g - android.graphics.Color.green(target)) < 60 &&
                    kotlin.math.abs(b - android.graphics.Color.blue(target)) < 60
                ) {
                    green++
                }
            }
        }
        return green to any
    }

    /**
     * The title this book's metadata carries, reduced the way the reader reduces it: letters and digits
     * only. The preferences key is derived from it, so a test has to derive it the same way or the
     * reader opens the book, finds nothing, and the test asserts against an empty list.
     */
    private val sanitizedTitle = "The Hobbit (Middle-Earth Universe)"
        .filter { it.isLetterOrDigit() }

    /**
     * Finds which chapter the quote is in by reading the book, rather than assuming one.
     *
     * Seeding the wrong chapter would have been an easy way to make this test pass or fail for the
     * wrong reason: a highlight is only ever resolvable against its own chapter's text, so pointing it at
     * the title page guarantees failure no matter how good the reader is. A distinctive slice of the
     * quote is searched for across the spine and its position is used.
     */
    private fun locateQuoteChapter(): Int {
        val needle = "dragon-slaying is not I believe your speciality"
        java.util.zip.ZipFile(
            instrumentationContext.assets.open(fixtureAssetName).let { input ->
                val cache = File(targetContext.cacheDir, "the_hobbit_probe.epub")
                cache.outputStream().use { input.copyTo(it) }
                cache
            }
        ).use { zip ->
            val opf = zip.getInputStream(zip.getEntry("content.opf")!!).bufferedReader().readText()
            val hrefById = Regex("""<item\b[^>]*>""").findAll(opf).mapNotNull { tag ->
                val id = Regex("""\bid="([^"]+)"""").find(tag.value)?.groupValues?.get(1)
                val href = Regex("""\bhref="([^"]+)"""").find(tag.value)?.groupValues?.get(1)
                if (id != null && href != null) id to href else null
            }.toMap()
            val spine = Regex("""idref="([^"]+)"""").findAll(opf).map { it.groupValues[1] }.toList()
            spine.forEachIndexed { index, id ->
                val href = hrefById[id] ?: return@forEachIndexed
                val text = zip.getInputStream(zip.getEntry(href)!!).bufferedReader().readText()
                    .replace(Regex("<[^>]+>"), " ")
                    .replace("&nbsp;", " ")
                    .replace(Regex("\\s+"), " ")
                if (text.contains(needle)) {
                    println("HOBBIT_CHAPTER index=$index href=$href")
                    return index
                }
            }
        }
        error("the quote is not in the book; the test's premise is wrong, not the reader's")
    }

    /**
     * Stores one highlight in the shape [id] describes, for this run's chapter.
     *
     * Written to the preferences key the reader reads on open, derived from the book's title the same way
     * the reader derives it. Seeding the wrong key is a quiet way to assert against an empty list.
     */
    private fun seedHighlight(id: String, startOffset: Int?, endOffset: Int?) {
        seededId = id
        val seeded = UserHighlight(
            id = id,
            cfi = "/4/2/6/4/2/8:300",
            text = quote,
            color = HighlightColor.GREEN,
            chapterIndex = quoteChapterIndex,
            colorArgb = 0xFF388E3C.toInt(),
            locator = ReaderLocator(
                chapterIndex = quoteChapterIndex,
                startOffset = startOffset,
                endOffset = endOffset,
                textQuote = quote,
                cfi = "/4/2/6/4/2/8:300"
            )
        )
        targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
            .edit()
            .putString(
                "highlights_data_$sanitizedTitle",
                EpubAnnotationSerializer.highlightsToJson(listOf(seeded))
            )
            .commit()
    }

    /**
     * The seeded highlight as the reader now stores it, or null if it has gone missing entirely.
     *
     * Read from the database and the preferences copy together, because either can be the one holding it
     * at a given moment: the reader reads highlights from preferences when a book is opened and moves them
     * into the database, and which of the two a lookup finds depends on how far along that handover is.
     * Depending on one of them made this case fail for reasons that had nothing to do with placement.
     */
    private fun awaitAnchor(): UserHighlight? {
        val deadline = System.currentTimeMillis() + 120_000
        var latest: UserHighlight? = null
        while (System.currentTimeMillis() < deadline) {
            latest = stored().firstOrNull { it.id == seededId }
            if (latest?.locator?.blockIndex != null) return latest
            Thread.sleep(500)
        }
        return latest
    }

    /** Everywhere the reader might have put the highlight, newest location last. */
    private fun stored(): List<UserHighlight> {
        val fromPrefs = runCatching {
            EpubAnnotationSerializer.parseHighlightsJson(
                targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
                    .getString("highlights_data_$sanitizedTitle", "[]")
            )
        }.getOrDefault(emptyList())
        return fromPrefs.ifEmpty { persisted() } + persisted()
    }

    /** The database is authoritative; an open reader clears the preferences copy. */
    private fun persisted(): List<UserHighlight> = runBlocking {
        AppDatabase.getDatabase(targetContext).recentFileDao().getFileByBookId(bookId)?.highlights
    }?.let { EpubAnnotationSerializer.parseHighlightsJson(it) } ?: emptyList()

    /** Writes a screenshot so the placement can be looked at, not only asserted. */
    private fun capture(name: String) {
        var bitmap: Bitmap? = null
        scenario?.onActivity { activity ->
            val view = activity.window.decorView
            if (view.width == 0 || view.height == 0) return@onActivity
            val target = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            // PixelCopy, not view.draw: the decor view holds hardware bitmaps and a software canvas
            // refuses to draw those.
            val latch = java.util.concurrent.CountDownLatch(1)
            android.view.PixelCopy.request(
                activity.window,
                target,
                { latch.countDown() },
                android.os.Handler(android.os.Looper.getMainLooper())
            )
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            bitmap = target
        }
        val shot = bitmap ?: return
        val out = File(targetContext.getExternalFilesDir(null), "$name.png")
        out.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("HOBBIT_FILE ${out.absolutePath}")
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