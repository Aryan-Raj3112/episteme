package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountLinkingTest {
    @Test
    fun `apple account with value requires server merge into existing google account`() {
        val apple = AccountMergeSnapshot(
            uid = "apple-uid",
            providers = setOf(AccountAuthProvider.APPLE),
            isPro = true,
            credits = 100,
            hasCloudData = false,
        )
        val google = AccountMergeSnapshot(
            uid = "google-uid",
            providers = setOf(AccountAuthProvider.GOOGLE),
            isPro = true,
            credits = 20,
            hasCloudData = true,
        )

        val plan = planGoogleAccountLink(apple, google)

        assertEquals(AccountLinkResolution.REQUIRE_SERVER_MERGE, plan.resolution)
        assertEquals("google-uid", plan.destinationUid)
        assertEquals("apple-uid", plan.sourceUid)
        assertTrue(plan.preservePro)
        assertEquals(100, plan.creditsToTransfer)
    }

    @Test
    fun `empty apple account can switch to existing google account`() {
        val plan = planGoogleAccountLink(
            current = AccountMergeSnapshot(
                uid = "temporary",
                providers = setOf(AccountAuthProvider.APPLE),
                isPro = false,
                credits = 0,
                hasCloudData = false,
            ),
            googleCredentialOwner = AccountMergeSnapshot(
                uid = "existing",
                providers = setOf(AccountAuthProvider.GOOGLE),
                isPro = true,
                credits = 0,
                hasCloudData = true,
            ),
        )

        assertEquals(AccountLinkResolution.SWITCH_TO_EXISTING, plan.resolution)
        assertEquals("existing", plan.destinationUid)
        assertTrue(plan.preservePro)
    }

    @Test
    fun `sync strictly requires google provider and drive permission`() {
        assertFalse(canEnableGoogleDriveSync(setOf(AccountAuthProvider.APPLE), true))
        assertFalse(canEnableGoogleDriveSync(setOf(AccountAuthProvider.GOOGLE), false))
        assertTrue(canEnableGoogleDriveSync(setOf(AccountAuthProvider.APPLE, AccountAuthProvider.GOOGLE), true))
    }

    @Test
    fun `cloud sync additionally requires pro while other pro features accept apple`() {
        assertFalse(
            canUseCloudSync(
                providers = setOf(AccountAuthProvider.GOOGLE),
                hasGoogleDrivePermission = true,
                isProUser = false,
            )
        )
        assertTrue(
            canUseCloudSync(
                providers = setOf(AccountAuthProvider.GOOGLE),
                hasGoogleDrivePermission = true,
                isProUser = true,
            )
        )
        assertTrue(canUseProFeature(isSignedIn = true, isProUser = true))
        assertFalse(canUseProFeature(isSignedIn = false, isProUser = true))
    }
}
