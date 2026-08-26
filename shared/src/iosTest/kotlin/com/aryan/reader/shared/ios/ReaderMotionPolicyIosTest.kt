package com.aryan.reader.shared.ios

import com.aryan.reader.shared.ui.iosReaderMotionPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderMotionPolicyIosTest {
    @Test
    fun `reduce motion accessibility flag disables transitions`() {
        assertTrue(iosReaderMotionPolicy(reduceMotionEnabled = true).reduceMotion)
    }

    @Test
    fun `normal accessibility setting preserves transitions`() {
        assertFalse(iosReaderMotionPolicy(reduceMotionEnabled = false).reduceMotion)
    }
}
