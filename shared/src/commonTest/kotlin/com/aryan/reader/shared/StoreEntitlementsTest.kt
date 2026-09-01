package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoreEntitlementsTest {
    @Test
    fun `effective pro is union of legacy google and apple sources`() {
        assertTrue(StoreEntitlementSnapshot(legacyIsPro = true).isPro)
        assertTrue(
            StoreEntitlementSnapshot(
                activeProSources = setOf(ProEntitlementSource.GOOGLE_PLAY_LIFETIME),
            ).isPro
        )
        assertTrue(
            StoreEntitlementSnapshot(
                activeProSources = setOf(ProEntitlementSource.APP_STORE_LIFETIME),
            ).isPro
        )
        assertFalse(StoreEntitlementSnapshot().isPro)
    }

    @Test
    fun `removing apple source does not erase google or legacy pro`() {
        val afterAppleRevocation = StoreEntitlementSnapshot(
            activeProSources = setOf(ProEntitlementSource.GOOGLE_PLAY_LIFETIME),
            legacyIsPro = true,
        )

        assertTrue(afterAppleRevocation.isPro)
    }

    @Test
    fun `credits never project below zero`() {
        assertEquals(0, StoreEntitlementSnapshot(credits = -5).safeCredits)
        assertEquals(300, StoreEntitlementSnapshot(credits = 300).safeCredits)
    }

    @Test
    fun `ios purchase gating is provider agnostic`() {
        assertEquals(
            IosPurchaseAvailability.SIGN_IN_REQUIRED,
            iosPurchaseAvailability(isSignedIn = false, isPro = false, purchasingPro = true),
        )
        assertEquals(
            IosPurchaseAvailability.PRO_ALREADY_ACTIVE,
            iosPurchaseAvailability(isSignedIn = true, isPro = true, purchasingPro = true),
        )
        assertEquals(
            IosPurchaseAvailability.AVAILABLE,
            iosPurchaseAvailability(isSignedIn = true, isPro = false, purchasingPro = true),
        )
        assertEquals(
            IosPurchaseAvailability.AVAILABLE,
            iosPurchaseAvailability(isSignedIn = true, isPro = true, purchasingPro = false),
        )
    }
}
