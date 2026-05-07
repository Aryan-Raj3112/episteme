package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopComposeInteropTest {
    @Test
    fun `desktop enables Compose interop blending before app startup`() {
        withSystemProperty(ComposeInteropBlendingProperty, null) {
            configureComposeSwingInterop()

            assertEquals(ComposeInteropBlendingEnabled, System.getProperty(ComposeInteropBlendingProperty))
        }
    }

    @Test
    fun `desktop treats blank Compose interop blending value as unset`() {
        withSystemProperty(ComposeInteropBlendingProperty, " ") {
            configureComposeSwingInterop()

            assertEquals(ComposeInteropBlendingEnabled, System.getProperty(ComposeInteropBlendingProperty))
        }
    }

    @Test
    fun `desktop preserves explicit Compose interop blending override`() {
        withSystemProperty(ComposeInteropBlendingProperty, "false") {
            configureComposeSwingInterop()

            assertEquals("false", System.getProperty(ComposeInteropBlendingProperty))
        }
    }

    private fun withSystemProperty(
        key: String,
        value: String?,
        block: () -> Unit
    ) {
        val previous = System.getProperty(key)
        try {
            if (value == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, value)
            }
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, previous)
            }
        }
    }
}
