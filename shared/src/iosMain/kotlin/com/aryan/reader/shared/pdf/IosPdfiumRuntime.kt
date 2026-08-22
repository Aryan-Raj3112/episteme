@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.pdfium.c.FPDF_InitLibrary
import platform.Foundation.NSLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * PDFium has process-wide initialization state. Keep initialization and document operations that
 * can be started concurrently by Compose (page rendering and outline loading) behind one lock.
 */
internal object IosPdfiumRuntime {
    val mutex = Mutex()
    private val initializationLock = NSLock()

    private var initialized = false

    fun ensureInitialized() {
        initializationLock.lock()
        try {
            if (!initialized) {
                FPDF_InitLibrary()
                initialized = true
            }
        } finally {
            initializationLock.unlock()
        }
    }

    /**
     * Runs exclusive PDFium work on a background dispatcher, mirroring Android where
     * `PdfiumCoreKt(Dispatchers.Default)` keeps rasterization off the UI thread while the
     * runtime mutex serializes access. Compose call sites (LaunchedEffect) run on Main, so
     * every direct `FPDF_*` caller must go through this helper.
     */
    suspend fun <T> withPdfium(block: suspend () -> T): T = withContext(Dispatchers.Default) {
        mutex.withLock {
            ensureInitialized()
            block()
        }
    }
}
