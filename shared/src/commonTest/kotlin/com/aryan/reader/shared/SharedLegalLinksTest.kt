package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedLegalLinksTest {
    @Test
    fun `standard and oss legal profiles use separate policy pages`() {
        val standard = sharedLegalLinksForProfile(SharedLegalProfile.STANDARD)
        val oss = sharedLegalLinksForProfile(SharedLegalProfile.OSS)

        assertEquals("$EPISTEME_POLICY_BASE_URL/privacy-policy.html", standard.privacyPolicyUrl)
        assertEquals("$EPISTEME_POLICY_BASE_URL/terms-and-conditions.html", standard.termsUrl)
        assertEquals("$EPISTEME_POLICY_BASE_URL/oss-privacy-policy.html", oss.privacyPolicyUrl)
        assertEquals("$EPISTEME_POLICY_BASE_URL/oss-terms-of-service.html", oss.termsUrl)
        assertEquals(standard.licensesUrl, oss.licensesUrl)
    }
}
