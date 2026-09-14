package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedNativeTableRowTest {
    @Test
    fun `empty row has no widths`() {
        assertEquals(
            emptyList(),
            sharedNativeTableCellWidthsPx(
                availableWidthPx = 100,
                fixedWidthsPx = emptyList(),
                weights = emptyList(),
            ),
        )
    }

    @Test
    fun `single weighted cell fills available width`() {
        assertEquals(
            listOf(360),
            sharedNativeTableCellWidthsPx(
                availableWidthPx = 360,
                fixedWidthsPx = listOf(null),
                weights = listOf(1f),
            ),
        )
    }

    @Test
    fun `two equal weights split equally`() {
        assertEquals(
            listOf(100, 100),
            sharedNativeTableCellWidthsPx(
                availableWidthPx = 200,
                fixedWidthsPx = listOf(null, null),
                weights = listOf(1f, 1f),
            ),
        )
    }

    @Test
    fun `weights follow colspan`() {
        assertEquals(
            listOf(100, 200),
            sharedNativeTableCellWidthsPx(
                availableWidthPx = 300,
                fixedWidthsPx = listOf(null, null),
                weights = listOf(1f, 2f),
            ),
        )
    }

    @Test
    fun `fixed cells keep width and remainder goes to weighted`() {
        assertEquals(
            listOf(80, 120),
            sharedNativeTableCellWidthsPx(
                availableWidthPx = 200,
                fixedWidthsPx = listOf(80, null),
                weights = listOf(0f, 3f),
            ),
        )
    }

    @Test
    fun `rounding remainder is absorbed by last weighted cell`() {
        val widths = sharedNativeTableCellWidthsPx(
            availableWidthPx = 100,
            fixedWidthsPx = listOf(null, null, null),
            weights = listOf(1f, 1f, 1f),
        )
        assertEquals(100, widths.sum())
        assertTrue(widths.all { it >= 33 })
    }

    @Test
    fun `fixed overflow leaves zero for weighted cells`() {
        assertEquals(
            listOf(150, 0),
            sharedNativeTableCellWidthsPx(
                availableWidthPx = 100,
                fixedWidthsPx = listOf(150, null),
                weights = listOf(0f, 1f),
            ),
        )
    }

    @Test
    fun `non positive available width yields zeros`() {
        assertEquals(
            listOf(0, 0),
            sharedNativeTableCellWidthsPx(
                availableWidthPx = -10,
                fixedWidthsPx = listOf(null, null),
                weights = listOf(1f, 1f),
            ),
        )
    }
}
