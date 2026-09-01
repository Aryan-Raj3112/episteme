package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderMotionPolicyAndroidTest {
    @Test
    fun `disabled animator scale enables reduced motion`() {
        assertTrue(androidReaderMotionPolicy(animatorScaleEnabled = false).reduceMotion)
    }

    @Test
    fun `enabled animator scale preserves normal motion`() {
        assertFalse(androidReaderMotionPolicy(animatorScaleEnabled = true).reduceMotion)
    }
}
