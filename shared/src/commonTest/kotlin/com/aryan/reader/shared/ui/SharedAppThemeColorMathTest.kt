package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedAppThemeColorMathTest {

    @Test
    fun `rgb color converts to expected hsv components`() {
        val hsv = Color(0xFFFF0000).toSharedHsvColor()

        assertClose(0f, hsv.hue)
        assertClose(1f, hsv.saturation)
        assertClose(1f, hsv.value)
    }

    @Test
    fun `hsv color converts back to compose rgb color`() {
        val color = SharedHsvColor(hue = 120f, saturation = 1f, value = 1f).toComposeColor()

        assertEquals(Color(0xFF00FF00).toArgb(), color.toArgb())
    }

    @Test
    fun `hex parser accepts android style six digit colors`() {
        val color = "#006C4C".toSharedHexColorOrNull()

        assertEquals(Color(0xFF006C4C).toArgb(), color?.toArgb())
        assertEquals("#006C4C", color?.toSharedHexString())
    }

    @Test
    fun `hex parser rejects incomplete and invalid colors`() {
        assertNull("006C4".toSharedHexColorOrNull())
        assertNull("#006C4Z".toSharedHexColorOrNull())
    }

    @Test
    fun `rgb hsv conversion round trips common custom theme colors`() {
        val original = Color(0xFF2D6A4F)
        val roundTripped = original.toSharedHsvColor().toComposeColor()

        assertTrue(abs(original.red - roundTripped.red) < 0.01f)
        assertTrue(abs(original.green - roundTripped.green) < 0.01f)
        assertTrue(abs(original.blue - roundTripped.blue) < 0.01f)
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.01f, "Expected $expected but was $actual")
    }
}
