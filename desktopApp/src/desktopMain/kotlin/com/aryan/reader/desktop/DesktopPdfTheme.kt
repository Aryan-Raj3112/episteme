package com.aryan.reader.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.pdf.pdfVerticalPageGapDp

internal val DesktopDefaultPdfDisplayMode = PdfDisplayMode.PAGINATION
internal val DesktopDefaultPdfVerticalPageGap = 8.dp
internal val DesktopDefaultPdfSpreadPageGap = 18.dp

internal fun desktopPdfPageBackgroundColor(
    theme: ReaderTheme,
    displayMode: PdfDisplayMode
): Color {
    return when (theme.id) {
        "no_theme", "system" -> if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Color.White else Color.Black
        "reverse" -> if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Color.Black else Color.White
        else -> theme.backgroundColor.takeIf { it.isSpecified }
            ?: if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Color.White else Color.Black
    }
}

internal fun desktopPdfVerticalViewportBackgroundColor(
    pageBackgroundColor: Color,
    gapBackgroundColor: Color,
    isPageGapVisible: Boolean
): Color {
    return if (isPageGapVisible) gapBackgroundColor else pageBackgroundColor
}

internal fun desktopPdfViewportBackgroundColor(
    displayMode: PdfDisplayMode,
    pageBackgroundColor: Color,
    appBackgroundColor: Color,
    isVerticalPageGapVisible: Boolean
): Color {
    return when (displayMode) {
        PdfDisplayMode.VERTICAL_SCROLL -> desktopPdfVerticalViewportBackgroundColor(
            pageBackgroundColor = pageBackgroundColor,
            gapBackgroundColor = appBackgroundColor,
            isPageGapVisible = isVerticalPageGapVisible
        )
        PdfDisplayMode.PAGINATION -> appBackgroundColor
    }
}

internal fun desktopPdfSpreadPageGapDp(
    isPageGapVisible: Boolean
): Dp = pdfVerticalPageGapDp(
    isPageGapVisible = isPageGapVisible,
    defaultGap = DesktopDefaultPdfSpreadPageGap
)
