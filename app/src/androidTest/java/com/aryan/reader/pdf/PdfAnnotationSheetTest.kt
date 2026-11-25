package com.aryan.reader.pdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.pdf.PdfHighlightColor
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the Android PDF annotation editor: the styling rows and the note field stay on screen
 * together, and Done flushes the draft through its callback. The note field sits outside the
 * scrolling body, so the keyboard can neither scroll it away nor grow it over the editor.
 */
@RunWith(AndroidJUnit4::class)
class PdfAnnotationSheetTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val highlight = PdfUserHighlight(
        id = "pdf-highlight-1",
        pageIndex = 3,
        bounds = emptyList(),
        color = PdfHighlightColor.YELLOW,
        text = "A highlighted sentence",
        range = 0 to 22,
        style = HighlightStyle.BACKGROUND,
    )

    @Test
    fun sheetKeepsTheEditorAroundTheNoteAndPersistsTheDraft() {
        val committedNote = AtomicReference<String?>(null)
        var dismissed = false

        composeTestRule.setContent {
            PdfAnnotationBottomSheet(
                highlight = highlight,
                effectiveBg = Color.White,
                effectiveText = Color.Black,
                onColorChange = {},
                onStyleChange = {},
                onDismiss = { dismissed = true },
                // The Android sheet commits the draft through `onSave` (the host treats it as
                // "persist", not "persist and close").
                onSave = { note, _ -> committedNote.set(note) },
                onUpdate = { _, _ -> },
                onDelete = {},
                onCopy = {},
                onDictionary = {},
                onTranslate = {},
                onSearch = {},
            )
        }

        // The styling rows and the quoted snippet are still there with the note field on screen.
        composeTestRule.onNodeWithContentDescription(HighlightStyle.UNDERLINE.id).assertIsDisplayed()
        composeTestRule.onNodeWithText("\"${highlight.text}\"").assertIsDisplayed()
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()

        composeTestRule.onNodeWithText("Add a note…").performTextInput("A PDF note")
        composeTestRule.onNodeWithText("A PDF note").assertIsDisplayed()

        // Done flushes the outstanding draft (the debounce is covered by the EPUB sheet test) and
        // then dismisses, which is how the host closes the editor.
        composeTestRule.onNodeWithText("Done").performClick()

        composeTestRule.runOnIdle {
            assertThat(committedNote.get()).isEqualTo("A PDF note")
            assertThat(dismissed).isTrue()
        }
    }
}
