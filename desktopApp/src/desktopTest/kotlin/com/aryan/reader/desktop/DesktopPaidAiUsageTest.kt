package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPaidAiUsageTest {
    @Test
    fun `desktop paid AI usage applies an optimistic integer credit decrement`() {
        assertEquals(9, desktopCreditsAfterPaidAiUsage(currentCredits = 10, cost = 1.0))
        assertEquals(7, desktopCreditsAfterPaidAiUsage(currentCredits = 10, cost = 2.2))
        assertEquals(10, desktopCreditsAfterPaidAiUsage(currentCredits = 10, cost = 0.0))
        assertEquals(10, desktopCreditsAfterPaidAiUsage(currentCredits = 10, cost = null))
        assertEquals(0, desktopCreditsAfterPaidAiUsage(currentCredits = 1, cost = 4.0))
    }
}
