package com.aryan.reader.epubreader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aryan.reader.FileType
import com.aryan.reader.FileHasher
import com.aryan.reader.MainActivity
import com.aryan.reader.R
import com.aryan.reader.RenderMode
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.data.RecentFileEntity
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.ReaderLocator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EpubReaderScreenTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val fixtureAssetName = "epub/reader_test_book.epub"
    private val fixtureBookTitle = "Reader Android UI Test Book"
    private val sanitizedFixtureBookTitle = "ReaderAndroidUITestBook"
    private val targetContext: Context = ApplicationProvider.getApplicationContext()
    private val instrumentationContext: Context = InstrumentationRegistry.getInstrumentation().context
    private var currentEpubFile: File? = null
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var fixtureBookId: String

    @Before
    fun setup() {
        clearReaderPrefs()
        fixtureBookId = requireNotNull(
            runBlocking {
                FileHasher.calculateSha256 {
                    instrumentationContext.assets.open(fixtureAssetName)
                }
            }
        )
        runBlocking {
            AppDatabase.getDatabase(targetContext)
                .recentFileDao()
                .deleteFilePermanently(listOf(fixtureBookId))
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        currentEpubFile?.let {
            if (it.exists()) it.delete()
        }
    }

    @Test
    fun fixtureEpub_opensReaderAndShowsBookTitle() {
        launchFixtureReader()
        waitForReader()
        showReaderChrome()

        waitForText(fixtureBookTitle)
        composeTestRule.onNodeWithText(fixtureBookTitle).assertIsDisplayed()
        assertThat(hasContentDescription(text(R.string.tooltip_search))).isTrue()
        assertThat(hasContentDescription(text(R.string.content_desc_chapters_menu))).isTrue()
    }

    @Test
    fun fixtureEpub_recordsInitialReadingPosition() {
        launchFixtureReader()
        waitForReader()

        waitForRecentFile(timeoutMillis = 30_000) { recentFile ->
            recentFile?.lastChapterIndex != null &&
                recentFile.locatorBlockIndex != null &&
                recentFile.locatorCharOffset != null &&
                recentFile.progressPercentage != null
        }

        val recentFile = readFixtureRecentFile()
        assertThat(recentFile?.lastChapterIndex).isAtLeast(0)
        assertThat(recentFile?.locatorBlockIndex).isAtLeast(0)
        assertThat(recentFile?.locatorCharOffset).isAtLeast(0)
        assertThat(recentFile?.progressPercentage).isAtLeast(0f)
    }

    @Test
    fun fixtureEpub_restoresSeededReadingPositionWithoutResettingToStart() {
        launchFixtureReader { fixtureUri ->
            seedFixtureRecentFile(
                uriString = fixtureUri.toString(),
                chapterIndex = 1,
                blockIndex = 1,
                charOffset = 0,
                progress = 45f
            )
        }
        waitForReader()

        waitForRecentFile(timeoutMillis = 30_000) { recentFile ->
            recentFile?.lastChapterIndex == 1 &&
                recentFile.locatorBlockIndex == 1 &&
                recentFile.locatorCharOffset == 0 &&
                (recentFile.progressPercentage ?: 0f) >= 45f
        }
    }

    @Test
    fun fixtureEpub_drawerShowsFixtureChapters() {
        launchFixtureReader()
        waitForReader()

        clickReaderControl(text(R.string.content_desc_chapters_menu))

        waitForText(text(R.string.tab_chapters))
        waitForTextContaining("Chapter One")
        waitForTextContaining("Chapter Two")
        waitForTextContaining("Chapter Three")
    }

    @Test
    fun fixtureEpub_drawerShowsFixtureImageCatalog() {
        launchFixtureReader()
        waitForReader()

        clickReaderControl(text(R.string.content_desc_chapters_menu))
        clickText(text(R.string.tab_images))

        waitForText("Fixture diagram")
        waitForTextContaining("Chapter Three")
        assertThat(hasContentDescription(text(R.string.content_desc_download_image))).isTrue()
    }

    @Test
    fun fixtureEpub_searchFindsUniqueFixtureMarker() {
        launchFixtureReader()
        waitForReader()

        clickReaderControl(text(R.string.tooltip_search))
        waitForTag("SearchTextField")

        composeTestRule.onNodeWithTag("SearchTextField").performTextInput("SEARCH_TARGET_DELTA")
        composeTestRule.onNodeWithTag("SearchTextField").assertTextContains("SEARCH_TARGET_DELTA")

        waitForTag("SearchResultItem_1", timeoutMillis = 20_000)
        composeTestRule.onNodeWithTag("SearchResultItem_1").assertIsDisplayed()
    }

    @Test
    fun fixtureEpub_searchNavigationPositionSurvivesReadingModeSwitches() {
        launchFixtureReader()
        waitForReader()

        navigateToFixtureSearchResult("SEARCH_TARGET_DELTA", expectedChapterIndex = 1)
        clickContentDescription(text(R.string.tooltip_close_search))

        waitForRecentFile(timeoutMillis = 30_000) { recentFile ->
            recentFile?.lastChapterIndex == 1 &&
                recentFile.locatorBlockIndex != null &&
                recentFile.locatorCharOffset != null &&
                recentFile.progressPercentage != null
        }

        openOverflowMenu()
        clickText(text(R.string.menu_change_reading_mode))
        clickText(text(R.string.menu_reading_mode_paginated))
        waitForRenderMode(RenderMode.PAGINATED)

        waitForRecentFile(timeoutMillis = 20_000) { recentFile ->
            recentFile?.lastChapterIndex == 1 &&
                (recentFile.progressPercentage ?: 0f) > 0f
        }

        openOverflowMenu()
        clickText(text(R.string.menu_change_reading_mode))
        clickText(text(R.string.menu_reading_mode_vertical_webview))
        waitForRenderMode(RenderMode.VERTICAL_SCROLL)

        waitForRecentFile(timeoutMillis = 20_000) { recentFile ->
            recentFile?.lastChapterIndex == 1 &&
                (recentFile.progressPercentage ?: 0f) > 0f
        }
    }

    @Test
    fun fixtureEpub_addsCurrentPageBookmarkPersistsAndDeletes() {
        launchFixtureReader()
        waitForReader()

        openOverflowMenu()
        clickText(text(R.string.menu_bookmark_this_page))

        waitForFixtureBookmarks(timeoutMillis = 20_000) { bookmarksJson ->
            parseBookmarksJson(bookmarksJson).isNotEmpty()
        }

        clickReaderControl(text(R.string.content_desc_chapters_menu))
        clickText(text(R.string.tab_bookmarks))
        waitForTextContaining("Chapter One")
        assertThat(hasContentDescription(text(R.string.content_desc_more_options_bookmark))).isTrue()

        clickContentDescription(text(R.string.content_desc_more_options_bookmark))
        clickText(text(R.string.action_delete))
        waitForText(text(R.string.dialog_delete_bookmark))
        clickText(text(R.string.action_delete))

        waitForText(text(R.string.no_bookmarks_yet))
        waitForFixtureBookmarks(timeoutMillis = 10_000) { bookmarksJson ->
            parseBookmarksJson(bookmarksJson).isEmpty()
        }
    }

    @Test
    fun fixtureEpub_drawerShowsSeededBookmarkAndSupportsRenameDelete() {
        seedFixtureBookmark()
        launchFixtureReader()
        waitForReader()

        clickReaderControl(text(R.string.content_desc_chapters_menu))
        clickText(text(R.string.tab_bookmarks))

        waitForText("BOOKMARK_TARGET_ECHO")
        waitForText("Chapter Two")

        clickContentDescription(text(R.string.content_desc_more_options_bookmark))
        clickText(text(R.string.action_rename))
        waitForText(text(R.string.dialog_rename_bookmark))
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Renamed fixture bookmark")
        clickText(text(R.string.action_save))
        waitForText("Renamed fixture bookmark")

        clickContentDescription(text(R.string.content_desc_more_options_bookmark))
        clickText(text(R.string.action_delete))
        waitForText(text(R.string.dialog_delete_bookmark))
        clickText(text(R.string.action_delete))
        waitForText(text(R.string.no_bookmarks_yet))
    }

    @Test
    fun fixtureEpub_drawerShowsSeededAnnotationWithNoteAndFilter() {
        seedFixtureHighlight()
        launchFixtureReader()
        waitForReader()

        clickReaderControl(text(R.string.content_desc_chapters_menu))
        clickText(text(R.string.tab_annotations))

        waitForText("ANNOTATION_TARGET_GOLF")
        waitForText("Fixture note survives startup")
        waitForTextContaining("Chapter Three")

        clickText(text(R.string.filter_with_notes))
        waitForText("ANNOTATION_TARGET_GOLF")
        waitForText("Fixture note survives startup")
    }

    @Test
    fun fixtureEpub_seededAnnotationSupportsColorNoteAndDeletePersistence() {
        seedFixtureHighlight()
        launchFixtureReader()
        waitForReader()

        clickReaderControl(text(R.string.content_desc_chapters_menu))
        clickText(text(R.string.tab_annotations))

        waitForText("ANNOTATION_TARGET_GOLF")
        clickContentDescription(text(R.string.content_desc_options))
        composeTestRule.onNodeWithTag("HighlightColor_blue").performClick()

        waitForFixtureHighlights(timeoutMillis = 10_000) { highlightsJson ->
            parseHighlightsJson(highlightsJson).singleOrNull()?.color == HighlightColor.BLUE
        }

        clickContentDescription(text(R.string.content_desc_options))
        clickText(text(R.string.menu_edit_note))
        waitForText(text(R.string.action_save_note))
        composeTestRule.onNode(hasSetTextAction())
            .performTextClearance()
        composeTestRule.onNode(hasSetTextAction())
            .performTextInput("Updated fixture note")
        clickText(text(R.string.action_save_note))

        waitForText("Updated fixture note")
        waitForFixtureHighlights(timeoutMillis = 10_000) { highlightsJson ->
            parseHighlightsJson(highlightsJson).singleOrNull()?.note == "Updated fixture note"
        }

        clickContentDescription(text(R.string.content_desc_options))
        clickText(text(R.string.action_delete))
        waitForText(text(R.string.dialog_delete_highlight))
        clickText(text(R.string.action_delete))

        waitForText(text(R.string.no_highlights_yet))
        waitForFixtureHighlights(timeoutMillis = 10_000) { highlightsJson ->
            parseHighlightsJson(highlightsJson).isEmpty()
        }
    }

    @Test
    fun fixtureEpub_overflowSwitchesReadingModeAndTogglesPageOptions() {
        launchFixtureReader()
        waitForReader()

        openOverflowMenu()
        clickText(text(R.string.menu_change_reading_mode))
        clickText(text(R.string.menu_reading_mode_paginated))
        waitForRenderMode(RenderMode.PAGINATED)
        composeTestRule.waitForIdle()

        openOverflowMenu()
        clickText(text(R.string.menu_tap_to_turn_pages))
        assertReaderSettingEventually(
            prefsName = "epub_reader_settings",
            key = "tap_to_navigate_enabled",
            expected = true
        )

        openOverflowMenu()
        clickText(text(R.string.menu_realistic_page_turns))
        assertReaderSettingEventually(
            prefsName = "reader_prefs",
            key = "page_turn_animation_enabled",
            expected = true
        )

        openOverflowMenu()
        clickText(text(R.string.menu_keep_screen_on))
        assertReaderSettingEventually(
            prefsName = "reader_prefs",
            key = "keep_screen_on_enabled",
            expected = true
        )
    }

    @Test
    fun fixtureEpub_visualOptionsSheetPersistsProgressPosition() {
        launchFixtureReader()
        waitForReader()

        openOverflowMenu()
        clickText(text(R.string.menu_visual_options))

        waitForText(text(R.string.visual_options_system_ui))
        waitForText(text(R.string.visual_options_progress_bar))
        waitForText(text(R.string.visual_options_progress_bar_position))
        clickText(text(R.string.label_top))

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
                .getInt("reader_page_info_position", 0) == 1
        }
    }

    @Test
    fun fixtureEpub_formatPanelShowsControlsAndPersistsLocalFontSize() {
        launchFixtureReader()
        waitForReader()

        clickReaderControl(text(R.string.content_desc_text_formatting))
        waitForText(text(R.string.section_font_alignment))
        waitForText(text(R.string.section_layout_spacing))
        waitForText(text(R.string.label_font_size))
        waitForText(text(R.string.label_line_height))
        waitForText(text(R.string.label_paragraph_gap))
        waitForText(text(R.string.label_image_size))
        waitForText(text(R.string.label_horizontal_margin))
        waitForText(text(R.string.label_vertical_margin))

        composeTestRule.onAllNodesWithContentDescription(text(R.string.content_desc_select_mode))[0]
            .performClick()
        clickText(text(R.string.format_local))

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
                .getBoolean("format_is_local_$fixtureBookId", false)
        }

        composeTestRule.onAllNodesWithContentDescription(text(R.string.content_desc_increase))[0]
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
                .getFloat("local_font_size_$fixtureBookId", 1.0f) > 1.0f
        }
    }

    @Test
    fun fixtureEpub_fontSelectionSheetPersistsFontFamily() {
        launchFixtureReader()
        waitForReader()

        clickReaderControl(text(R.string.content_desc_text_formatting))
        waitForText(text(R.string.section_font_alignment))
        clickContentDescription(text(R.string.content_desc_select_font_family))

        waitForText(text(R.string.select_font))
        waitForText(text(R.string.tab_presets))
        waitForText(text(R.string.tab_imported))
        clickText("Lato")

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
                .getString("reader_font_family", "original") == "lato"
        }
    }

    @Test
    fun fixtureEpub_themePanelShowsThemesAndPersistsSelection() {
        launchFixtureReader()
        waitForReader()

        clickReaderControl(text(R.string.tooltip_theme_desc))

        waitForText(text(R.string.reading_themes))
        waitForText(text(R.string.theme_solid_colors))
        waitForText("Light")
        waitForText("Dark")
        clickText("Sepia")

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            targetContext.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
                .getString(PREF_READER_THEME, "system") == "sepia"
        }
    }

    @Test
    fun fixtureEpub_drawerShowsEmptyBookmarkAndAnnotationStates() {
        launchFixtureReader()
        waitForReader()

        clickReaderControl(text(R.string.content_desc_chapters_menu))
        clickText(text(R.string.tab_bookmarks))
        waitForText(text(R.string.no_bookmarks_yet))

        clickText(text(R.string.tab_annotations))
        waitForText(text(R.string.no_highlights_yet))
    }

    @Test
    fun fixtureEpub_searchCanClearAndClose() {
        launchFixtureReader()
        waitForReader()

        clickReaderControl(text(R.string.tooltip_search))
        waitForTag("SearchTextField")

        composeTestRule.onNodeWithTag("SearchTextField")
            .performTextInput("SEARCH_TARGET_DELTA")
        waitForTextContaining("SEARCH_TARGET_DELTA")

        clickContentDescription(text(R.string.tooltip_clear_search))
        waitForNoContentDescription(text(R.string.tooltip_clear_search))

        clickContentDescription(text(R.string.tooltip_close_search))
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            hasContentDescription(text(R.string.tooltip_search))
        }
    }

    @Test
    fun fixtureEpub_dictionarySettingsShowsLookupSections() {
        launchFixtureReader()
        waitForReader()

        clickReaderControl(text(R.string.content_desc_dictionary_settings))

        waitForText(text(R.string.dict_lookup_settings))
        waitForText(text(R.string.tooltip_dictionary))
        waitForText(text(R.string.dict_translate))
        waitForText(text(R.string.tooltip_search))
    }

    @Test
    fun fixtureEpub_ttsReplacementSheetShowsGlobalAndBookScopes() {
        launchFixtureReader()
        waitForReader()

        openOverflowMenu()
        clickText(text(R.string.menu_tts_settings))
        clickText(text(R.string.menu_tts_word_replacements))

        waitForText(text(R.string.menu_tts_word_replacements))
        waitForText(fixtureBookTitle)
        waitForText(text(R.string.tts_replacements_tab_global))
        waitForText(text(R.string.tts_replacements_tab_this_book))
        waitForText(text(R.string.tts_replacements_enable))
    }

    private fun launchFixtureReader(beforeLaunch: (Uri) -> Unit = {}) {
        val fixtureUri = copyAndroidTestAssetToCache(fixtureAssetName)
        beforeLaunch(fixtureUri)
        scenario = ActivityScenario.launch<MainActivity>(createEpubViewIntent(fixtureUri))
    }

    private fun clearReaderPrefs() {
        listOf(
            "epub_reader_settings",
            "epub_reader_bookmarks",
            "reader_prefs"
        ).forEach { prefsName ->
            targetContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
        targetContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("render_mode", "VERTICAL_SCROLL")
            .commit()
    }

    private fun text(resId: Int): String = targetContext.getString(resId)

    private fun seedFixtureBookmark() {
        val bookmark = org.json.JSONObject().apply {
            put("cfi", "android-locator:1:1:0")
            put("chapterTitle", "Chapter Two")
            put("label", org.json.JSONObject.NULL)
            put("snippet", "BOOKMARK_TARGET_ECHO")
            put("pageInChapter", 1)
            put("totalPagesInChapter", 3)
            put("chapterIndex", 1)
            put(
                "locator",
                org.json.JSONObject().apply {
                    put("chapterIndex", 1)
                    put("chapterId", "chapter-02")
                    put("pageIndex", 0)
                    put("blockIndex", 1)
                    put("charOffset", 0)
                    put("textQuote", "BOOKMARK_TARGET_ECHO")
                    put("cfi", "android-locator:1:1:0")
                }
            )
        }

        targetContext.getSharedPreferences("epub_reader_bookmarks", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("bookmarks_cfi_$sanitizedFixtureBookTitle", setOf(bookmark.toString()))
            .commit()
    }

    private fun seedFixtureHighlight() {
        val highlight = UserHighlight(
            id = "fixture_annotation_golf",
            cfi = "android-locator:2:1:0",
            text = "ANNOTATION_TARGET_GOLF",
            color = HighlightColor.GREEN,
            chapterIndex = 2,
            note = "Fixture note survives startup",
            locator = ReaderLocator(
                chapterIndex = 2,
                chapterId = "chapter-03",
                blockIndex = 1,
                charOffset = 0,
                textQuote = "ANNOTATION_TARGET_GOLF",
                cfi = "android-locator:2:1:0"
            )
        )

        targetContext.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("highlights_data_$sanitizedFixtureBookTitle", highlightsToJson(listOf(highlight)))
            .commit()
    }

    private fun seedFixtureRecentFile(
        uriString: String,
        chapterIndex: Int,
        blockIndex: Int,
        charOffset: Int,
        progress: Float
    ) {
        val now = System.currentTimeMillis()
        runBlocking {
            AppDatabase.getDatabase(targetContext)
                .recentFileDao()
                .insertOrUpdateFile(
                    RecentFileEntity(
                        bookId = fixtureBookId,
                        uriString = uriString,
                        type = FileType.EPUB,
                        displayName = fixtureBookTitle,
                        timestamp = now,
                        coverImagePath = null,
                        title = fixtureBookTitle,
                        author = "Fixture Author",
                        lastChapterIndex = chapterIndex,
                        lastPage = null,
                        lastPositionCfi = "android-locator:$chapterIndex:$blockIndex:$charOffset",
                        progressPercentage = progress,
                        isRecent = true,
                        isAvailable = true,
                        lastModifiedTimestamp = now,
                        isDeleted = false,
                        locatorBlockIndex = blockIndex,
                        locatorCharOffset = charOffset,
                        bookmarks = null,
                        sourceFolderUri = null,
                        isReflowPreferred = false,
                        customName = null,
                        highlights = null,
                        fileSize = 0L,
                        fileContentModifiedTimestamp = 0L,
                        seriesName = null,
                        seriesIndex = null,
                        description = null,
                        folderTextMetadataParsed = false,
                        folderCoverMetadataParsed = false,
                        originalTitle = fixtureBookTitle,
                        originalAuthor = "Fixture Author",
                        originalSeriesName = null,
                        originalSeriesIndex = null,
                        originalDescription = null
                    )
                )
        }
    }

    private fun createEpubViewIntent(uri: Uri): Intent {
        return Intent(targetContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, "application/epub+zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun copyAndroidTestAssetToCache(assetName: String): Uri {
        val file = File(targetContext.cacheDir, "${UUID.randomUUID()}_reader_test_book.epub")
        currentEpubFile = file

        instrumentationContext.assets.open(assetName).use { inputStream ->
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return FileProvider.getUriForFile(
            targetContext,
            "${targetContext.packageName}.provider",
            file
        )
    }

    private fun navigateToFixtureSearchResult(query: String, expectedChapterIndex: Int) {
        clickReaderControl(text(R.string.tooltip_search))
        waitForTag("SearchTextField")

        composeTestRule.onNodeWithTag("SearchTextField").performTextInput(query)
        waitForTag("SearchResultItem_$expectedChapterIndex", timeoutMillis = 20_000)
        composeTestRule.onNodeWithTag("SearchResultItem_$expectedChapterIndex").performClick()
    }

    private fun waitForReader() {
        waitForTag("ReaderContainer", timeoutMillis = 30_000)
    }

    private fun showReaderChrome() {
        if (hasAnyReaderControl()) return

        composeTestRule.onRoot().performTouchInput { click(center) }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            hasAnyReaderControl()
        }
    }

    private fun clickReaderControl(contentDescription: String) {
        showReaderChrome()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            hasContentDescription(contentDescription)
        }
        composeTestRule.onAllNodesWithContentDescription(contentDescription)[0].performClick()
    }

    private fun clickContentDescription(contentDescription: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            hasContentDescription(contentDescription)
        }
        composeTestRule.onAllNodesWithContentDescription(contentDescription)[0].performClick()
    }

    private fun clickText(value: String) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText(value)[0].performClick()
    }

    private fun openOverflowMenu() {
        clickReaderControl(text(R.string.content_desc_more_options))
    }

    private fun hasAnyReaderControl(): Boolean {
        return hasContentDescription(text(R.string.tooltip_search)) ||
            hasContentDescription(text(R.string.content_desc_chapters_menu)) ||
            hasContentDescription(text(R.string.content_desc_more_options))
    }

    private fun hasContentDescription(contentDescription: String): Boolean {
        return composeTestRule
            .onAllNodesWithContentDescription(contentDescription)
            .fetchSemanticsNodes()
            .isNotEmpty()
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(value: String, timeoutMillis: Long = 10_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeTestRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTextContaining(value: String, timeoutMillis: Long = 10_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeTestRule.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNoContentDescription(contentDescription: String, timeoutMillis: Long = 5_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeTestRule
                .onAllNodesWithContentDescription(contentDescription)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun waitForRenderMode(expected: RenderMode) {
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            targetContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
                .getString("render_mode", RenderMode.VERTICAL_SCROLL.name) == expected.name
        }
    }

    private fun assertReaderSettingEventually(
        prefsName: String,
        key: String,
        expected: Boolean
    ) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            targetContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .getBoolean(key, !expected) == expected
        }
    }

    private fun readFixtureRecentFile(): RecentFileEntity? {
        return runBlocking {
            AppDatabase.getDatabase(targetContext)
                .recentFileDao()
                .getFileByBookId(fixtureBookId)
        }
    }

    private fun waitForRecentFile(
        timeoutMillis: Long = 10_000,
        predicate: (RecentFileEntity?) -> Boolean
    ) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            predicate(readFixtureRecentFile())
        }
    }

    private fun waitForFixtureBookmarks(
        timeoutMillis: Long = 10_000,
        predicate: (String?) -> Boolean
    ) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            predicate(readFixtureRecentFile()?.bookmarks)
        }
    }

    private fun waitForFixtureHighlights(
        timeoutMillis: Long = 10_000,
        predicate: (String?) -> Boolean
    ) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            predicate(readFixtureRecentFile()?.highlights)
        }
    }

    private fun parseBookmarksJson(rawJson: String?): Set<Bookmark> {
        return EpubAnnotationSerializer.parseBookmarksJson(rawJson)
    }
}
