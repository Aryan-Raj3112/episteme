package com.aryan.reader.shared

import com.aryan.reader.shared.ui.mobileAccountSignInLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * iOS launch scope. Google sign-in, Drive folder mirror, credits purchase, and
 * cloud TTS remain hidden; library cloud sync is visible on the pure-CloudKit
 * backend (Pro + iCloud only, no Google/Drive required). Android stays the
 * benchmark and is unaffected by these flags or the defaulted `requiresGoogle`
 * gate.
 */
class IosFeatureGatingTest {

    @Test
    fun `ios shows cloudkit library sync but hides google folder-drive-clear-credits-tts`() {
        assertFalse(IosFeatureGating.SHOW_GOOGLE_SIGN_IN)
        assertTrue(IosFeatureGating.SHOW_CLOUD_SYNC)
        // CloudKit authenticates with iCloud; Google/Drive must not gate sync.
        assertFalse(IosFeatureGating.REQUIRES_GOOGLE_DRIVE_FOR_SYNC)
        assertFalse(IosFeatureGating.SHOW_DRIVE_FOLDER_SYNC)
        assertFalse(IosFeatureGating.SHOW_CLOUD_DATA_CLEAR)
        assertFalse(IosFeatureGating.SHOW_CREDITS_PURCHASE)
        assertFalse(IosFeatureGating.SHOW_CLOUD_TTS)
    }

    @Test
    fun `cloudkit gate never requires google`() {
        // Pro, Apple-only, no Drive permission -> READY on CloudKit, blocked on Drive.
        assertTrue(
            canUseCloudSync(
                providers = setOf(AccountAuthProvider.APPLE),
                hasGoogleDrivePermission = false,
                isProUser = true,
                requiresGoogle = false,
            )
        )
        assertFalse(
            canUseCloudSync(
                providers = setOf(AccountAuthProvider.APPLE),
                hasGoogleDrivePermission = false,
                isProUser = true,
                requiresGoogle = true,
            )
        )
        // Non-Pro stays blocked regardless of backend.
        assertFalse(
            canUseCloudSync(
                providers = setOf(AccountAuthProvider.APPLE),
                hasGoogleDrivePermission = false,
                isProUser = false,
                requiresGoogle = false,
            )
        )
        // Android default (no explicit argument) must keep requiring Google.
        assertFalse(
            canUseCloudSync(
                providers = setOf(AccountAuthProvider.APPLE),
                hasGoogleDrivePermission = false,
                isProUser = true,
            )
        )
    }

    @Test
    fun `library sync row shows while drive folder and clear rows stay hidden`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.IOS,
                isDebugBuild = true,
                isSignedIn = true,
                isProUser = true,
                syncAvailable = IosFeatureGating.SHOW_CLOUD_SYNC,
                cloudSyncSetupIntent = resolveCloudSyncSetupIntent(
                    isProUser = true,
                    providers = setOf(AccountAuthProvider.APPLE),
                    hasGoogleDrivePermission = false,
                    requiresGoogle = IosFeatureGating.REQUIRES_GOOGLE_DRIVE_FOR_SYNC,
                ),
                folderSyncAvailable = IosFeatureGating.SHOW_DRIVE_FOLDER_SYNC,
                includeCloudLocalDataClear = IosFeatureGating.SHOW_CLOUD_DATA_CLEAR,
            )
        )
        val actions = model.rootCategories.flatMap { category ->
            model.page(category.destination).items.map { it.action }
        }

        assertTrue(SharedSettingsAction.CLOUD_SYNC in actions)
        assertFalse(SharedSettingsAction.FOLDER_SYNC in actions)
        assertFalse(SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA in actions)
        assertTrue(SharedSettingsAction.SIGN_OUT in actions)
    }

    @Test
    fun `apple-only provider set keeps an apple-only sign-in label`() {
        assertEquals(
            "Sign in with Apple",
            mobileAccountSignInLabel(setOf(AccountAuthProvider.APPLE)),
        )
    }
}

