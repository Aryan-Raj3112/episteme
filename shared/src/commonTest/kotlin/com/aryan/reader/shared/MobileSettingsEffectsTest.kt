package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MobileSettingsEffectsTest {
    private val disabled = MobileSettingsMutationState(
        tabsEnabled = false,
        strictFileFilterEnabled = false,
        pdfFileNameAsDisplayName = false,
        folderSyncEnabled = false,
        hideReaderAi = false,
    )

    @Test
    fun portableTogglesInvertCurrentState() {
        assertEquals(
            MobileSettingsMutation.SetTabsEnabled(true),
            planMobileSettingsMutation(SharedSettingsAction.TABS_TOGGLE, disabled),
        )
        assertEquals(
            MobileSettingsMutation.SetPdfFileNameAsDisplayName(true),
            planMobileSettingsMutation(SharedSettingsAction.PDF_FILENAME_DISPLAY_NAME, disabled),
        )
        assertEquals(
            MobileSettingsMutation.SetFolderSyncEnabled(true),
            planMobileSettingsMutation(SharedSettingsAction.FOLDER_SYNC, disabled),
        )
        assertEquals(
            MobileSettingsMutation.SetHideReaderAi(true),
            planMobileSettingsMutation(SharedSettingsAction.HIDE_READER_AI, disabled),
        )
    }

    @Test
    fun strictFilterRequiresConfirmationOnlyWhenEnabling() {
        assertEquals(
            MobileSettingsMutation.ChangeStrictFileFilter(MobileStrictFileFilterEffect.CONFIRM_ENABLE),
            planMobileSettingsMutation(SharedSettingsAction.STRICT_FILE_FILTER, disabled),
        )
        assertEquals(
            MobileSettingsMutation.ChangeStrictFileFilter(MobileStrictFileFilterEffect.DISABLE),
            planMobileSettingsMutation(
                SharedSettingsAction.STRICT_FILE_FILTER,
                disabled.copy(strictFileFilterEnabled = true),
            ),
        )
    }

    @Test
    fun platformAndNavigationActionsDoNotProducePortableMutations() {
        assertNull(planMobileSettingsMutation(SharedSettingsAction.APP_THEME, disabled))
        assertNull(planMobileSettingsMutation(SharedSettingsAction.SIGN_IN, disabled))
        assertNull(planMobileSettingsMutation(SharedSettingsAction.TEXT_READER_DEFAULTS, disabled))
    }
}
