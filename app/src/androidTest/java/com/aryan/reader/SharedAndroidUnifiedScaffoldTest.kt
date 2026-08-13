package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.MobileUnifiedLibrarySection
import com.aryan.reader.shared.ui.SharedAndroidUnifiedScaffold
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedAndroidUnifiedScaffoldTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun homeImportFabIsOwnedBySharedScaffold() {
        var importClicks = 0
        setScaffold(
            section = MobileUnifiedLibrarySection.HOME,
            showingShelf = false,
            onImport = { importClicks++ },
        )

        composeTestRule.onNodeWithTag("UnifiedLibraryImport").performClick()
        composeTestRule.runOnIdle { assertThat(importClicks).isEqualTo(1) }
    }

    @Test
    fun shelfFabIsHiddenInsideSelectedShelf() {
        setScaffold(
            section = MobileUnifiedLibrarySection.SHELVES,
            showingShelf = true,
        )

        composeTestRule.onAllNodesWithTag("UnifiedLibraryNewShelf").assertCountEquals(0)
    }

    private fun setScaffold(
        section: MobileUnifiedLibrarySection,
        showingShelf: Boolean,
        onImport: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                SharedAndroidUnifiedScaffold(
                    section = section,
                    showingShelf = showingShelf,
                    importDescription = "Import",
                    addAudiobookDescription = "Add audiobook",
                    newShelfLabel = "New shelf",
                    onImport = onImport,
                    onAddAudiobook = {},
                    onNewShelf = {},
                    topBar = {},
                    bottomBar = {},
                    sectionContent = { _, _ -> },
                )
            }
        }
    }
}
