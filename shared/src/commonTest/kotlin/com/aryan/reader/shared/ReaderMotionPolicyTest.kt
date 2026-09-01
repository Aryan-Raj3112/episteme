package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderMotionPolicyTest {
    @Test
    fun `reduced motion disables requested transitions and smooth scrolling`() {
        val policy = ReaderMotionPolicy(reduceMotion = true)

        assertFalse(policy.animationsEnabled)
        assertFalse(policy.shouldAnimate(requested = true))
        assertTrue(policy.shouldAnimate(requested = false).not())
        assertEquals(0, policy.durationMillis(700))
        assertEquals("auto", policy.webViewScrollBehavior())
    }

    @Test
    fun `normal motion preserves user animation preference and smooth scrolling`() {
        val policy = ReaderMotionPolicy(reduceMotion = false)

        assertTrue(policy.animationsEnabled)
        assertTrue(policy.shouldAnimate(requested = true))
        assertFalse(policy.shouldAnimate(requested = false))
        assertEquals(700, policy.durationMillis(700))
        assertEquals("smooth", policy.webViewScrollBehavior())
        assertEquals("auto", policy.webViewScrollBehavior(requestedSmooth = false))
    }
}
