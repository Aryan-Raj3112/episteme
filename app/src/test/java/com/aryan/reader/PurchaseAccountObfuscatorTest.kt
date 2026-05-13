package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseAccountObfuscatorTest {
    @Test
    fun `obfuscated account id is stable and safe for billing`() {
        val accountId = PurchaseAccountObfuscator.obfuscatedAccountId("firebase-user-123")

        assertTrue(accountId.startsWith("firebase_"))
        assertFalse(accountId.contains("="))
        assertEquals(accountId, PurchaseAccountObfuscator.obfuscatedAccountId("firebase-user-123"))
    }

    @Test
    fun `purchase token hash matches worker format`() {
        val token = "jjjnbecgjekfeigbnagcheee.AO-J1OzOurukZmfLAyu6EdPlEvLIyehyOLYajYbGlEK3knhjN4nGe-BLgjXVrSCfRFocGJ5Wc8VcLazRLZxHTdgiUQ5zULRyoQ"

        assertEquals(
            "sha256_KBe2Ev9nqOx9PMypxP3AlwDgm4E-KIa-i5Eenr1QPF8",
            PurchaseAccountObfuscator.purchaseTokenHash(token)
        )
    }
}
