package com.aryan.reader.pdf

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.aryan.reader.MainActivity
import com.aryan.reader.savePdfRightToLeftPagination
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Two-page spread order under right-to-left pagination.
 *
 * Android's EPUB pager mirrors the visible spread for right-to-left reading
 * (`EpubPageSpread.visibleBookPagesForDisplay`), and the desktop PDF reader does
 * the same. The Android PDF pager only flipped the page-turn direction, so the
 * two pages of a spread kept their left-to-right order while the pager advanced
 * right-to-left. This asserts the on-screen order end to end: page 1's number
 * overlay sits right of page 2's when reading right-to-left, and left of it
 * otherwise.
 *
 * sample.pdf has four pages, so the first two-page spread is pages 1 and 2 and
 * each page renders its own "n/4" overlay (enabled by default).
 */
@RunWith(AndroidJUnit4::class)
class PdfSpreadRtlOrderTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var currentPdfFile: File? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setup() {
        context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        saveDisplayMode(context, DisplayMode.PAGINATION)
        savePdfPageSpreadMode(context, ReaderPageSpreadMode.TWO_PAGE)
        savePdfFirstPageStandaloneInSpread(context, false)
    }

    @After
    fun tearDown() {
        scenario?.close()
        currentPdfFile?.let {
            if (it.exists()) it.delete()
        }
    }

    @Test
    fun rtlTwoPageSpread_placesEarlierPageOnTheRight() {
        launchReader(rightToLeft = true)

        val firstPage = pageNumberOverlayBounds(1)
        val secondPage = pageNumberOverlayBounds(2)

        assertThat(firstPage.left).isGreaterThan(secondPage.left)
    }

    @Test
    fun ltrTwoPageSpread_placesEarlierPageOnTheLeft() {
        launchReader(rightToLeft = false)

        val firstPage = pageNumberOverlayBounds(1)
        val secondPage = pageNumberOverlayBounds(2)

        assertThat(firstPage.left).isLessThan(secondPage.left)
    }

    private fun launchReader(rightToLeft: Boolean) {
        savePdfRightToLeftPagination(context, rightToLeft)

        val pdfFile = copyAssetToShareableCache(context, context, "sample.pdf")
        currentPdfFile = pdfFile
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = shareableCacheUri(context, pdfFile)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        scenario = ActivityScenario.launch(intent)
    }

    private fun pageNumberOverlayBounds(pageNumber: Int): Rect {
        val label = "$pageNumber/4"
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule
                .onAllNodesWithText(label, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        return composeTestRule
            .onNodeWithText(label, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
    }
}
