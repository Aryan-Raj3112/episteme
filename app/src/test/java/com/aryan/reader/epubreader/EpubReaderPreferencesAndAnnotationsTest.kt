package com.aryan.reader.epubreader

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.epub.EpubChapter
import io.mockk.every
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReaderPreferencesAndAnnotationsTest {

    @Test
    fun `reader settings defaults and invalid persisted enum values fall back safely`() {
        val prefs = TestSharedPreferences(
            "reader_system_ui_mode" to Int.MIN_VALUE,
            "reader_page_info_mode" to Int.MAX_VALUE,
            "reader_page_info_position" to -20,
            "reader_font_family" to "missing",
            "reader_text_align" to "diagonal"
        )
        val context = contextWithPrefs(SETTINGS_PREFS_NAME to prefs)

        val format = loadFormatSettings(context, bookId = "book", isLocal = false)

        assertEquals(SystemUiMode.DEFAULT, loadSystemUiMode(context))
        assertEquals(PageInfoMode.DEFAULT, loadPageInfoMode(context))
        assertEquals(PageInfoPosition.BOTTOM, loadPageInfoPosition(context))
        assertEquals(DEFAULT_FONT_SIZE_VAL, format.fontSize, 0.0001f)
        assertEquals(DEFAULT_LINE_HEIGHT_VAL, format.lineHeight, 0.0001f)
        assertEquals(DEFAULT_PARAGRAPH_GAP_VAL, format.paragraphGap, 0.0001f)
        assertEquals(DEFAULT_IMAGE_SIZE_VAL, format.imageSize, 0.0001f)
        assertEquals(ReaderFont.ORIGINAL, format.font)
        assertEquals(ReaderTextAlign.DEFAULT, format.textAlign)
        assertNull(format.customPath)
    }

    @Test
    fun `global and local format settings round trip including custom fonts`() {
        val prefs = TestSharedPreferences()
        val context = contextWithPrefs(SETTINGS_PREFS_NAME to prefs)

        saveReaderSettings(
            context = context,
            fontSize = 1.4f,
            lineHeight = 1.6f,
            paragraphGap = 0.7f,
            imageSize = 1.2f,
            horizontalMargin = 1.8f,
            verticalMargin = 0.4f,
            fontFamily = ReaderFont.LORA,
            customFontPath = null,
            textAlign = ReaderTextAlign.JUSTIFY
        )
        saveLocalReaderSettings(
            context = context,
            bookId = "book",
            fontSize = 0.9f,
            lineHeight = 1.1f,
            paragraphGap = 1.3f,
            imageSize = 1.5f,
            horizontalMargin = 0.2f,
            verticalMargin = 2.2f,
            fontFamily = ReaderFont.MERRIWEATHER,
            customFontPath = "/fonts/custom.ttf",
            textAlign = ReaderTextAlign.LEFT
        )

        val global = loadFormatSettings(context, bookId = "book", isLocal = false)
        val local = loadFormatSettings(context, bookId = "book", isLocal = true)

        assertEquals(1.4f, global.fontSize, 0.0001f)
        assertEquals(ReaderFont.LORA, global.font)
        assertEquals(ReaderTextAlign.JUSTIFY, global.textAlign)
        assertNull(global.customPath)
        assertEquals(0.9f, local.fontSize, 0.0001f)
        assertEquals(1.1f, local.lineHeight, 0.0001f)
        assertEquals(1.3f, local.paragraphGap, 0.0001f)
        assertEquals(1.5f, local.imageSize, 0.0001f)
        assertEquals(0.2f, local.horizontalMargin, 0.0001f)
        assertEquals(2.2f, local.verticalMargin, 0.0001f)
        assertEquals(ReaderFont.ORIGINAL, local.font)
        assertEquals("/fonts/custom.ttf", local.customPath)
        assertEquals(ReaderTextAlign.LEFT, local.textAlign)
    }

    @Test
    fun `local format settings fall back to global values per missing local field`() {
        val prefs = TestSharedPreferences(
            "reader_font_size" to 1.8f,
            "reader_line_height" to 1.7f,
            "reader_paragraph_gap" to 1.6f,
            "reader_image_size" to 1.5f,
            "reader_horizontal_margin" to 1.4f,
            "reader_vertical_margin" to 1.3f,
            "reader_font_family" to ReaderFont.LEXEND.id,
            "reader_text_align" to ReaderTextAlign.JUSTIFY.id,
            "local_font_size_book" to 0.8f,
            "local_font_family_book" to ReaderFont.LATO.id
        )
        val context = contextWithPrefs(SETTINGS_PREFS_NAME to prefs)

        val local = loadFormatSettings(context, bookId = "book", isLocal = true)

        assertEquals(0.8f, local.fontSize, 0.0001f)
        assertEquals(1.7f, local.lineHeight, 0.0001f)
        assertEquals(1.6f, local.paragraphGap, 0.0001f)
        assertEquals(1.5f, local.imageSize, 0.0001f)
        assertEquals(1.4f, local.horizontalMargin, 0.0001f)
        assertEquals(1.3f, local.verticalMargin, 0.0001f)
        assertEquals(ReaderFont.LATO, local.font)
        assertEquals(ReaderTextAlign.JUSTIFY, local.textAlign)
    }

    @Test
    fun `simple reader preference toggles and numeric settings round trip`() {
        val prefs = TestSharedPreferences()
        val context = contextWithPrefs(SETTINGS_PREFS_NAME to prefs)

        saveTtsSpeechRate(context, 1.35f)
        saveTtsPitch(context, 0.85f)
        saveSystemUiMode(context, SystemUiMode.HIDDEN)
        savePageInfoMode(context, PageInfoMode.SYNC)
        savePageInfoPosition(context, PageInfoPosition.TOP)
        savePullToTurn(context, false)
        savePullToTurnMultiplier(context, 1.75f)
        saveAutoScrollSpeed(context, 2.5f)
        saveTapToNavigateSetting(context, true)
        saveVolumeScrollSetting(context, true)
        saveRemoveEdgePadding(context, true)
        saveFormatIsLocal(context, "book", true)

        assertEquals(1.35f, loadTtsSpeechRate(context), 0.0001f)
        assertEquals(0.85f, loadTtsPitch(context), 0.0001f)
        assertEquals(SystemUiMode.HIDDEN, loadSystemUiMode(context))
        assertEquals(PageInfoMode.SYNC, loadPageInfoMode(context))
        assertEquals(PageInfoPosition.TOP, loadPageInfoPosition(context))
        assertFalse(loadPullToTurn(context))
        assertEquals(1.75f, loadPullToTurnMultiplier(context), 0.0001f)
        assertEquals(2.5f, loadAutoScrollSpeed(context), 0.0001f)
        assertTrue(loadTapToNavigateSetting(context))
        assertTrue(loadVolumeScrollSetting(context))
        assertTrue(loadRemoveEdgePadding(context))
        assertTrue(loadFormatIsLocal(context, "book"))
        assertEquals(0f, loadHorizontalMargin(context), 0.0001f)
    }

    @Test
    fun `explicit horizontal margin wins over remove edge padding migration fallback`() {
        val prefs = TestSharedPreferences()
        val context = contextWithPrefs(SETTINGS_PREFS_NAME to prefs)

        saveRemoveEdgePadding(context, true)
        saveReaderSettings(
            context = context,
            fontSize = 1f,
            lineHeight = 1f,
            paragraphGap = 1f,
            imageSize = 1f,
            horizontalMargin = 2.4f,
            verticalMargin = 1f,
            fontFamily = ReaderFont.ORIGINAL,
            customFontPath = null,
            textAlign = ReaderTextAlign.DEFAULT
        )

        assertEquals(2.4f, loadHorizontalMargin(context), 0.0001f)
    }

    @Test
    fun `highlight palette saves exactly four known colors and falls back otherwise`() {
        val prefs = TestSharedPreferences()
        val context = contextWithPrefs(SETTINGS_PREFS_NAME to prefs)

        saveHighlightPalette(context, listOf(HighlightColor.CYAN, HighlightColor.MAGENTA, HighlightColor.LIME, HighlightColor.PINK))

        assertEquals(
            listOf(HighlightColor.CYAN, HighlightColor.MAGENTA, HighlightColor.LIME, HighlightColor.PINK),
            loadHighlightPalette(context)
        )

        val invalidContext = contextWithPrefs(
            SETTINGS_PREFS_NAME to TestSharedPreferences("highlight_palette_ids" to "yellow,unknown,blue")
        )
        assertEquals(
            listOf(HighlightColor.YELLOW, HighlightColor.GREEN, HighlightColor.BLUE, HighlightColor.RED),
            loadHighlightPalette(invalidContext)
        )
    }

    @Test
    fun `highlight JSON round trips notes escapes unknown colors and invalid JSON`() {
        val highlights = listOf(
            UserHighlight(id = "h1", cfi = "/4/2:1", text = "Quote", color = HighlightColor.BLUE, chapterIndex = 2, note = "Remember"),
            UserHighlight(id = "h2", cfi = "/4/4:3", text = "Plain", color = HighlightColor.RED, chapterIndex = 3, note = null)
        )

        val parsed = parseHighlightsJson(highlightsToJson(highlights))

        assertEquals(highlights, parsed)
        assertNull(parsed[1].note)
        val unknownColorJson = JSONArray().put(
            JSONObject()
                .put("id", "h3")
                .put("cfi", "/4")
                .put("text", "Text")
                .put("colorId", "infrared")
                .put("chapterIndex", 1)
        ).toString()
        assertEquals(HighlightColor.YELLOW, parseHighlightsJson(unknownColorJson).single().color)
        assertEquals(emptyList<UserHighlight>(), parseHighlightsJson("{broken"))
        assertEquals(emptyList<UserHighlight>(), parseHighlightsJson(null))
    }

    @Test
    fun `highlight preference storage uses sanitized title and can be cleared`() {
        val prefs = TestSharedPreferences()
        val context = contextWithPrefs(SETTINGS_PREFS_NAME to prefs)
        val highlight = UserHighlight(id = "h1", cfi = "/4", text = "Text", color = HighlightColor.GREEN, chapterIndex = 0, note = "Note")

        saveHighlightsToPrefs(context, "Book: One!", listOf(highlight))

        assertEquals(listOf(highlight), loadHighlightsFromPrefs(context, "Book One"))
        clearHighlightsFromPrefs(context, "Book One")
        assertEquals(emptyList<UserHighlight>(), loadHighlightsFromPrefs(context, "Book One"))
    }

    @Test
    fun `processAndAddHighlight updates exact cfi match and appends overlaps independently`() {
        val highlights = mutableListOf(
            UserHighlight(id = "existing", cfi = "/4/2:10", text = "Old", color = HighlightColor.YELLOW, chapterIndex = 1, note = "keep")
        )

        val updatedCfi = processAndAddHighlight("/4/2:10", "New", HighlightColor.PURPLE, chapterIndex = 1, currentList = highlights)
        val addedCfi = processAndAddHighlight("/4/2:11", "Overlap", HighlightColor.CYAN, chapterIndex = 1, currentList = highlights)

        assertEquals("/4/2:10", updatedCfi)
        assertEquals("/4/2:11", addedCfi)
        assertEquals(2, highlights.size)
        assertEquals("existing", highlights[0].id)
        assertEquals("New", highlights[0].text)
        assertEquals(HighlightColor.PURPLE, highlights[0].color)
        assertEquals("keep", highlights[0].note)
        assertEquals("Overlap", highlights[1].text)
    }

    @Test
    fun `bookmarks parse current and legacy payloads with optional label and pages`() {
        val chapters = listOf(
            chapter("Chapter 1"),
            chapter("Chapter 2")
        )
        val current = JSONObject()
            .put("cfi", "/4")
            .put("chapterTitle", "Chapter 1")
            .put("label", "Named mark")
            .put("snippet", "Snippet")
            .put("pageInChapter", 2)
            .put("totalPagesInChapter", 9)
            .put("chapterIndex", 0)
        val legacy = JSONObject()
            .put("cfi", "/6")
            .put("chapterTitle", "Chapter 2")
            .put("snippet", "Legacy")
        val context = contextWithPrefs()

        val bookmarks = loadBookmarks(context, "Book", chapters, JSONArray(listOf(current.toString(), legacy.toString())).toString())

        assertEquals(
            setOf(
                Bookmark("/4", "Chapter 1", "Named mark", "Snippet", 2, 9, 0),
                Bookmark("/6", "Chapter 2", null, "Legacy", null, null, 1)
            ),
            bookmarks
        )
    }

    @Test
    fun `bookmarks fall back to shared preferences using sanitized book title`() {
        val bookmarkPrefs = TestSharedPreferences(
            "bookmarks_cfi_BookOne" to setOf(
                JSONObject()
                    .put("cfi", "/4")
                    .put("chapterTitle", "Chapter")
                    .put("snippet", "Saved")
                    .put("chapterIndex", 0)
                    .toString()
            )
        )
        val context = contextWithPrefs("epub_reader_bookmarks" to bookmarkPrefs)

        val bookmarks = loadBookmarks(context, "Book: One!", listOf(chapter("Chapter")), bookmarksJson = null)

        assertEquals(setOf(Bookmark("/4", "Chapter", null, "Saved", null, null, 0)), bookmarks)
    }

    @Test
    fun `bookmarks ignore malformed entries while keeping valid ones from view model json`() {
        val valid = JSONObject()
            .put("cfi", "/8")
            .put("chapterTitle", "Chapter")
            .put("snippet", "Valid")
            .put("chapterIndex", 0)
            .toString()
        val malformed = "{\"cfi\":\"/broken\""
        val context = contextWithPrefs()

        val bookmarks = loadBookmarks(context, "Book", listOf(chapter("Chapter")), JSONArray(listOf(valid, malformed)).toString())

        assertEquals(setOf(Bookmark("/8", "Chapter", null, "Valid", null, null, 0)), bookmarks)
    }

    @Test
    fun `escapeJsString escapes all characters that break JavaScript string literals`() {
        val raw = "\\ ' \" \n \r \t \u2028 \u2029"

        assertEquals("\\\\ \\' \\\" \\n \\r \\t \\u2028 \\u2029", escapeJsString(raw))
    }

    @Test
    fun `highlight color metadata stays unique and maps to concrete argb colors`() {
        assertEquals(HighlightColor.entries.size, HighlightColor.entries.map { it.id }.toSet().size)
        assertEquals(HighlightColor.entries.size, HighlightColor.entries.map { it.cssClass }.toSet().size)
        assertEquals(Color(0xFFFBC02D).toArgb(), HighlightColor.YELLOW.color.toArgb())
    }

    private fun chapter(title: String): EpubChapter =
        EpubChapter(
            chapterId = title,
            absPath = "$title.xhtml",
            title = title,
            htmlFilePath = "$title.xhtml",
            plainTextContent = "",
            htmlContent = ""
        )

    private fun contextWithPrefs(vararg prefsByName: Pair<String, SharedPreferences>): Context {
        val context = mockk<Context>()
        val prefsMap = prefsByName.toMap()
        every { context.getSharedPreferences(any<String>(), Context.MODE_PRIVATE) } answers {
            prefsMap[firstArg<String>()] ?: TestSharedPreferences()
        }
        return context
    }
}
