package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.ui.SharedMobileEpubAnnotationLabels
import com.aryan.reader.shared.ui.SharedMobileEpubAnnotationSheet
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the shared annotation sheet both hosts now render.
 *
 * The point is the seam, not the pixels: the sheet owns the note draft and reports colour, style and
 * note back through callbacks, and each host wires those to its own store. It is also the only thing
 * that exercises the four vector drawables the tool row loads on Android, which is how a
 * resource-parsing regressions shows up as an app crash rather than a test.
 */
@RunWith(AndroidJUnit4::class)
class SharedMobileEpubAnnotationSheetTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val highlight = UserHighlight(
        id = "highlight-1",
        cfi = "/2/4/6:0",
        text = "The owl and the pussy-cat",
        color = HighlightColor.YELLOW,
        chapterIndex = 0,
    )

    @Test
    fun sheetReportsNoteColourAndStyleThroughHostCallbacks() {
        var savedNote: String? = null
        var chosenArgb: Int? = null
        var chosenStyle: HighlightStyle? = null
        var deleted = false

        composeTestRule.setContent {
            MaterialTheme {
                SharedMobileEpubAnnotationSheet(
                    highlight = highlight,
                    palette = ReaderHighlightPalette(),
                    effectiveBg = MaterialTheme.colorScheme.surface,
                    effectiveText = MaterialTheme.colorScheme.onSurface,
                    labels = SharedMobileEpubAnnotationLabels(
                        copy = "Copy",
                        speak = "Speak",
                        dictionary = "Dict",
                        translate = "Translate",
                        search = "Search",
                        notePlaceholder = "Add a note…",
                        delete = "Delete",
                        saveNote = "Save Note",
                    ),
                    onColorChange = { chosenArgb = it },
                    onStyleChange = { chosenStyle = it },
                    onCopy = {},
                    onDictionary = {},
                    onTranslate = {},
                    onSearch = {},
                    onDismiss = {},
                    onSave = { savedNote = it },
                    onDelete = { deleted = true },
                )
            }
        }

        composeTestRule.onNodeWithText("\"${highlight.text}\"").assertIsDisplayed()
        // The four Android-benchmark tool entries, by label: this is the row that has to stay
        // identical on both hosts.
        listOf("Copy", "Dict", "Translate", "Search").forEach { label ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag("HighlightColor_yellow").performClick()
        composeTestRule.onNodeWithContentDescription(HighlightStyle.UNDERLINE.id).performClick()
        composeTestRule.onNodeWithText("Save Note").assertIsDisplayed()

        composeTestRule.runOnIdle {
            assertThat(chosenArgb).isEqualTo(HighlightColor.YELLOW.color.toArgb())
            assertThat(chosenStyle).isEqualTo(HighlightStyle.UNDERLINE)
        }

        composeTestRule.onNodeWithText("Add a note…").performTextInput("A note")
        composeTestRule.onNodeWithText("Save Note").performClick()
        composeTestRule.onNodeWithText("Delete").performClick()

        composeTestRule.runOnIdle {
            assertThat(savedNote).isEqualTo("A note")
            assertThat(deleted).isTrue()
        }
    }
}
