package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class MobileReaderBackPolicyTest {
    @Test
    fun epubPreservesAndroidDrawerAutoScrollSearchPriority() {
        assertEquals(
            MobileEpubReaderBackAction.CLOSE_DRAWER,
            selectMobileEpubReaderBackAction(drawerOpen = true, autoScrollActive = true, searchActive = true),
        )
        assertEquals(
            MobileEpubReaderBackAction.STOP_AUTO_SCROLL,
            selectMobileEpubReaderBackAction(drawerOpen = false, autoScrollActive = true, searchActive = true),
        )
        assertEquals(
            MobileEpubReaderBackAction.CLOSE_SEARCH,
            selectMobileEpubReaderBackAction(drawerOpen = false, autoScrollActive = false, searchActive = true),
        )
        assertEquals(
            MobileEpubReaderBackAction.SAVE_AND_EXIT,
            selectMobileEpubReaderBackAction(drawerOpen = false, autoScrollActive = false, searchActive = false),
        )
    }

    @Test
    fun pdfPreservesAndroidPriorityAcrossEveryDismissibleLayer() {
        val orderedFlags = listOf<(MobilePdfReaderBackState) -> MobilePdfReaderBackState>(
            { it.copy(passwordPromptVisible = true) },
            { it.copy(visualOptionsVisible = true) },
            { it.copy(reindexDialogVisible = true) },
            { it.copy(autoScrollActive = true) },
            { it.copy(drawerOpen = true) },
            { it.copy(richTextEditing = true) },
            { it.copy(aiHubVisible = true) },
            { it.copy(permissionRationaleVisible = true) },
            { it.copy(summarizationUpsellVisible = true) },
            { it.copy(aiDefinitionVisible = true) },
            { it.copy(dictionaryUpsellVisible = true) },
            { it.copy(toolCustomizationVisible = true) },
            { it.copy(searchActive = true) },
            { it.copy(ttsSettingsVisible = true) },
            { it.copy(ttsReplacementsVisible = true) },
            { it.copy(themePanelVisible = true) },
        )
        val expected = MobilePdfReaderBackAction.entries.dropLast(1)
        expected.indices.forEach { activeIndex ->
            val state = orderedFlags.drop(activeIndex).fold(MobilePdfReaderBackState()) { value, enable -> enable(value) }
            assertEquals(expected[activeIndex], selectMobilePdfReaderBackAction(state))
        }
        assertEquals(MobilePdfReaderBackAction.SAVE_AND_EXIT, selectMobilePdfReaderBackAction(MobilePdfReaderBackState()))
    }
}
