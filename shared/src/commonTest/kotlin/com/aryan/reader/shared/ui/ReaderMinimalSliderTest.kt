package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderMinimalSliderTest {
    @Test
    fun markerFractionMapsValueIntoRange() {
        assertEquals(
            0.5f,
            readerMinimalSliderMarkerFraction(
                markerValue = 5f,
                valueRange = 0f..10f
            )
        )
    }

    @Test
    fun markerFractionClampsOutsideRange() {
        assertEquals(
            0f,
            readerMinimalSliderMarkerFraction(
                markerValue = -4f,
                valueRange = 1f..9f
            )
        )
        assertEquals(
            1f,
            readerMinimalSliderMarkerFraction(
                markerValue = 12f,
                valueRange = 1f..9f
            )
        )
    }

    @Test
    fun markerFractionIsAbsentForMissingOrEmptyRange() {
        assertNull(readerMinimalSliderMarkerFraction(null, 0f..10f))
        assertNull(readerMinimalSliderMarkerFraction(4f, 5f..5f))
    }
}
