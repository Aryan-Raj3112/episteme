package com.aryan.reader.shared.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedMobileGridWidthTest {
    @Test
    fun `phones stay compact below 600dp`() {
        assertEquals(SharedAndroidHomeWidthClass.COMPACT, sharedMobileWidthClassForWidth(360.dp))
        assertEquals(SharedAndroidHomeWidthClass.COMPACT, sharedMobileWidthClassForWidth(599.dp))
    }

    @Test
    fun `medium band spans 600dp until 840dp`() {
        assertEquals(SharedAndroidHomeWidthClass.MEDIUM, sharedMobileWidthClassForWidth(600.dp))
        assertEquals(SharedAndroidHomeWidthClass.MEDIUM, sharedMobileWidthClassForWidth(839.dp))
    }

    @Test
    fun `expanded band starts at 840dp`() {
        assertEquals(SharedAndroidHomeWidthClass.EXPANDED, sharedMobileWidthClassForWidth(840.dp))
        assertEquals(SharedAndroidHomeWidthClass.EXPANDED, sharedMobileWidthClassForWidth(1280.dp))
    }

    @Test
    fun `compact always uses three columns regardless of width`() {
        assertEquals(3, SharedAndroidHomeWidthClass.COMPACT.bookGridColumns(320.dp))
        assertEquals(3, SharedAndroidHomeWidthClass.COMPACT.bookGridColumns(1200.dp))
    }

    @Test
    fun `larger screens gain columns which caps the card size`() {
        val mediumColumns = SharedAndroidHomeWidthClass.MEDIUM.bookGridColumns(768.dp)
        val expandedColumns = SharedAndroidHomeWidthClass.EXPANDED.bookGridColumns(1240.dp)
        assertTrue(mediumColumns > 3)
        assertTrue(expandedColumns > mediumColumns)
        // Cards stay near the minimum cell size instead of stretching.
        val expandedCardWidth = 1240.dp / expandedColumns
        assertTrue(expandedCardWidth < 220.dp)
    }

    @Test
    fun `lazy cells match the Android benchmark mapping`() {
        assertEquals(GridCells.Fixed(3), SharedAndroidHomeWidthClass.COMPACT.bookGridCells())
        assertEquals(
            GridCells.Adaptive(140.dp),
            SharedAndroidHomeWidthClass.MEDIUM.bookGridCells(),
        )
        assertEquals(
            GridCells.Adaptive(160.dp),
            SharedAndroidHomeWidthClass.EXPANDED.bookGridCells(),
        )
    }
}
