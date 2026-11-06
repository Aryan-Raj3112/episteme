package com.aryan.reader.pdf

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard for the reported Pdfium crash: `PdfiumCoreU` throws
 * `RuntimeException("Failed to initialize PdfiumCore native libraries")` with an
 * `UnsatisfiedLinkError` cause when `libpdfium.so` cannot be resolved from the app's own library
 * directory (missing/stale ABI split on an App Bundle install, or a linker namespace that refuses
 * the platform fallback). It used to escape from composition because the core was a bare `by lazy`.
 *
 * A JVM unit test cannot load Pdfium's native libraries either, so these run the REAL failure path
 * rather than mocking it: `PdfiumCoreKt(Dispatchers.Default)` fails on `System.loadLibrary`. The
 * provider has to latch that and let callers degrade. See docs/crashlytics-triage.md#90.
 */
class PdfiumCoreUnavailableTest {

    @Test
    fun `coreOrNull degrades instead of throwing when the native libraries are missing`() {
        assertNull(PdfiumCoreProvider.coreOrNull)
        assertTrue(PdfiumCoreProvider.isUnavailable)
    }

    @Test
    fun `the failure is latched so repeated access cannot throw or re-log`() {
        assertNull(PdfiumCoreProvider.coreOrNull)
        assertNull(PdfiumCoreProvider.coreOrNull)
        assertTrue(PdfiumCoreProvider.isUnavailable)
    }

    @Test
    fun `core still reports the real load failure to callers that already catch it`() {
        val error = runCatching { PdfiumCoreProvider.core }.exceptionOrNull()

        assertTrue("core must keep failing while the libraries are unavailable", error != null)
        assertTrue(
            "the original LinkageError has to stay in the cause chain",
            generateSequence(error) { it.cause }.any { it is LinkageError }
        )
    }
}
