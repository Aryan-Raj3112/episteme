package com.aryan.reader.shared

import com.aryan.reader.shared.ui.mobileAccountSignInLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Documents the intentional temporary iOS launch scope: Google sign-in,
 * cloud sync, credits purchase, and cloud TTS are hidden from iOS UI while
 * their logic stays intact. Flip [IosFeatureGating] flags to restore.
 * Android remains the benchmark and is unaffected by these flags.
 */
class IosFeatureGatingTest {

    @Test
    fun `temporary iOS scope hides google cloud sync credits and cloud tts`() {
        assertFalse(IosFeatureGating.SHOW_GOOGLE_SIGN_IN)
        assertFalse(IosFeatureGating.SHOW_CLOUD_SYNC)
        assertFalse(IosFeatureGating.SHOW_CREDITS_PURCHASE)
        assertFalse(IosFeatureGating.SHOW_CLOUD_TTS)
    }

    @Test
    fun `hidden cloud sync inputs remove sync rows but keep account entry`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.IOS,
                isSignedIn = true,
                isProUser = true,
                // Mirrors the iOS caller while IosFeatureGating hides sync.
                syncAvailable = IosFeatureGating.SHOW_CLOUD_SYNC,
                folderSyncAvailable = IosFeatureGating.SHOW_CLOUD_SYNC,
            )
        )
        val actions = model.rootCategories.flatMap { category ->
            model.page(category.destination).items.map { it.action }
        }

        assertFalse(SharedSettingsAction.CLOUD_SYNC in actions)
        assertFalse(SharedSettingsAction.FOLDER_SYNC in actions)
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
