package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderTtsOverlayModelsTest {
    @Test
    fun `overlay choices match Android tri state layout`() {
        assertEquals(
            listOf(ReaderTtsOverlaySize.MEDIUM, ReaderTtsOverlaySize.SMALL),
            readerTtsOverlayAlternativeSizes(ReaderTtsOverlaySize.LARGE),
        )
        assertEquals(
            listOf(ReaderTtsOverlaySize.LARGE, ReaderTtsOverlaySize.SMALL),
            readerTtsOverlayAlternativeSizes(ReaderTtsOverlaySize.MEDIUM),
        )
        assertEquals(
            listOf(ReaderTtsOverlaySize.LARGE, ReaderTtsOverlaySize.MEDIUM),
            readerTtsOverlayAlternativeSizes(ReaderTtsOverlaySize.SMALL),
        )
    }

    @Test
    fun `compact overlay is trailing while expanded states stay leading`() {
        assertEquals(0f, readerTtsOverlayAlignmentBias(ReaderTtsOverlaySize.LARGE))
        assertEquals(0f, readerTtsOverlayAlignmentBias(ReaderTtsOverlaySize.MEDIUM))
        assertEquals(1f, readerTtsOverlayAlignmentBias(ReaderTtsOverlaySize.SMALL))
    }

    @Test
    fun `stored overlay values default safely`() {
        assertEquals(ReaderTtsOverlaySize.MEDIUM, resolveReaderTtsOverlaySize("MEDIUM"))
        assertEquals(ReaderTtsOverlaySize.LARGE, resolveReaderTtsOverlaySize(null))
        assertEquals(ReaderTtsOverlaySize.LARGE, resolveReaderTtsOverlaySize("FULL"))
    }
}
