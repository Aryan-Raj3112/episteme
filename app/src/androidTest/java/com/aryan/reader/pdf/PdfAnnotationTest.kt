// PdfAnnotationTest.kt
package com.aryan.reader.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class PdfAnnotationTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var currentPdfFile: File? = null
    private var scenario: ActivityScenario<MainActivity>? = null
    private val samplePdfUri: Uri by lazy { copyAssetToCache(context, "sample.pdf") }
    private fun text(resId: Int): String = context.getString(resId)
    private fun dockTag(resId: Int): String = "DockItem_${text(resId)}"

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

    @Before
    fun setup() {
        // Clear settings to ensure fresh state for every test
        context.getSharedPreferences("annotation_settings_global", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("epub_reader_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()

        scenario = ActivityScenario.launch<MainActivity>(createPdfViewIntent(context, samplePdfUri))
        waitForDocumentLoad()
    }

    @After
    fun tearDown() {
        scenario?.close()
        currentPdfFile?.let { if (it.exists()) it.delete() }
    }

    private fun waitForDocumentLoad() {
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeTestRule.onNodeWithTag("PageNumberIndicator").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    private fun enterEditMode() {
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_toggle_editing_mode))
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_close_edit_mode)).assertIsDisplayed()
    }

    private fun tapOutsidePopup() {
        // Taps the center of the PDF viewer to dismiss popups
        composeTestRule.onNodeWithTag("PdfVerticalScroll").performTouchInput {
            click(center)
        }
        composeTestRule.waitForIdle()
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
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    // --- BASIC UI TESTS ---

    @Test
    fun testEnterAndExitEditMode() {
        enterEditMode()

        // Verify Dock Items exist using new Tags
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_pen)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_highlighter)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_eraser)).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_close_edit_mode)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_toggle_editing_mode)).assertIsDisplayed()
    }

    // --- TOOL LOGIC TESTS ---

    @Test
    fun testToolPersistence() {
        enterEditMode()

        // 1. Select Highlighter
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_highlighter)).performClick()
        composeTestRule.waitForIdle()

        // 2. Verify selection state
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_highlighter)).assertIsSelected()
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_pen)).assertIsNotSelected()

        // 3. Exit Edit Mode
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_close_edit_mode)).performClick()
        composeTestRule.waitForIdle()

        // 4. Re-enter Edit Mode
        enterEditMode()

        // 5. Verify Highlighter is STILL selected (Persistence)
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_highlighter)).assertIsSelected()
    }

    @Test
    fun testEraserSettingsPopupOpensWhenAlreadySelected() {
        enterEditMode()

        // Select Eraser
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_eraser)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_eraser)).assertIsSelected()

        // Click Eraser again to open its settings popup.
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_eraser)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ToolSettingsPopup").assertIsDisplayed()
    }

    // --- SETTINGS POPUP TESTS ---

    @Test
    fun testSettingsPopupInteractions() {
        enterEditMode()

        // 1. Pen is default. Click Pen ONCE to open Settings.
        // (Clicking twice would toggle it off, which caused previous failures)
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_pen)).performClick()
        composeTestRule.waitForIdle()

        // 2. Verify Popup Displayed
        composeTestRule.onNodeWithTag("ToolSettingsPopup").assertIsDisplayed()

        // 3. Verify Pen Types exist
        composeTestRule.onNodeWithTag("SettingsItem_FOUNTAIN_PEN").assertIsDisplayed()
        composeTestRule.onNodeWithTag("SettingsItem_MARKER").assertIsDisplayed()

        // 4. Switch internal Pen Type
        composeTestRule.onNodeWithTag("SettingsItem_PENCIL").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("SettingsItem_PENCIL").assertIsSelected()

        // 5. Dismiss Settings
        tapOutsidePopup()
        assertNoNodeWithTag("ToolSettingsPopup")
    }

    @Test
    fun testColorPaletteAndThickness() {
        enterEditMode()

        // Open Settings for Pen (Default selected, so one click opens settings)
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_pen)).performClick()
        composeTestRule.waitForIdle()

        // Test Palette Click (Index 1)
        composeTestRule.onNodeWithTag("Palette_Item_1").assertIsDisplayed().performClick()

        // Test Thickness Buttons
        composeTestRule.onNodeWithTag("Property_Plus").performClick()
        composeTestRule.onNodeWithTag("Property_Plus").performClick()
        composeTestRule.onNodeWithTag("Property_Minus").performClick()

        tapOutsidePopup()

        // Quick verification that settings didn't crash app
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_pen)).assertIsDisplayed()
    }

    // --- UNDO/REDO TESTS ---

    @Test
    fun testDrawingEnablesUndo() {
        enterEditMode()

        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_undo))
            .assertIsDisplayed()
            .assertIsNotEnabled()

        // Draw a single DOT stroke to ensure exactly one action is recorded
        composeTestRule.onNodeWithTag("PdfVerticalScroll").performTouchInput {
            click(center)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_undo)).assertIsEnabled()
    }

    @Test
    fun testUndoRedoLogic() {
        enterEditMode()

        // Draw 1 stroke (click = dot) to ensure stack size is exactly 1
        composeTestRule.onNodeWithTag("PdfVerticalScroll").performTouchInput {
            click(center)
        }
        composeTestRule.waitForIdle()

        val undoNode = composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_undo))
        val redoNode = composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_redo))

        undoNode.assertIsEnabled()
        redoNode.assertIsNotEnabled()

        // Perform Undo
        undoNode.performClick()
        composeTestRule.waitForIdle()

        undoNode.assertIsNotEnabled()
        redoNode.assertIsEnabled()

        // Perform Redo
        redoNode.performClick()
        composeTestRule.waitForIdle()

        undoNode.assertIsEnabled()
        redoNode.assertIsNotEnabled()
    }

    // --- DOCK INTERACTIONS TESTS ---

    @Test
    fun testDockMinimization() {
        enterEditMode()

        // 1. Drag Dock to make it floating (using Pen icon as handle)
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_pen)).performTouchInput {
            down(center)
            advanceEventTime(600) // Long press
            // Drag UP significantly
            moveBy(androidx.compose.ui.geometry.Offset(0f, -600f), delayMillis = 1000)
            up()
        }
        composeTestRule.waitForIdle()

        // 2. Minimize (Eye icon)
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_toggle_visibility)).performClick()
        composeTestRule.waitForIdle()

        // 3. Verify Dock items are hidden
        assertNoNodeWithTag(dockTag(R.string.content_desc_pen))

        // 4. Verify "Show Dock" floating button is visible
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_show_dock)).assertIsDisplayed()

        // 5. Restore
        composeTestRule.onNodeWithContentDescription(text(R.string.content_desc_show_dock)).performClick()
        composeTestRule.waitForIdle()

        // 6. Verify Dock items return
        composeTestRule.onNodeWithTag(dockTag(R.string.content_desc_pen)).assertIsDisplayed()
    }
}
