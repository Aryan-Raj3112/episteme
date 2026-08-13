package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.opds.SharedOpdsScreenState
import com.aryan.reader.shared.ui.SharedOpdsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedOpdsScreenTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun mobileCatalogListOwnsAddCatalogFabAndDialog() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedOpdsScreen(
                    state = SharedOpdsScreenState(),
                    localLibraryBooks = emptyList(),
                    onOpenCatalog = {},
                    onOpenFeedUrl = {},
                    onNavigateBack = {},
                    onSearch = {},
                    onLoadNextPage = {},
                    onAddCatalog = { _, _, _, _ -> },
                    onUpdateCatalog = { _, _, _, _, _ -> },
                    onRemoveCatalog = {},
                    onDeleteCatalogStreams = {},
                    onDownloadBook = { _, _ -> },
                    onReadBook = {},
                    onStreamBook = { _, _ -> },
                    onClearError = {},
                    mobileLayout = true,
                )
            }
        }

        composeTestRule.onNodeWithText("Add catalog").performClick()
        composeTestRule.onNodeWithText("Add OPDS catalog").assertExists()
    }
}
