package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the highlight stroke geometry that produced "little gaps at the end of the
 * stroke". See `docs/pdf-vertical-scroll-and-highlight-audit.md` §3.
 */
class PdfHighlightGeometryTest {

    // --- the defect itself ------------------------------------------------------------------

    /**
     * The old wave advanced in whole wavelengths and clamped only the curve endpoints to the line
     * end. In the final partial period the control points overshot the end, so the tail loop bulged
     * past the last character. The sine-sampled wave must never leave the line.
     */
    @Test
    fun waveNeverLeavesTheLineHorizontally() {
        val heights = listOf(8f, 11.8f, 14f, 21f, 40f, 90f)
        val widths = listOf(0.5f, 1f, 3f, 7.3f, 13f, 20f, 37f, 60f, 120f, 233f, 512f)
        for (height in heights) {
            val style = pdfHighlightStrokeStyle(height)
            for (width in widths) {
                val line = PdfHighlightLine(
                    left = 10f,
                    right = 10f + width,
                    top = 100f,
                    height = height,
                )
                val points = pdfHighlightWavePoints(style, line)
                if (points.isEmpty()) continue
                assertTrue(points.isNotEmpty(), "width=$width height=$height produced no points")
                assertTrue(
                    points.all { it.x >= line.left - 0.001f },
                    "point left of line start: width=$width height=$height",
                )
                assertTrue(
                    points.all { it.x <= line.right + 0.001f },
                    "point past line end (the old tail loop): width=$width height=$height " +
                        "max=${points.maxOf { it.x }} end=${line.right}",
                )
                // Amplitude must stay within the resolved bounds around the baseline.
                val baseline = line.baselineForUnderline
                points.forEach { point ->
                    assertTrue(
                        point.y >= baseline - style.amplitude - 0.001f &&
                            point.y <= baseline + style.amplitude + 0.001f,
                        "amplitude out of bounds: width=$width height=$height y=${point.y}",
                    )
                }
            }
        }
    }

    /** The last sample must land exactly on the line end, not short of it or past it. */
    @Test
    fun waveEndsExactlyOnTheLineEnd() {
        val style = pdfHighlightStrokeStyle(21f)
        for (width in listOf(5f, 13f, 27f, 40f, 91f, 120f)) {
            val line = PdfHighlightLine(0f, width, 0f, 21f)
            val points = pdfHighlightWavePoints(style, line)
            assertEquals(width, points.last().x, 0.001f, "width=$width")
        }
    }

    /**
     * Amplitude and period have to be exact at any width. The old final period was squeezed into the
     * leftover `r` pixels, which is what made the tail read as a broken spike.
     */
    @Test
    fun waveAmplitudeIsReachedEvenForWidthsThatAreNotAMultipleOfTheWavelength() {
        val height = 21f
        val style = pdfHighlightStrokeStyle(height)
        val baseline = PdfHighlightLine(0f, 0f, 0f, height).baselineForUnderline
        // Awkward widths, including leftovers of several fractions of the wavelength. Once a line is
        // at least half a wavelength long it must contain a crest, whatever the leftover is.
        for (fraction in listOf(0.5f, 0.61f, 1.37f, 2.5f, 3.99f, 7.13f)) {
            val width = style.wavelength * fraction
            val line = PdfHighlightLine(0f, width, 0f, height)
            val crest = pdfHighlightWavePoints(style, line).minOf { it.y }
            assertTrue(
                crest <= baseline - style.amplitude * 0.99f,
                "crest did not reach full amplitude: fraction=$fraction " +
                    "crest=$crest expected<=${baseline - style.amplitude * 0.99f}",
            )
        }
    }

    /**
     * A line shorter than a crest cannot contain one - the old code pretended otherwise by
     * stretching a partial period. It must instead draw a proportional arc, still inside its bounds.
     */
    @Test
    fun subWavelengthLinesDrawAProportionalArcRatherThanAStretchedCrest() {
        val height = 21f
        val style = pdfHighlightStrokeStyle(height)
        val baseline = PdfHighlightLine(0f, 0f, 0f, height).baselineForUnderline
        val width = style.wavelength * 0.2f
        val points = pdfHighlightWavePoints(style, PdfHighlightLine(0f, width, 0f, height))
        assertTrue(points.isNotEmpty())
        // The crest is the true sine value at that phase, not the full amplitude.
        // `kotlin.math` throughout, not `java.lang.Math`: this is a common test and `Math` does not
        // exist on Kotlin/Native. `sin` has a Float overload, so no trailing conversion is needed.
        val expected = baseline - style.amplitude * kotlin.math.sin(
            2f * kotlin.math.PI.toFloat() * 0.2f
        )
        assertTrue(
            kotlin.math.abs(points.minOf { it.y } - expected) < 0.05f,
            "arc should follow the sine, got ${points.minOf { it.y }} expected ~$expected",
        )
    }

    /**
     * A highlight that pdfium split into several font-run rects on one line must be drawn as one
     * continuous line, not one stroke per rect. Two rects with a shared band are one line.
     */
    @Test
    fun rectsOnOneLineGroupIntoASingleLine() {
        val rects = listOf(
            PdfHighlightRect(10f, 100f, 80f, 118f),
            PdfHighlightRect(80f, 100f, 150f, 118f),
            PdfHighlightRect(150f, 101f, 220f, 119f),
        )
        val lines = pdfHighlightLines(rects)
        assertEquals(1, lines.size, "adjacent font runs on one line must merge")
        assertEquals(10f, lines[0].left, 0.001f)
        assertEquals(220f, lines[0].right, 0.001f)
    }

    /**
     * The other half of the defect: a loose overlap test collapsed two text lines into one rect, and
     * the single stroke then landed under the second line only - the first line lost its stroke
     * entirely. Tight leading (12pt leading over a ~13.8pt font box, so ~1.8pt of shared band) must
     * produce two lines.
     */
    @Test
    fun tightlyLedAdjacentLinesStaySeparate() {
        val lines = pdfHighlightLines(
            listOf(
                PdfHighlightRect(10f, 700f, 200f, 713.8f),
                PdfHighlightRect(10f, 676f, 190f, 690f),
            )
        )
        assertEquals(2, lines.size, "two text lines must not collapse into one rect")
    }

    /** A run must not chain its way up through several lines via the running band. */
    @Test
    fun aRunCannotChainAcrossManyLines() {
        val rects = (0 until 6).map { index ->
            // Each box overlaps its neighbour by ~1.8pt, like tight leading.
            val top = 700f - index * 12f
            PdfHighlightRect(10f, top, 200f, top + 13.8f)
        }
        val lines = pdfHighlightLines(rects)
        assertEquals(6, lines.size, "each line must keep its own stroke")
    }

    @Test
    fun lineGroupingIgnoresInputOrder() {
        val rects = listOf(
            PdfHighlightRect(10f, 700f, 200f, 713.8f),
            PdfHighlightRect(10f, 676f, 190f, 690f),
        )
        assertEquals(
            pdfHighlightLines(rects).map { it.top },
            pdfHighlightLines(rects.reversed()).map { it.top },
        )
    }

    @Test
    fun lineGroupingSkipsDegenerateRects() {
        val lines = pdfHighlightLines(
            listOf(
                PdfHighlightRect(0f, 0f, 0f, 20f),
                PdfHighlightRect(10f, 100f, 90f, 120f),
                PdfHighlightRect(10f, 100f, 10f, 120f),
            )
        )
        assertEquals(1, lines.size)
        assertEquals(10f, lines[0].left, 0.001f)
        assertEquals(90f, lines[0].right, 0.001f)
    }

    // --- baselines and styles --------------------------------------------------------------

    @Test
    fun baselinesUseTheDocumentedFractions() {
        val line = PdfHighlightLine(0f, 100f, 200f, 20f)
        assertEquals(200f + 20f * 0.86f, line.baselineForUnderline, 0.001f)
        assertEquals(200f + 20f * 0.52f, line.baselineForStrikethrough, 0.001f)
    }

    @Test
    fun styleKeepsThePreviousPixelFloors() {
        // Low zoom must not degenerate the wave into a scribble, and high zoom must not blow it up.
        val small = pdfHighlightStrokeStyle(4f)
        assertEquals(1.2f, small.amplitude, 0.001f)
        assertEquals(6f, small.wavelength, 0.001f)
        assertEquals(1.5f, small.lineStrokeWidth, 0.001f)
        assertEquals(1.2f, small.waveStrokeWidth, 0.001f)

        val large = pdfHighlightStrokeStyle(400f)
        assertEquals(3.5f, large.amplitude, 0.001f)
        assertEquals(14f, large.wavelength, 0.001f)
        assertEquals(4f, large.lineStrokeWidth, 0.001f)
        assertEquals(3f, large.waveStrokeWidth, 0.001f)

        val mid = pdfHighlightStrokeStyle(21f)
        assertEquals(21f * 0.08f, mid.amplitude, 0.001f)
        assertEquals(21f * 0.62f, mid.wavelength, 0.001f)
    }

    @Test
    fun styleIsSafeForNonFiniteAndZeroHeights() {
        listOf(0f, -5f, Float.NaN, Float.POSITIVE_INFINITY).forEach { height ->
            val style = pdfHighlightStrokeStyle(height)
            assertEquals(0f, style.height, 0.001f)
            assertTrue(!style.hasArea, "height=$height must not report area")
            assertTrue(
                pdfHighlightWavePoints(style, PdfHighlightLine(0f, 100f, 0f, height)).isEmpty(),
                "height=$height must produce no wave",
            )
        }
    }

    @Test
    fun rectNormalizesInvertedBounds() {
        val rect = PdfHighlightRect.from(90f, 120f, 10f, 100f)
        assertEquals(10f, rect.left, 0.001f)
        assertEquals(100f, rect.top, 0.001f)
        assertEquals(90f, rect.right, 0.001f)
        assertEquals(120f, rect.bottom, 0.001f)
        assertTrue(rect.hasArea)
    }

    @Test
    fun phaseCarriesAcrossContinuedLines() {
        // Two half-width segments of the same line: continuing the phase must keep the same slope at
        // the seam that a single full-width line would have.
        val style = pdfHighlightStrokeStyle(21f)
        val wavelength = style.wavelength
        val half = wavelength * 1.5f
        val whole = pdfHighlightWavePoints(
            style,
            PdfHighlightLine(0f, half * 2f, 0f, 21f),
        )
        val firstHalf = pdfHighlightWavePoints(
            style,
            PdfHighlightLine(0f, half, 0f, 21f),
        )
        val secondHalf = pdfHighlightWavePoints(
            style,
            PdfHighlightLine(half, half * 2f, 0f, 21f),
            startPhaseCycles = half / wavelength,
        )
        // At the seam the continued run must sit on the baseline exactly like the whole run does.
        assertEquals(whole[whole.size / 2].y, secondHalf.first().y, 0.001f)
        assertEquals(whole[whole.size / 2].y, firstHalf.last().y, 0.001f)
    }
}