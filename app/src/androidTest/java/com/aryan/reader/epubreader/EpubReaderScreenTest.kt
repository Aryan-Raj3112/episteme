package com.aryan.reader.epubreader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
class EpubReaderScreenTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val targetContext: Context = ApplicationProvider.getApplicationContext()
    private val instrumentationContext: Context = InstrumentationRegistry.getInstrumentation().context
    private var currentEpubFile: File? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setup() {
        clearReaderPrefs()

        val fixtureUri = copyAndroidTestAssetToCache("epub/reader_test_book.epub")
        scenario = ActivityScenario.launch<MainActivity>(createEpubViewIntent(fixtureUri))
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
        waitForReader()
        showReaderChrome()

        waitForText("Reader Android UI Test Book")
        composeTestRule.onNodeWithText("Reader Android UI Test Book").assertIsDisplayed()
        assertThat(hasContentDescription(text(R.string.tooltip_search))).isTrue()
        assertThat(hasContentDescription(text(R.string.content_desc_chapters_menu))).isTrue()
    }

    @Test
    fun fixtureEpub_drawerShowsFixtureChapters() {
        waitForReader()

        clickReaderControl(text(R.string.content_desc_chapters_menu))

        waitForText(text(R.string.tab_chapters))
        waitForTextContaining("Chapter One")
        waitForTextContaining("Chapter Two")
        waitForTextContaining("Chapter Three")
    }

    @Test
    fun fixtureEpub_searchFindsUniqueFixtureMarker() {
        waitForReader()

        clickReaderControl(text(R.string.tooltip_search))
        waitForTag("SearchTextField")

        composeTestRule.onNodeWithTag("SearchTextField").performTextInput("SEARCH_TARGET_DELTA")
        composeTestRule.onNodeWithTag("SearchTextField").assertTextContains("SEARCH_TARGET_DELTA")

        waitForTag("SearchResultItem_1", timeoutMillis = 20_000)
        composeTestRule.onNodeWithTag("SearchResultItem_1").assertIsDisplayed()
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
}
