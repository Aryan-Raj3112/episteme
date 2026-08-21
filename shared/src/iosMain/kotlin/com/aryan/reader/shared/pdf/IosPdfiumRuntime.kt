@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.pdfium.c.FPDF_InitLibrary
import platform.Foundation.NSLock
import kotlinx.coroutines.sync.Mutex

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
}
