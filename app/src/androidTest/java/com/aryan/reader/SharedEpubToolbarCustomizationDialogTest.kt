package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ui.SharedEpubToolbarCustomizationDialog
import com.aryan.reader.shared.ui.SharedEpubToolbarCustomizationLabels
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedEpubToolbarCustomizationDialogTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun resetEmitsExactSharedFirstRunPreferences() {
        var hidden: Set<String>? = null
        var order: List<ReaderTool>? = null
        var bottom: Set<String>? = null

        composeTestRule.setContent {
            MaterialTheme {
                SharedEpubToolbarCustomizationDialog(
                    hiddenToolIds = setOf(ReaderTool.SEARCH.id),
                    toolOrder = ReaderTool.entries.reversed(),
                    bottomToolIds = setOf(ReaderTool.THEME.id),
                    toolbarTools = toolbarTools,
                    availableTools = ReaderTool.entries.toSet(),
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

        composeTestRule.onNodeWithText("Reset now").performClick()
        composeTestRule.runOnIdle {
            val defaults = ReaderToolbarPreferences()
            assertThat(hidden).isEqualTo(defaults.hiddenToolIds)
            assertThat(order).isEqualTo(defaults.toolOrder)
            assertThat(bottom).isEqualTo(defaults.bottomToolIds)
        }
    }

    private val toolbarTools = setOf(
        ReaderTool.DICTIONARY,
        ReaderTool.THEME,
        ReaderTool.BRIGHTNESS,
        ReaderTool.SLIDER,
        ReaderTool.TOC,
        ReaderTool.FORMAT,
        ReaderTool.SEARCH,
        ReaderTool.AI_FEATURES,
        ReaderTool.TTS_CONTROLS,
        ReaderTool.SCREEN_ORIENTATION,
    )

    private fun labels() = SharedEpubToolbarCustomizationLabels(
        title = "Toolbar title",
        reset = "Reset now",
        close = "Close now",
        topBar = "Localized top",
        bottomBar = "Localized bottom",
        hiddenTools = "Localized hidden",
        dropToolsHere = "Localized empty",
        moreMenu = "Localized more",
    )
}
