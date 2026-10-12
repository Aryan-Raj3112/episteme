package com.aryan.reader.paginatedreader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the flex arrangement tables both native stacks share (B1 step 3, flex
 * block kind). Android's paginated flex branch and shared's flex branch
 * carried identical copies; Android's vertical flex path ignores both
 * properties and is deliberately not covered (recorded, not ported).
 */
class NativeFlexArrangementTest {
    @Test
    fun `row main axis follows justify content`() {
        assertEquals(Arrangement.Center, nativeFlexRowHorizontalArrangement("center"))
        assertEquals(Arrangement.End, nativeFlexRowHorizontalArrangement("flex-end"))
        assertEquals(Arrangement.SpaceBetween, nativeFlexRowHorizontalArrangement("space-between"))
        assertEquals(Arrangement.SpaceAround, nativeFlexRowHorizontalArrangement("space-around"))
        assertEquals(Arrangement.Start, nativeFlexRowHorizontalArrangement(null))
        assertEquals(Arrangement.Start, nativeFlexRowHorizontalArrangement("flex-start"))
    }

    @Test
    fun `row cross axis follows align items`() {
        assertEquals(Alignment.CenterVertically, nativeFlexRowVerticalAlignment("center"))
        assertEquals(Alignment.Bottom, nativeFlexRowVerticalAlignment("flex-end"))
        assertEquals(Alignment.Top, nativeFlexRowVerticalAlignment(null))
        assertEquals(Alignment.Top, nativeFlexRowVerticalAlignment("stretch"))
    }

    @Test
    fun `column main axis follows justify content`() {
        assertEquals(Arrangement.Center, nativeFlexColumnVerticalArrangement("center"))
        assertEquals(Arrangement.Bottom, nativeFlexColumnVerticalArrangement("flex-end"))
        assertEquals(Arrangement.SpaceBetween, nativeFlexColumnVerticalArrangement("space-between"))
        assertEquals(Arrangement.SpaceAround, nativeFlexColumnVerticalArrangement("space-around"))
        assertEquals(Arrangement.Top, nativeFlexColumnVerticalArrangement(null))
    }

    @Test
    fun `column cross axis follows align items`() {
        assertEquals(Alignment.CenterHorizontally, nativeFlexColumnHorizontalAlignment("center"))
        assertEquals(Alignment.End, nativeFlexColumnHorizontalAlignment("flex-end"))
        assertEquals(Alignment.Start, nativeFlexColumnHorizontalAlignment(null))
    }
}
