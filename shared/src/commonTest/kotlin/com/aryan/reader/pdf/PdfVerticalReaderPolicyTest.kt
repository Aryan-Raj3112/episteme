package com.aryan.reader.pdf

import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfVerticalReaderPolicyTest {
    @Test
    fun `Android benchmark themes resolve exact vertical page backgrounds`() {
        assertEquals(Color.White, resolvePdfVerticalPageBackgroundColor(theme("no_theme", Color.Black)))
        assertEquals(Color.White, resolvePdfVerticalPageBackgroundColor(theme("system", Color.Black)))
        assertEquals(Color.Black, resolvePdfVerticalPageBackgroundColor(theme("reverse", Color.White)))
        assertEquals(Color(0xFFEEE8D5), resolvePdfVerticalPageBackgroundColor(theme("sepia", Color(0xFFEEE8D5))))
        assertEquals(Color.White, resolvePdfVerticalPageBackgroundColor(theme("custom", Color.Unspecified)))
    }

    @Test
    fun `locked reset keeps target page at header using fit zoom`() {
        assertEquals(
            PdfLockedOrientationResetCamera(zoom = 1f, panX = 0f, panY = -960f),
            calculateLockedOrientationResetCamera(
                pageTopY = 1_000f,
                totalDocHeight = 3_000f,
                screenWidth = 800f,
                screenHeight = 1_200f,
                headerHeightPx = 40f,
                footerHeightPx = 60f,
                fitZoom = 1f,
            ),
        )
    }

    @Test
    fun `locked reset centers narrow short document and clamps it below header`() {
        assertEquals(
            PdfLockedOrientationResetCamera(zoom = 0.5f, panX = 250f, panY = 40f),
            calculateLockedOrientationResetCamera(
                pageTopY = 120f,
                totalDocHeight = 500f,
                screenWidth = 1_000f,
                screenHeight = 900f,
                headerHeightPx = 40f,
                footerHeightPx = 60f,
                fitZoom = 0.5f,
            ),
        )
    }

    private fun theme(id: String, background: Color): ReaderTheme = ReaderTheme(
        id = id,
        name = id,
        backgroundColor = background,
        textColor = Color.Black,
        isDark = id == "reverse",
    )
}
