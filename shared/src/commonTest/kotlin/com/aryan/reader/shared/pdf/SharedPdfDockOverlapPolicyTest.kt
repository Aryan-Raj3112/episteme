package com.aryan.reader.shared.pdf

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedPdfDockOverlapPolicyTest {
    private val gap = 8f
    private val edgeTop = 0f
    private val edgeBottom = 1000f
    private val wheelW = 96f
    private val wheelH = 192f

    @Test
    fun `side drop without another dock just clamps`() {
        assertEquals(
            500f,
            resolveSharedPdfSideWheelDropY(500f, 0f, wheelW, wheelH, edgeTop, edgeBottom, gap, null),
        )
        assertEquals(
            0f,
            resolveSharedPdfSideWheelDropY(-50f, 0f, wheelW, wheelH, edgeTop, edgeBottom, gap, null),
        )
        assertEquals(
            808f,
            resolveSharedPdfSideWheelDropY(900f, 0f, wheelW, wheelH, edgeTop, edgeBottom, gap, null),
        )
    }

    @Test
    fun `same-side drop stacks above or below the other wheel`() {
        val other = Rect(0f, 400f, wheelW, 592f)
        // Drop point below the other's center slides below it.
        assertEquals(
            600f,
            resolveSharedPdfSideWheelDropY(450f, 0f, wheelW, wheelH, edgeTop, edgeBottom, gap, other),
        )
        // Drop point above the other's center slides above it.
        assertEquals(
            200f,
            resolveSharedPdfSideWheelDropY(350f, 0f, wheelW, wheelH, edgeTop, edgeBottom, gap, other),
        )
        // A clear drop on the same edge passes through.
        assertEquals(
            700f,
            resolveSharedPdfSideWheelDropY(700f, 0f, wheelW, wheelH, edgeTop, edgeBottom, gap, other),
        )
    }

    @Test
    fun `opposite-side dock never interferes`() {
        val other = Rect(1000f - wheelW, 400f, 1000f, 592f)
        assertEquals(
            450f,
            resolveSharedPdfSideWheelDropY(450f, 0f, wheelW, wheelH, edgeTop, edgeBottom, gap, other),
        )
    }

    @Test
    fun `side wheel slides clear of top and bottom bars`() {
        val topBar = Rect(0f, 0f, 1000f, 60f)
        assertEquals(
            68f,
            resolveSharedPdfSideWheelDropY(0f, 0f, wheelW, wheelH, edgeTop, edgeBottom, gap, topBar),
        )
        val bottomBar = Rect(0f, 940f, 1000f, 1000f)
        assertEquals(
            740f,
            resolveSharedPdfSideWheelDropY(800f, 0f, wheelW, wheelH, edgeTop, edgeBottom, gap, bottomBar),
        )
        // Mid-edge drops clear of both bars are untouched.
        assertEquals(
            500f,
            resolveSharedPdfSideWheelDropY(500f, 0f, wheelW, wheelH, edgeTop, edgeBottom, gap, topBar),
        )
    }

    @Test
    fun `floating bar slides out of a side wheel band`() {
        val wheel = Rect(0f, 400f, wheelW, 592f)
        // Overlapping the band pushes right of it.
        assertEquals(
            104f,
            resolveSharedPdfBarDropX(50f, 450f, 300f, 48f, 1000f, gap, wheel),
        )
        // Clear drops pass through, null wheels pass through.
        assertEquals(
            300f,
            resolveSharedPdfBarDropX(300f, 100f, 300f, 48f, 1000f, gap, wheel),
        )
        assertEquals(
            50f,
            resolveSharedPdfBarDropX(50f, 450f, 300f, 48f, 1000f, gap, null),
        )
    }

    @Test
    fun `landed bar band nudges a hugging wheel clear`() {
        // Wheel hugging the top corner slides below the landed top bar.
        assertEquals(
            68f,
            resolveSharedPdfSideWheelClearOfBarBand(0f, wheelH, edgeTop, edgeBottom, gap, 0f, 60f),
        )
        // Wheel hugging the bottom corner slides above the landed bottom bar.
        assertEquals(
            740f,
            resolveSharedPdfSideWheelClearOfBarBand(800f, wheelH, edgeTop, edgeBottom, gap, 940f, 1000f),
        )
        // A clear wheel only clamps.
        assertEquals(
            500f,
            resolveSharedPdfSideWheelClearOfBarBand(500f, wheelH, edgeTop, edgeBottom, gap, 0f, 60f),
        )
    }
}
