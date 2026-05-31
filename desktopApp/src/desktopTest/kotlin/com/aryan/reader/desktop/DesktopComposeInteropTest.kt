package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopComposeInteropTest {
    @Test
    fun `desktop enables Compose interop blending before app startup`() {
        withSystemProperty(ComposeInteropBlendingProperty, null) {
            configureComposeSwingInterop(nonNativeWebViewPlatform)

            assertEquals(ComposeInteropBlendingEnabled, System.getProperty(ComposeInteropBlendingProperty))
        }
    }

    @Test
    fun `desktop treats blank Compose interop blending value as unset`() {
        withSystemProperty(ComposeInteropBlendingProperty, " ") {
            configureComposeSwingInterop(nonNativeWebViewPlatform)

            assertEquals(ComposeInteropBlendingEnabled, System.getProperty(ComposeInteropBlendingProperty))
        }
    }

    @Test
    fun `desktop preserves explicit Compose interop blending override`() {
        withSystemProperty(ComposeInteropBlendingProperty, "false") {
            configureComposeSwingInterop(nonNativeWebViewPlatform)

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

    private companion object {
        val nonNativeWebViewPlatform = DesktopPlatform(
            os = DesktopOperatingSystem.OTHER,
            architecture = DesktopArchitecture.X64
        )
    }
}
