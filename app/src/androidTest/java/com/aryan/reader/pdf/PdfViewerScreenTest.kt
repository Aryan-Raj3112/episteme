package com.aryan.reader.pdf

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.aryan.reader.MainActivity
import com.aryan.reader.R
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PdfViewerScreenTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var currentPdfFile: File? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setup() {
        context.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val samplePdfUri = copyAssetToCache(context, "sample.pdf")
        scenario = ActivityScenario.launch<MainActivity>(createPdfViewIntent(context, samplePdfUri))
    }

    @After
    fun tearDown() {
        scenario?.close()
        currentPdfFile?.let {
            if (it.exists()) it.delete()
        }
    }

    private fun text(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    private fun assertNoNodeWithTag(tag: String) {
        assertThat(composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes()).isEmpty()
    }

    private fun createPdfViewIntent(context: Context, uri: Uri): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun waitForDocumentLoad(pageText: String = text(R.string.page_of_pages, 1, 4)) {
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText(pageText)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openMoreOptions() {
        composeTestRule.onNodeWithContentDescription(text(R.string.tooltip_more_options)).performClick()
    }

    private fun openNavigationDrawer() {
        composeTestRule.onNodeWithTag("TocButton").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(text(R.string.tab_chapters)).fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag("BookmarksTab").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun selectChaptersTabIfTabsPaneIsFirst() {
        if (composeTestRule.onAllNodesWithTag("TabsTab").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText(text(R.string.tab_chapters)).performClick()
            composeTestRule.waitForIdle()
        }
    }

    private fun selectBookmarksTab() {
        composeTestRule.onNodeWithTag("BookmarksTab").performClick()
        composeTestRule.waitForIdle()
    }

    private fun selectReadingMode(modeText: String) {
        openMoreOptions()
        composeTestRule.onNodeWithText(text(R.string.menu_change_reading_mode)).performClick()
        composeTestRule.onNodeWithText(modeText).performClick()
        composeTestRule.waitForIdle()
    }

    private fun ensurePaginationMode() {
        selectReadingMode(text(R.string.menu_reading_mode_paginated))
    }

    @Suppress("SameParameterValue")
    private fun copyAssetToCache(context: Context, assetName: String): Uri {
        val uniqueName = "${UUID.randomUUID()}_$assetName"
        val file = File(context.cacheDir, uniqueName)

        currentPdfFile = file

        if (file.exists()) file.delete()
        context.assets.open(assetName).use { inputStream ->
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }

    @Test
    fun documentLoadsAndDisplaysCorrectPageCount() {
        waitForDocumentLoad()
        composeTestRule.onNodeWithTag("PageNumberIndicator")
            .assertIsDisplayed()
    }

    @Test
    fun tableOfContentsButton_handlesTabsPaneAndOpensChaptersTab() {
        waitForDocumentLoad()

        openNavigationDrawer()
        selectChaptersTabIfTabsPaneIsFirst()

        composeTestRule.onNodeWithText(text(R.string.tab_chapters)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("BookmarksTab").assertIsDisplayed()
    }

    @Test
    fun bookmarkFunctionality_addAndDeleteCurrentPage() {
        waitForDocumentLoad()

        openMoreOptions()
        composeTestRule.onNodeWithText(text(R.string.menu_bookmark_this_page)).performClick()
        composeTestRule.waitForIdle()

        openNavigationDrawer()
        selectBookmarksTab()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTag("BookmarkItem_0").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("BookmarkItem_0").assertIsDisplayed()
            .assert(hasText(text(R.string.pdf_page_short, 1), substring = true))

        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_more_options_bookmark)).performClick()
        composeTestRule.onNodeWithText(text(R.string.action_delete)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(text(R.string.action_delete), useUnmergedTree = true).performClick()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTag("BookmarkItem_0").fetchSemanticsNodes().isEmpty()
        }
        assertNoNodeWithTag("BookmarkItem_0")
        composeTestRule.onNodeWithText(text(R.string.no_bookmarks_yet)).assertIsDisplayed()
    }

    @Test
    fun sliderNavigation_opensAndDisplaysCorrectly() {
        waitForDocumentLoad()

        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_navigate_slider)).performClick()

        composeTestRule.onNodeWithText(text(R.string.page_format, 1, 4)).assertIsDisplayed()
    }

    @Test
    fun displayMode_switchesToVerticalScroll() {
        waitForDocumentLoad()

        ensurePaginationMode()

        assertNoNodeWithTag("PdfVerticalScroll")

        selectReadingMode(text(R.string.menu_reading_mode_vertical))

        composeTestRule.onNodeWithTag("PdfVerticalScroll").assertIsDisplayed()

        ensurePaginationMode()

        assertNoNodeWithTag("PdfVerticalScroll")
    }

    @Test
    fun displayModeSelectionPersistsReaderPreference() {
        waitForDocumentLoad()

        selectReadingMode(text(R.string.menu_reading_mode_paginated))
        waitForDisplayModePreference(DisplayMode.PAGINATION)

        selectReadingMode(text(R.string.menu_reading_mode_vertical))
        waitForDisplayModePreference(DisplayMode.VERTICAL_SCROLL)
    }

    @Test
    fun search_uiOpensAndAcceptsQuery() {
        waitForDocumentLoad()

        composeTestRule.onNodeWithTag("SearchButton").performClick()
        composeTestRule.waitForIdle()

        val ocrLanguageText = text(R.string.ocr_language_latin)
        if (composeTestRule.onAllNodesWithText(ocrLanguageText).fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText(ocrLanguageText).performClick()
            composeTestRule.waitForIdle()
        }

        composeTestRule.onNodeWithTag("SearchTextField").assertIsDisplayed()

        composeTestRule.onNodeWithTag("SearchTextField").performTextInput("test query")

        composeTestRule.onNodeWithTag("SearchTextField").assertTextContains("test query")

        composeTestRule.onNodeWithContentDescription(text(R.string.tooltip_close_search)).performClick()

        assertNoNodeWithTag("SearchTextField")
    }

    private fun waitForDisplayModePreference(expected: DisplayMode) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(DISPLAY_MODE_KEY, DisplayMode.VERTICAL_SCROLL.name) == expected.name
        }
    }

}
