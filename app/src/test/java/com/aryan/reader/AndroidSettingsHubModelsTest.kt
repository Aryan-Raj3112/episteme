package com.aryan.reader

import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsHubModel
import com.aryan.reader.shared.SharedSettingsItemModel
import com.aryan.reader.shared.sharedSettingsHubModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSettingsHubModelsTest {

    @Test
    fun `offline android settings hide network backed sections`() {
        val model = sharedSettingsHubModel(
            androidSettingsHubInput(
                uiState = ReaderScreenState(),
                isOssBuild = true,
                isOfflineBuild = true,
                isDebugBuild = false
            )
        )
        val actions = model.visibleNestedActions()

        assertFalse(SharedSettingsAction.AI_SETTINGS in actions)
        assertFalse(SharedSettingsAction.CLOUD_SYNC in actions)
        assertFalse(SharedSettingsAction.HELP_FEEDBACK in actions)
        assertTrue(SharedSettingsAction.TTS_SETTINGS in actions)
        assertTrue(SharedSettingsAction.CUSTOM_FONTS in actions)
    }

    @Test
    fun `android settings expose debug-only storage actions only in debug`() {
        val releaseActions = sharedSettingsHubModel(
            androidSettingsHubInput(
                uiState = ReaderScreenState(),
                isOssBuild = false,
                isOfflineBuild = false,
                isDebugBuild = false
            )
        ).visibleNestedActions()
        val debugActions = sharedSettingsHubModel(
            androidSettingsHubInput(
                uiState = ReaderScreenState(),
                isOssBuild = false,
                isOfflineBuild = false,
                isDebugBuild = true
            )
        ).visibleNestedActions()

        assertFalse(SharedSettingsAction.EXPORT_LOGS in releaseActions)
        assertTrue(SharedSettingsAction.EXPORT_LOGS in debugActions)
    }

    @Test
    fun `android settings reflect global toggles from reader state`() {
        val model = sharedSettingsHubModel(
            androidSettingsHubInput(
                uiState = ReaderScreenState(
                    isTabsEnabled = true,
                    useStrictFileFilter = true,
                    isScreenCaptureProtectionEnabled = true
                ),
                isOssBuild = false,
                isOfflineBuild = false,
                isDebugBuild = false
            )
        )
        val toggles = model.visibleNestedItems().associateBy { it.action }

        assertTrue(toggles.getValue(SharedSettingsAction.TABS_TOGGLE).checked == true)
        assertTrue(toggles.getValue(SharedSettingsAction.STRICT_FILE_FILTER).checked == true)
        assertTrue(toggles.getValue(SharedSettingsAction.SCREEN_CAPTURE_PROTECTION).checked == true)
    }

    @Test
    fun `cloud sync row is gated by pro state`() {
        val freeSync = sharedSettingsHubModel(
            androidSettingsHubInput(
                uiState = ReaderScreenState(isProUser = false),
                isOssBuild = false,
                isOfflineBuild = false,
                isDebugBuild = false
            )
        ).visibleNestedItems().single { it.action == SharedSettingsAction.CLOUD_SYNC }
        val proSync = sharedSettingsHubModel(
            androidSettingsHubInput(
                uiState = ReaderScreenState(isProUser = true),
                isOssBuild = false,
                isOfflineBuild = false,
                isDebugBuild = false
            )
        ).visibleNestedItems().single { it.action == SharedSettingsAction.CLOUD_SYNC }

        assertFalse(freeSync.enabled)
        assertTrue(proSync.enabled)
    }
}

private fun SharedSettingsHubModel.visibleNestedItems(): List<SharedSettingsItemModel> {
    return rootCategories.flatMap { category ->
        page(category.destination).items
    }
}

private fun SharedSettingsHubModel.visibleNestedActions(): List<SharedSettingsAction> {
    return visibleNestedItems().map { it.action }
}
