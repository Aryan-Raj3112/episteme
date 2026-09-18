package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.isSpecified
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedNativeHighlightLayoutTest {
    private fun highlight(style: HighlightStyle): UserHighlight = UserHighlight(
        id = "h1",
        cfi = "cfi",
        text = "alpha",
        color = HighlightColor.YELLOW,
        chapterIndex = 0,
        style = style,
        locator = ReaderLocator(chapterIndex = 0, startOffset = 0, endOffset = 5, textQuote = "alpha")
    )

    @Test
    fun `native highlight spans are paint only and cannot shift layout`() {
        HighlightStyle.entries.forEach { style ->
            val span = highlight(style).nativeSpanStyle()
            // Layout-affecting metrics must stay unspecified: background /
            // underline paint must not change font size or spacing.
            // (SpanStyle carries no lineHeight; that lives in ParagraphStyle.)
            assertFalse(span.fontSize.isSpecified, "style=$style fontSize must be unspecified")
            assertFalse(span.letterSpacing.isSpecified, "style=$style letterSpacing must be unspecified")
            assertEquals(null, span.baselineShift, "style=$style baselineShift must be null")
            assertEquals(null, span.textGeometricTransform, "style=$style transform must be null")
        }
        // Background highlights paint a background; line styles paint decorations.
        assertTrue(highlight(HighlightStyle.BACKGROUND).nativeSpanStyle().background.isSpecified)
        assertEquals(
            androidx.compose.ui.text.style.TextDecoration.Underline,
            highlight(HighlightStyle.UNDERLINE).nativeSpanStyle().textDecoration
        )
        assertEquals(
            androidx.compose.ui.text.style.TextDecoration.LineThrough,
            highlight(HighlightStyle.STRIKETHROUGH).nativeSpanStyle().textDecoration
        )
    }
}
