package com.aryan.reader.shared

data class MobileSettingsMutationState(
    val tabsEnabled: Boolean,
    val strictFileFilterEnabled: Boolean,
    val pdfFileNameAsDisplayName: Boolean,
    val folderSyncEnabled: Boolean,
)

enum class MobileStrictFileFilterEffect {
    DISABLE,
    CONFIRM_ENABLE,
}

sealed interface MobileSettingsMutation {
    data class SetTabsEnabled(val enabled: Boolean) : MobileSettingsMutation
    data class ChangeStrictFileFilter(val effect: MobileStrictFileFilterEffect) : MobileSettingsMutation
    data class SetPdfFileNameAsDisplayName(val enabled: Boolean) : MobileSettingsMutation
    data class SetFolderSyncEnabled(val enabled: Boolean) : MobileSettingsMutation
}

/**
 * Produces portable setting mutations while each host retains persistence,
 * dialogs, resources, and native effects.
 */
fun planMobileSettingsMutation(
    action: SharedSettingsAction,
    state: MobileSettingsMutationState,
): MobileSettingsMutation? = when (action) {
    SharedSettingsAction.TABS_TOGGLE ->
        MobileSettingsMutation.SetTabsEnabled(!state.tabsEnabled)
    SharedSettingsAction.STRICT_FILE_FILTER -> MobileSettingsMutation.ChangeStrictFileFilter(
        if (state.strictFileFilterEnabled) {
            MobileStrictFileFilterEffect.DISABLE
        } else {
            MobileStrictFileFilterEffect.CONFIRM_ENABLE
        }
    )
    SharedSettingsAction.PDF_FILENAME_DISPLAY_NAME ->
        MobileSettingsMutation.SetPdfFileNameAsDisplayName(!state.pdfFileNameAsDisplayName)
    SharedSettingsAction.FOLDER_SYNC ->
        MobileSettingsMutation.SetFolderSyncEnabled(!state.folderSyncEnabled)
    else -> null
}
