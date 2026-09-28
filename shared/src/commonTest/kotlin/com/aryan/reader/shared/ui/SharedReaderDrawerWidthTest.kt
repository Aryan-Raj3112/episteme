package com.aryan.reader.shared.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedReaderDrawerWidthTest {

    @Test
    fun `phone-width windows stay at the material benchmark fraction`() {
        // 0.86 fraction of a 393dp iPhone width, under the cap and above the floor.
        assertEquals(337.98.dp, sharedReaderDrawerSheetWidth(393.dp))
    }

    @Test
    fun `wide windows clamp instead of fighting the material cap`() {
        // The old fractional constraint conflicted with CMP's internal 360dp
        // sheet cap on landscape phones and tablets, squeezing the sheet.
        assertEquals(340.dp, sharedReaderDrawerSheetWidth(1194.dp))
        assertEquals(340.dp, sharedReaderDrawerSheetWidth(1366.dp))
    }

    @Test
    fun `narrow windows never break the sheet below the floor`() {
        assertEquals(300.dp, sharedReaderDrawerSheetWidth(320.dp))
        assertEquals(300.dp, sharedReaderDrawerSheetWidth(240.dp))
    }

    @Test
    fun `unmeasured windows fall back to the capped width`() {
        assertEquals(340.dp, sharedReaderDrawerSheetWidth(0.dp))
    }

    @Test
    fun `width always stays within the bounds the policy promises`() {
        for (window in listOf(240.dp, 320.dp, 393.dp, 600.dp, 834.dp, 1194.dp, 2000.dp)) {
            val width = sharedReaderDrawerSheetWidth(window)
            assertTrue(width >= 300.dp, "width $width below floor for window $window")
            assertTrue(width <= 340.dp, "width $width above cap for window $window")
        }
    }
}
