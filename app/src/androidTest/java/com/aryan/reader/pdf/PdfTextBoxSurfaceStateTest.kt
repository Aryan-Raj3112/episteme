package com.aryan.reader.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.pdf.data.PdfTextBox
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfTextBoxSurfaceStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun independentlyComposedViewportSeesTextBoxValueUpdates() {
        val initialBox = testBox(text = "old")
        val updatedBox = initialBox.copy(text = "a")
        val textBoxSurfaceState = PdfViewerTextBoxSurfaceState()
        textBoxSurfaceState.data.value = PdfViewerTextBoxSurfaceData(
            all = listOf(initialBox),
            byPage = mapOf(initialBox.pageIndex to listOf(initialBox)),
        )

        composeTestRule.setContent {
            val surfaceData = textBoxSurfaceState.data.value
            Box {
                Text(
                    text = surfaceData.all.singleOrNull()?.text.orEmpty(),
                    modifier = Modifier.testTag("PdfTextBoxSurfaceValue"),
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("PdfTextBoxSurfaceValue").assertTextEquals("old")

        composeTestRule.runOnIdle {
            textBoxSurfaceState.data.value = PdfViewerTextBoxSurfaceData(
                all = listOf(updatedBox),
                byPage = mapOf(updatedBox.pageIndex to listOf(updatedBox)),
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("PdfTextBoxSurfaceValue").assertTextEquals("a")
        assertThat(textBoxSurfaceState.data.value.byPage[updatedBox.pageIndex])
            .containsExactly(updatedBox)
    }

    private fun testBox(text: String): PdfTextBox = PdfTextBox(
        id = "box",
        pageIndex = 0,
        relativeBounds = Rect(0f, 0f, 1f, 1f),
        text = text,
        color = Color.Black,
        backgroundColor = Color.Transparent,
        fontSize = 16f,
    )
}
