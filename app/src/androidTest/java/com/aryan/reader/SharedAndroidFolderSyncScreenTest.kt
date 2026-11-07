package com.aryan.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.SyncedFolder
import com.aryan.reader.shared.ui.SharedAndroidFolderSyncScreen
import com.aryan.reader.shared.ui.SharedAndroidFolderSyncStrings
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedAndroidFolderSyncScreenTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun disableKeepDataPreservesAndroidCallbackShape() {
        var enabled: Boolean? = null
        var removeData: Boolean? = null
        composeTestRule.setContent {
            MaterialTheme {
                SharedAndroidFolderSyncScreen(
                    folders = listOf(SyncedFolder("folder://one", "Books", 0L, setOf(FileType.EPUB), true)),
                    statsByFolderUri = emptyMap(),
                    syncableFileTypes = listOf(FileType.EPUB),
                    isLoading = false,
                    strings = strings(),
                    onAddFolder = {},
                    onRemoveFolder = {},
                    onLocalSyncChange = { _, nextEnabled, nextRemoveData ->
                        enabled = nextEnabled
                        removeData = nextRemoveData
                    },
                    onFileTypesChange = { _, _ -> },
                    onScanAll = {},
                    onSyncMetadata = {},
                    formatLastScan = { "formatted" },
                    syncIcon = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Options").performClick()
        composeTestRule.onNodeWithText("Disable local sync").performClick()
        composeTestRule.onNodeWithText("Disable and keep data").performClick()
        composeTestRule.runOnIdle {
            assertThat(enabled).isFalse()
            assertThat(removeData).isFalse()
        }
    }

    @Test
    fun addFolderFabSitsOnTheSectionBottomEdge() {
        composeTestRule.setContent {
            MaterialTheme {
                SharedAndroidFolderSyncScreen(
                    folders = listOf(SyncedFolder("folder://one", "Books", 0L, setOf(FileType.EPUB), true)),
                    statsByFolderUri = emptyMap(),
                    syncableFileTypes = listOf(FileType.EPUB),
                    isLoading = false,
                    strings = strings(),
                    onAddFolder = {},
                    onRemoveFolder = {},
                    onLocalSyncChange = { _, _, _ -> },
                    onFileTypesChange = { _, _ -> },
                    onScanAll = {},
                    onSyncMetadata = {},
                    formatLastScan = { "formatted" },
                    syncIcon = {},
                )
            }
        }

        val rootBottom = composeTestRule.onRoot().getBoundsInRoot().bottom
        val fabBottom = composeTestRule
            .onNodeWithTag("UnifiedLibraryAddFolder")
            .assertIsDisplayed()
            .getBoundsInRoot()
            .bottom
        // The host Scaffold owns window insets: this section keeps only the shared 16.dp
        // edge spacing so its FAB lines up with the FABs on the other tabs.
        assertThat(fabBottom.value).isWithin(1f).of((rootBottom - 16.dp).value)
    }

    private fun strings() = SharedAndroidFolderSyncStrings(
        addFolder = "Add folder", addDescription = "Add", scanning = "Scanning", scanAll = "Scan all",
        syncMetadata = "Sync meta", emptyTitle = "Folders", emptyMessage = "Connect folders",
        selectFolder = "Select folder", localSyncDisabled = "Disabled", optionsDescription = "Options",
        editFilters = "Edit filters", disableLocalSync = "Disable local sync", enableLocalSync = "Enable local sync",
        removeFolder = "Remove folder", lastSync = "Last sync", never = "Never", booksCount = "Books",
        filterCount = { type, count -> "${type.name}: $count" }, filterFileTypes = "Filter file types",
        filterFileTypesDescription = "Choose types", save = "Save", cancel = "Cancel",
        disableDialogTitle = "Disable local sync?", disableDialogDescription = "Choose what to do",
        disableRemoveData = "Disable and remove data", disableKeepData = "Disable and keep data",
    )
}
