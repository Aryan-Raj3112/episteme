package com.aryan.reader.shared.reader

enum class MobileEpubReaderBackAction {
    CLOSE_DRAWER,
    STOP_AUTO_SCROLL,
    CLOSE_SEARCH,
    SAVE_AND_EXIT,
}

fun selectMobileEpubReaderBackAction(
    drawerOpen: Boolean,
    autoScrollActive: Boolean,
    searchActive: Boolean,
): MobileEpubReaderBackAction = when {
    drawerOpen -> MobileEpubReaderBackAction.CLOSE_DRAWER
    autoScrollActive -> MobileEpubReaderBackAction.STOP_AUTO_SCROLL
    searchActive -> MobileEpubReaderBackAction.CLOSE_SEARCH
    else -> MobileEpubReaderBackAction.SAVE_AND_EXIT
}

enum class MobilePdfReaderBackAction {
    EXIT_PASSWORD_PROMPT,
    CLOSE_VISUAL_OPTIONS,
    CLOSE_REINDEX_DIALOG,
    STOP_AUTO_SCROLL,
    CLOSE_DRAWER,
    STOP_RICH_TEXT_EDITING,
    CLOSE_AI_HUB,
    CLOSE_PERMISSION_RATIONALE,
    CLOSE_SUMMARIZATION_UPSELL,
    CLOSE_AI_DEFINITION,
    CLOSE_DICTIONARY_UPSELL,
    CLOSE_TOOL_CUSTOMIZATION,
    CLOSE_SEARCH,
    CLOSE_TTS_SETTINGS,
    CLOSE_TTS_REPLACEMENTS,
    CLOSE_THEME_PANEL,
    SAVE_AND_EXIT,
}

data class MobilePdfReaderBackState(
    val passwordPromptVisible: Boolean = false,
    val visualOptionsVisible: Boolean = false,
    val reindexDialogVisible: Boolean = false,
    val autoScrollActive: Boolean = false,
    val drawerOpen: Boolean = false,
    val richTextEditing: Boolean = false,
    val aiHubVisible: Boolean = false,
    val permissionRationaleVisible: Boolean = false,
    val summarizationUpsellVisible: Boolean = false,
    val aiDefinitionVisible: Boolean = false,
    val dictionaryUpsellVisible: Boolean = false,
    val toolCustomizationVisible: Boolean = false,
    val searchActive: Boolean = false,
    val ttsSettingsVisible: Boolean = false,
    val ttsReplacementsVisible: Boolean = false,
    val themePanelVisible: Boolean = false,
)

fun selectMobilePdfReaderBackAction(state: MobilePdfReaderBackState): MobilePdfReaderBackAction = with(state) {
    when {
        passwordPromptVisible -> MobilePdfReaderBackAction.EXIT_PASSWORD_PROMPT
        visualOptionsVisible -> MobilePdfReaderBackAction.CLOSE_VISUAL_OPTIONS
        reindexDialogVisible -> MobilePdfReaderBackAction.CLOSE_REINDEX_DIALOG
        autoScrollActive -> MobilePdfReaderBackAction.STOP_AUTO_SCROLL
        drawerOpen -> MobilePdfReaderBackAction.CLOSE_DRAWER
        richTextEditing -> MobilePdfReaderBackAction.STOP_RICH_TEXT_EDITING
        aiHubVisible -> MobilePdfReaderBackAction.CLOSE_AI_HUB
        permissionRationaleVisible -> MobilePdfReaderBackAction.CLOSE_PERMISSION_RATIONALE
        summarizationUpsellVisible -> MobilePdfReaderBackAction.CLOSE_SUMMARIZATION_UPSELL
        aiDefinitionVisible -> MobilePdfReaderBackAction.CLOSE_AI_DEFINITION
        dictionaryUpsellVisible -> MobilePdfReaderBackAction.CLOSE_DICTIONARY_UPSELL
        toolCustomizationVisible -> MobilePdfReaderBackAction.CLOSE_TOOL_CUSTOMIZATION
        searchActive -> MobilePdfReaderBackAction.CLOSE_SEARCH
        ttsSettingsVisible -> MobilePdfReaderBackAction.CLOSE_TTS_SETTINGS
        ttsReplacementsVisible -> MobilePdfReaderBackAction.CLOSE_TTS_REPLACEMENTS
        themePanelVisible -> MobilePdfReaderBackAction.CLOSE_THEME_PANEL
        else -> MobilePdfReaderBackAction.SAVE_AND_EXIT
    }
}
