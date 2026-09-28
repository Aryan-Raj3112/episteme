package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedMobileAiUsageBadgeTest {

    @Test
    fun `badge mirrors android result-based rule`() {
        // Android (AiResultContentView): cache hit, cost present, or loading.
        assertTrue(sharedAiUsageBadgeVisible(isCacheHit = true, cost = null, isLoading = false, hasText = true))
        assertTrue(sharedAiUsageBadgeVisible(isCacheHit = false, cost = 0.5, isLoading = false, hasText = true))
        assertTrue(sharedAiUsageBadgeVisible(isCacheHit = false, cost = null, isLoading = true, hasText = false))
        // No account/credits condition on either platform.
        assertFalse(sharedAiUsageBadgeVisible(isCacheHit = false, cost = null, isLoading = false, hasText = true))
        assertFalse(sharedAiUsageBadgeVisible(isCacheHit = false, cost = null, isLoading = false, hasText = false))
    }
}
