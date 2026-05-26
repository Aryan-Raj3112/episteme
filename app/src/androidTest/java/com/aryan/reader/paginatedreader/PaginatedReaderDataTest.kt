// PaginatedReaderDataTest.kt
package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaginatedReaderDataTest {

    @Test
    fun cssStyle_mergeCorrectlyCombinesStyles() {
        val baseStyle = CssStyle(
            spanStyle = SpanStyle(color = Color.Black, fontWeight = FontWeight.Normal, fontSize = 16.sp),
            paragraphStyle = ParagraphStyle(textAlign = TextAlign.Start),
            fontFamilies = listOf("serif"),
            display = "block"
        )

        val overrideStyle = CssStyle(
            spanStyle = SpanStyle(color = Color.Red, fontStyle = FontStyle.Italic),
            paragraphStyle = ParagraphStyle(textAlign = TextAlign.Center),
            fontFamilies = listOf("sans-serif"),
            textTransform = "uppercase"
        )

        val merged = baseStyle.merge(overrideStyle)

        // Overridden properties
        assertThat(merged.spanStyle.color).isEqualTo(Color.Red)
        assertThat(merged.spanStyle.fontStyle).isEqualTo(FontStyle.Italic)
        assertThat(merged.paragraphStyle.textAlign).isEqualTo(TextAlign.Center)
        assertThat(merged.fontFamilies).containsExactly("sans-serif")
        assertThat(merged.textTransform).isEqualTo("uppercase")

        // Inherited properties
        assertThat(merged.spanStyle.fontWeight).isEqualTo(FontWeight.Normal)
        assertThat(merged.spanStyle.fontSize).isEqualTo(16.sp)
        assertThat(merged.display).isEqualTo("block")
    }

    @Test
    fun cssStyle_mergeWithEmptyOverrideDoesNotChangeBase() {
        val baseStyle = CssStyle(
            spanStyle = SpanStyle(color = Color.Black, fontWeight = FontWeight.Normal),
            fontFamilies = listOf("serif")
        )
        val overrideStyle = CssStyle()

        val merged = baseStyle.merge(overrideStyle)

        assertThat(merged).isEqualTo(baseStyle)
    }

    @Test
    fun blockStyle_mergeUsesOverrideProperties() {
        val baseStyle = BlockStyle(
            padding = BoxBorders(top = 10.dp, left = 10.dp),
            margin = BoxBorders(top = 5.dp, bottom = 5.dp),
            width = 100.dp,
            backgroundColor = Color.White
        )

        val overrideStyle = BlockStyle(
            padding = BoxBorders(top = 5.dp, right = 5.dp),
            margin = BoxBorders(bottom = 10.dp, left = 10.dp),
            width = 200.dp,
            backgroundColor = Color.Black,
            borderTop = BorderStyle(width = 1.dp, color = Color.Red)
        )

        val merged = baseStyle.merge(overrideStyle)

        // Padding should be from override, not additive
        assertThat(merged.padding.top).isEqualTo(5.dp)
        assertThat(merged.padding.left).isEqualTo(10.dp) // from base
        assertThat(merged.padding.right).isEqualTo(5.dp)
        assertThat(merged.padding.bottom).isEqualTo(0.dp) // from base

        // Margin should be from override
        assertThat(merged.margin.top).isEqualTo(5.dp) // from base
        assertThat(merged.margin.bottom).isEqualTo(10.dp)
        assertThat(merged.margin.left).isEqualTo(10.dp)
        assertThat(merged.margin.right).isEqualTo(0.dp) // from base

        // Other properties
        assertThat(merged.width).isEqualTo(200.dp)
        assertThat(merged.backgroundColor).isEqualTo(Color.Black)
        assertThat(merged.borderTop).isNotNull()
        assertThat(merged.borderTop?.width).isEqualTo(1.dp)
    }

    @Test
    fun blockStyle_mergeWithEmptyOverrideDoesNotChangeBase() {
        val baseStyle = BlockStyle(
            padding = BoxBorders(10.dp, 10.dp, 10.dp, 10.dp),
            margin = BoxBorders(5.dp, 5.dp, 5.dp, 5.dp),
            width = 100.dp,
            backgroundColor = Color.White
        )
        val overrideStyle = BlockStyle()

        val merged = baseStyle.merge(overrideStyle)

        assertThat(merged.padding.top).isEqualTo(10.dp)
        assertThat(merged.margin.top).isEqualTo(5.dp)
        assertThat(merged.width).isEqualTo(100.dp)
        assertThat(merged.backgroundColor).isEqualTo(Color.White)
        assertThat(merged.borderTop).isNull()
        assertThat(merged.borderRight).isNull()
        assertThat(merged.borderBottom).isNull()
        assertThat(merged.borderLeft).isNull()
    }
}
