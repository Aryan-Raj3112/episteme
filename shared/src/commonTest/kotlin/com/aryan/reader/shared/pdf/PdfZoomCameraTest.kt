package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfZoomCameraTest {
    private val viewport = PdfZoomSize(400f, 800f)
    private val content = PdfZoomSize(400f, 600f)

    @Test
    fun pinchKeepsFocalPointAndClampsToContentBounds() {
        val camera = PdfZoomCamera().transformed(
            zoomChange = 2f,
            panChange = PdfZoomPoint(0f, 0f),
            pivot = PdfZoomPoint(100f, 300f),
            viewport = viewport,
            content = content
        )
        assertEquals(2f, camera.scale)
        assertEquals(100f, camera.offset.x)
        assertEquals(100f, camera.offset.y)
    }

    @Test
    fun panCannotExposeSpaceBeyondScaledPage() {
        val camera = PdfZoomCamera(3f, PdfZoomPoint(10_000f, -10_000f))
            .normalized(viewport, content)
        assertEquals(400f, camera.offset.x)
        assertEquals(-500f, camera.offset.y)
    }

    @Test
    fun panBoundsRemainCorrectAtOneThousandPercent() {
        val camera = PdfZoomCamera(PDF_MAX_ZOOM_SCALE, PdfZoomPoint(10_000f, -10_000f))
            .normalized(viewport, content)

        assertEquals(1_800f, camera.offset.x)
        assertEquals(-2_600f, camera.offset.y)
    }

    @Test
    fun returningToFitResetsOffset() {
        assertEquals(PdfZoomCamera(), PdfZoomCamera(1f, PdfZoomPoint(80f, 90f)).normalized(viewport, content))
    }

    @Test
    fun nonFiniteCameraValuesFallBackToFiniteDefaults() {
        val camera = PdfZoomCamera(Float.NaN, PdfZoomPoint(Float.NaN, Float.POSITIVE_INFINITY))
            .normalized(viewport, content)

        assertEquals(PdfZoomCamera(), camera)
        assertEquals(7f, finitePdfZoomValue(Float.NaN, 7f))
    }

    @Test
    fun paginatedOrientationChangeResetsCameraButResizeAndVerticalModeDoNot() {
        val portrait = PdfZoomSize(400f, 800f)
        val landscape = PdfZoomSize(800f, 400f)

        assertTrue(shouldResetPdfZoomForOrientationChange(portrait, landscape, isPaginated = true))
        assertEquals(false, shouldResetPdfZoomForOrientationChange(portrait, PdfZoomSize(500f, 900f), true))
        assertEquals(false, shouldResetPdfZoomForOrientationChange(portrait, landscape, false))
        assertEquals(false, shouldResetPdfZoomForOrientationChange(null, landscape, true))
    }

    @Test
    fun oneHandZoomDoublesOverBenchmarkDistance() {
        assertEquals(2f, pdfOneHandZoomScale(1f, 240f, 240f))
        assertEquals(8f, pdfOneHandZoomScale(4f, 240f, 240f))
        assertEquals(PDF_MAX_ZOOM_SCALE, pdfOneHandZoomScale(8f, 240f, 240f))

        val maxCamera = PdfZoomCamera(20f).normalized(
            PdfZoomSize(100f, 100f),
            PdfZoomSize(100f, 100f),
        )
        assertEquals(PDF_MAX_ZOOM_SCALE, maxCamera.scale)
    }

    @Test
    fun doubleTapMatchesAndroidToggleThreshold() {
        assertEquals(2.5f, pdfDoubleTapTargetScale(1.1f))
        assertEquals(1f, pdfDoubleTapTargetScale(1.11f))
    }

    @Test
    fun visibleBoundsInvertTheCameraTransform() {
        val bounds = visiblePdfPageBounds(
            camera = PdfZoomCamera(2f, PdfZoomPoint(0f, 0f)),
            transformedPageLeft = -200f,
            transformedPageTop = -400f,
            transformedPageRight = 600f,
            transformedPageBottom = 1200f,
            viewportLeft = 0f,
            viewportTop = 0f,
            viewportRight = 400f,
            viewportBottom = 800f
        )
        assertEquals(PdfPageBounds(0.25f, 0.25f, 0.75f, 0.75f), bounds)
    }

    @Test
    fun visibleBoundsRemainNormalizedAtOneThousandPercent() {
        val bounds = visiblePdfPageBounds(
            camera = PdfZoomCamera(PDF_MAX_ZOOM_SCALE),
            transformedPageLeft = -1_800f,
            transformedPageTop = -3_600f,
            transformedPageRight = 2_200f,
            transformedPageBottom = 4_400f,
            viewportLeft = 0f,
            viewportTop = 0f,
            viewportRight = 400f,
            viewportBottom = 800f,
        )

        assertEquals(PdfPageBounds(0.45f, 0.45f, 0.55f, 0.55f), bounds)
    }
}
