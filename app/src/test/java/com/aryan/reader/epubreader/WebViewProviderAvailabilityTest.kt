package com.aryan.reader.epubreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewProviderAvailabilityTest {

    @Test
    fun `missing provider package reports unavailable`() {
        assertFalse(isWebViewProviderAvailable { null })
    }

    @Test
    fun `provider query exception reports unavailable`() {
        assertFalse(
            isWebViewProviderAvailable {
                throw RuntimeException("MissingWebViewPackageException")
            }
        )
    }

    @Test
    fun `provider query error reports unavailable`() {
        assertFalse(
            isWebViewProviderAvailable {
                throw UnsatisfiedLinkError("webview native library failed to load")
            }
        )
    }

    @Test
    fun `present provider package reports available`() {
        assertTrue(isWebViewProviderAvailable { Any() })
    }
}
