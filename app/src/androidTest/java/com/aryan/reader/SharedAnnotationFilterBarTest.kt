package com.aryan.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.ui.SharedAndroidPdfHighlightStrings
import com.aryan.reader.shared.ui.SharedAndroidPdfHighlightsList
import com.aryan.reader.shared.ui.SharedMobileEpubHighlights
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the annotation drawer's colour filter on both reader surfaces that render it: the shared
 * EPUB highlight list and the shared PDF highlight list Android injects its model into.
 *
 * The colour strip is the part that cannot be asserted in a unit test — it only exists when the file
 * uses more than one colour, and the swatch is the only control that carries the colour identity.
 */
@RunWith(AndroidJUnit4::class)
class SharedAnnotationFilterBarTest {
    @get:Rule val composeTestRule = createComposeRule()

    private fun epubHighlight(
        id: String,
        color: HighlightColor,
        note: String? = null,
    ) = UserHighlight(
        id = id,
        cfi = "/2/4/6:$id",
        text = "TEXT_$id",
        color = color,
        chapterIndex = 0,
        note = note,
    )

    private fun setEpubList(highlights: List<UserHighlight>) {
        composeTestRule.setContent {
            MaterialTheme {
                SharedMobileEpubHighlights(
                    highlights = highlights,
                    chapterTitleOf = { "Chapter 1" },
                    palette = ReaderHighlightPalette(),
                    onHighlightClick = {},
                    onHighlightEdit = {},
                    onHighlightColorChange = { _, _ -> },
                    onDeleteHighlight = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    /**
     * The drawer is fed Android's live `SnapshotStateList`, which keeps its identity across in-place
     * edits. A projection memoised on that identity used to go stale, so a highlight added or a note
     * edited while the drawer was open only showed up after the book was closed and reopened.
     */
    @Test
    fun epubDrawerReflectsInPlaceMutationsOfTheLiveList() {
        val live = mutableStateListOf(epubHighlight("first", HighlightColor.YELLOW))
        setEpubList(live)

        composeTestRule.onNodeWithText("TEXT_first").assertIsDisplayed()

        composeTestRule.runOnIdle {
            live.add(epubHighlight("second", HighlightColor.BLUE))
        }
        composeTestRule.onNodeWithText("TEXT_second").assertIsDisplayed()

        composeTestRule.runOnIdle {
            live[0] = live[0].copy(note = "fresh note")
        }
        composeTestRule.onNodeWithText("fresh note").assertIsDisplayed()
    }

    @Test
    fun epubDrawerFiltersByColourSwatch() {
        setEpubList(
            listOf(
                epubHighlight("yellow", HighlightColor.YELLOW),
                epubHighlight("blue", HighlightColor.BLUE),
            )
        )

        composeTestRule.onNodeWithText("TEXT_yellow").assertIsDisplayed()
        composeTestRule.onNodeWithText("TEXT_blue").assertIsDisplayed()

        composeTestRule.onNodeWithTag("AnnotationColorFilter_blue").performClick()
        composeTestRule.onNodeWithText("TEXT_blue").assertIsDisplayed()
        composeTestRule.onNodeWithText("TEXT_yellow").assertDoesNotExist()

        // Tapping the selected swatch is the way back to every colour.
        composeTestRule.onNodeWithTag("AnnotationColorFilter_blue").performClick()
        composeTestRule.onNodeWithText("TEXT_yellow").assertIsDisplayed()
    }

    @Test
    fun epubDrawerCombinesNotesAndColourAndAllResetsBoth() {
        setEpubList(
            listOf(
                epubHighlight("yellow_note", HighlightColor.YELLOW, note = "a note"),
                epubHighlight("yellow_plain", HighlightColor.YELLOW),
                epubHighlight("blue_note", HighlightColor.BLUE, note = "a note"),
            )
        )

        composeTestRule.onNodeWithText("With Notes").performClick()
        composeTestRule.onNodeWithText("TEXT_yellow_plain").assertDoesNotExist()

        composeTestRule.onNodeWithTag("AnnotationColorFilter_blue").performClick()
        composeTestRule.onNodeWithText("TEXT_blue_note").assertIsDisplayed()
        composeTestRule.onNodeWithText("TEXT_yellow_note").assertDoesNotExist()

        // All is the reset for both dimensions, not just the notes toggle.
        composeTestRule.onNodeWithText("All").performClick()
        composeTestRule.onNodeWithText("TEXT_yellow_plain").assertIsDisplayed()
        composeTestRule.onNodeWithText("TEXT_blue_note").assertIsDisplayed()
    }

    @Test
    fun epubDrawerExplainsFilteredEmptyStateAndClearsIt() {
        setEpubList(
            listOf(
                epubHighlight("yellow", HighlightColor.YELLOW),
                epubHighlight("blue", HighlightColor.BLUE),
            )
        )

        composeTestRule.onNodeWithText("With Notes").performClick()

        // A file with highlights and no notes must not claim it has no highlights.
        composeTestRule.onNodeWithText("No annotations match these filters").assertIsDisplayed()
        composeTestRule.onNodeWithText("TEXT_yellow").assertDoesNotExist()

        composeTestRule.onNodeWithText("Clear filters").performClick()
        composeTestRule.onNodeWithText("TEXT_yellow").assertIsDisplayed()
    }

    @Test
    fun pdfDrawerFiltersByColourArgb() {
        val yellowArgb = HighlightColor.YELLOW.color.copy(alpha = 0.55f)
        val blueArgb = HighlightColor.BLUE.color.copy(alpha = 0.55f)
        val rows = listOf(
            PdfFilterRow(id = "row_yellow", text = "PDF_YELLOW", colorArgb = yellowArgb),
            PdfFilterRow(id = "row_blue", text = "PDF_BLUE", colorArgb = blueArgb),
        )
        composeTestRule.setContent {
            MaterialTheme {
                SharedAndroidPdfHighlightsList(
                    highlights = rows,
                    strings = SharedAndroidPdfHighlightStrings(
                        empty = "No highlights yet.",
                        allFilter = "All",
                        withNotesFilter = "With Notes",
                        noMatchingFilters = "No annotations match these filters",
                        clearFilters = "Clear filters",
                        defaultHighlight = "Highlighted section",
                        optionsDescription = "Options",
                        addNoteAction = "Add Note",
                        editNoteAction = "Edit Note",
                        deleteAction = "Delete",
                        cancelAction = "Cancel",
                        deleteDialogTitle = "Delete highlight?",
                        deleteDialogDescription = "Delete this highlight?",
                    ),
                    key = { it.id },
                    sortKey = { it.pageIndex },
                    text = { it.text },
                    note = { it.note },
                    pageLabel = { "Page ${it.pageIndex + 1}" },
                    color = { it.colorArgb },
                    // The stored opacity differs from the palette's opaque colour; the filter still
                    // has to recognise the hue as one swatch.
                    colorArgb = { it.colorArgb.toArgb() },
                    onNavigateToHighlight = {},
                    onNoteRequested = {},
                    onDeleteHighlight = {},
                )
            }
        }

        composeTestRule.onNodeWithText("PDF_YELLOW").assertIsDisplayed()

        composeTestRule.onNodeWithTag("AnnotationColorFilter_yellow").performClick()
        composeTestRule.onNodeWithText("PDF_YELLOW").assertIsDisplayed()
        composeTestRule.onNodeWithText("PDF_BLUE").assertDoesNotExist()
    }

    private data class PdfFilterRow(
        val id: String,
        val text: String,
        val colorArgb: Color,
        val pageIndex: Int = 0,
        val note: String? = null,
    )
}
