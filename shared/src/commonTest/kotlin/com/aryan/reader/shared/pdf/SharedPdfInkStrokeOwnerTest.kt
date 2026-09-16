package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedPdfInkStrokeOwnerTest {

    @Test
    fun `free stroke list is claimed by any page`() {
        assertEquals(3, sharedPdfResolveInkStrokeOwner(currentOwnerPdfPage = null, pageIndex = 3))
        assertEquals(0, sharedPdfResolveInkStrokeOwner(currentOwnerPdfPage = null, pageIndex = 0))
    }

    @Test
    fun `owning page keeps ownership while its stroke runs`() {
        assertEquals(2, sharedPdfResolveInkStrokeOwner(currentOwnerPdfPage = 2, pageIndex = 2))
    }

    @Test
    fun `concurrent claim from another page is rejected`() {
        assertEquals(2, sharedPdfResolveInkStrokeOwner(currentOwnerPdfPage = 2, pageIndex = 5))
    }
}
