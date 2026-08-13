package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.ui.SharedMobileLibraryFilterDialog
import com.aryan.reader.shared.ui.SharedMobileLibraryFilterLabels
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedLibraryFilterDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun selectionIsAppliedThroughAndroidCallback() {
        var applied: LibraryFilters? = null

        composeTestRule.setContent {
            MaterialTheme {
                SharedMobileLibraryFilterDialog(
                    filters = LibraryFilters(),
                    allTags = emptyList(),
                    syncedFolders = emptyList(),
                    readableFileTypes = setOf(FileType.PDF, FileType.EPUB),
                    fileTypeLabels = mapOf(FileType.PDF to "PDF", FileType.EPUB to "EPUB"),
                    readStatusLabels = ReadStatusFilter.entries.associateWith { it.name },
                    labels = SharedMobileLibraryFilterLabels(
                        title = "Filter library",
                        fileType = "File type",
                        sourceFolder = "Source folder",
                        inAppStorage = "In-app storage",
                        readStatus = "Reading status",
                        tags = "Tags",
                        clearAll = "Clear all",
                        apply = "Apply",
                    ),
                    onApply = { applied = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("PDF").performClick()
        composeTestRule.onNodeWithText("Apply").performClick()

        composeTestRule.runOnIdle {
            assertThat(applied?.fileTypes).containsExactly(FileType.PDF)
        }
    }
}
