package com.aryan.reader.paginatedreader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderLinkHitTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun urlAnnotationAtPositionIgnoresSameLineSpaceAfterLink() {
        lateinit var text: AnnotatedString
        var layoutResult: TextLayoutResult? = null

        composeTestRule.setContent {
            val textMeasurer = rememberTextMeasurer()
            text = linkText("Open")
            layoutResult = textMeasurer.measure(
                text = text,
                style = TextStyle(fontSize = 24.sp),
                constraints = Constraints.fixedWidth(500)
            )
        }

        composeTestRule.waitForIdle()

        val layout = checkNotNull(layoutResult)
        val firstBox = layout.getBoundingBox(0)
        val lastBox = layout.getBoundingBox(text.length - 1)
        val y = (firstBox.top + firstBox.bottom) / 2f

        assertThat(text.readerUrlAnnotationAtPosition(layout, Offset(firstBox.left + 1f, y)))
            .isEqualTo(HREF)
        assertThat(text.readerUrlAnnotationAtPosition(layout, Offset(lastBox.right + 60f, y)))
            .isNull()
    }

    @Test
    fun urlAnnotationAtPositionAppliesTextStartOffsetForWrappedLineLayouts() {
        lateinit var fullText: AnnotatedString
        var lineLayoutResult: TextLayoutResult? = null
        val prefix = "Before "
        val label = "Open"

        composeTestRule.setContent {
            val textMeasurer = rememberTextMeasurer()
            fullText = buildAnnotatedString {
                append(prefix)
                append(label)
                addStringAnnotation("URL", HREF, prefix.length, prefix.length + label.length)
            }
            lineLayoutResult = textMeasurer.measure(
                text = fullText.subSequence(prefix.length, fullText.length),
                style = TextStyle(fontSize = 24.sp),
                constraints = Constraints.fixedWidth(500)
            )
        }

        composeTestRule.waitForIdle()

        val layout = checkNotNull(lineLayoutResult)
        val firstBox = layout.getBoundingBox(0)
        val lastBox = layout.getBoundingBox(label.length - 1)
        val y = (firstBox.top + firstBox.bottom) / 2f

        assertThat(
            fullText.readerUrlAnnotationAtPosition(
                layout = layout,
                position = Offset(firstBox.left + 1f, y),
                textStartOffset = prefix.length
            )
        ).isEqualTo(HREF)
        assertThat(
            fullText.readerUrlAnnotationAtPosition(
                layout = layout,
                position = Offset(lastBox.right + 60f, y),
                textStartOffset = prefix.length
            )
        ).isNull()
    }

    private fun linkText(label: String) = buildAnnotatedString {
        append(label)
        addStringAnnotation("URL", HREF, 0, label.length)
    }

    private companion object {
        const val HREF = "chapter.xhtml#target"
    }
}
