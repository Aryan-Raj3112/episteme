package com.aryan.reader.shared.ui

import com.aryan.reader.shared.AccountAuthProvider
import com.aryan.reader.shared.UserData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MobileAccountPresentationTest {
    @Test
    fun `provider-aware sign-in copy names both iOS providers`() {
        assertEquals(
            "Sign in with Apple or Google",
            mobileAccountSignInLabel(setOf(AccountAuthProvider.APPLE, AccountAuthProvider.GOOGLE)),
        )
        assertEquals(
            "Sign in with Google",
            mobileAccountSignInLabel(setOf(AccountAuthProvider.GOOGLE)),
        )
    }

    @Test
    fun `auth reset clears identity providers and account entitlements`() {
        val signedIn = MobileAccountPresentation(
            currentUser = UserData("uid", "Reader", "https://example.test/photo", "reader@example.test"),
            providers = setOf(AccountAuthProvider.APPLE, AccountAuthProvider.GOOGLE),
            isProUser = true,
            credits = 300,
            legalDisclosure = MobileAccountLegalDisclosure("agreement"),
        )

        val reset = signedIn.resetAuthentication()

        assertFalse(reset.isSignedIn)
        assertNull(reset.currentUser)
        assertTrue(reset.providers.isEmpty())
        assertFalse(reset.isProUser)
        assertEquals(0, reset.credits)
        assertEquals(signedIn.legalDisclosure, reset.legalDisclosure)
    }

    @Test
    fun `legal disclosure keeps link segments for host callbacks`() {
        val disclosure = MobileAccountLegalDisclosure(
            text = "By signing in, you agree to our Terms and read our Privacy Policy.",
            termsLabel = "Terms",
            privacyLabel = "Privacy Policy",
        )

        assertTrue(disclosure.text.contains(disclosure.termsLabel!!))
        assertTrue(disclosure.text.contains(disclosure.privacyLabel!!))
    }
}
