package com.aryan.reader.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderTheme

internal val DesktopDefaultPdfDisplayMode = PdfDisplayMode.VERTICAL_SCROLL
internal val DesktopDefaultPdfVerticalPageGap = 8.dp

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
