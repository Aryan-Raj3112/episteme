package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.PdfReaderTool
import com.aryan.reader.shared.PdfToolbarPreferences
import com.aryan.reader.shared.ui.SharedPdfToolbarCustomizationDialog
import com.aryan.reader.shared.ui.SharedPdfToolbarCustomizationLabels
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPdfToolbarCustomizationDialogTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun resetEmitsDefaultsSanitizedForAvailableFlavorTools() {
        val availableTools = PdfReaderTool.entries.toSet() - PdfReaderTool.OCR_LANGUAGE
        var hidden: Set<String>? = null
        var order: List<PdfReaderTool>? = null
        var bottom: Set<String>? = null

        composeTestRule.setContent {
            MaterialTheme {
                SharedPdfToolbarCustomizationDialog(
                    hiddenToolIds = setOf(PdfReaderTool.SEARCH.id),
                    toolOrder = PdfReaderTool.entries.reversed(),
                    bottomToolIds = setOf(PdfReaderTool.THEME.id),
                    availableTools = availableTools,
                    labels = labels(),
                    toolTitle = { it.title },
                    onHiddenToolsUpdate = { hidden = it },
                    onToolOrderUpdate = { order = it },
                    onBottomToolsUpdate = { bottom = it },
                    onDismiss = {},
                    toolIcon = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Reset PDF").performClick()
        composeTestRule.runOnIdle {
            val defaults = PdfToolbarPreferences().sanitized(availableTools)
            assertThat(hidden).isEqualTo(defaults.hiddenToolIds)
            assertThat(order).isEqualTo(defaults.toolOrder)
            assertThat(bottom).isEqualTo(defaults.bottomToolIds)
            assertThat(order).doesNotContain(PdfReaderTool.OCR_LANGUAGE)
        }
    }

    private fun labels() = SharedPdfToolbarCustomizationLabels(
        title = "PDF toolbar",
        reset = "Reset PDF",
        close = "Close PDF",
        topBar = "Top PDF",
        bottomBar = "Bottom PDF",
        hiddenTools = "Hidden PDF",
        dropToolsHere = "Drop PDF",
        moreMenu = "More PDF",
    )
}
