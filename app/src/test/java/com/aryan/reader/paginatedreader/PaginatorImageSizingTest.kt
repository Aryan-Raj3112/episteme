package com.aryan.reader.paginatedreader

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

class PaginatorImageSizingTest {
    @Test
    fun `unsized images use intrinsic CSS width and remain within the column`() {
        val density = Density(2f)

        assertEquals(120f, intrinsicImageWidthPx(60f, density, maxWidthPx = 800f))
        assertEquals(800f, intrinsicImageWidthPx(600f, density, maxWidthPx = 800f))
    }
}
