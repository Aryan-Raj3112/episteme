package com.aryan.reader

import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLegalLinksTest {
    @Test
    fun `android oss flavor maps to oss legal pages`() {
        val links = legalLinksForAndroidFlavor("oss")

        assertTrue(links.privacyPolicyUrl.endsWith("/oss-privacy-policy.html"))
        assertTrue(links.termsUrl.endsWith("/oss-terms-of-service.html"))
    }

    @Test
    fun `android pro flavor maps to standard legal pages`() {
        val links = legalLinksForAndroidFlavor("pro")

        assertTrue(links.privacyPolicyUrl.endsWith("/privacy-policy.html"))
        assertTrue(links.termsUrl.endsWith("/terms-and-conditions.html"))
    }
}
