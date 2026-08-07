package com.aryan.reader.shared.ui

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.aryan.reader.paginatedreader.BlockStyle
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedNativeImageStyleTest {

    private fun image(objectFit: String, filter: String? = null): SemanticImage {
        return SemanticImage(
            path = "data:image/png;base64,iVBORw0KGgo=",
            altText = "sample",
            intrinsicWidth = 120f,
            intrinsicHeight = 80f,
            style = CssStyle(
                blockStyle = BlockStyle(
                    width = 120.dp,
                    height = 80.dp,
                    objectFit = objectFit,
                    filter = filter
                )
            ),
            elementId = null,
            cfi = null,
            blockIndex = 0
        )
    }

    @Test
    fun `shared native image content scale mirrors android object fit mapping`() {
        assertEquals(ContentScale.Crop, image("cover").sharedNativeImageContentScale())
        assertEquals(ContentScale.FillBounds, image("fill").sharedNativeImageContentScale())
        assertEquals(ContentScale.Fit, image("contain").sharedNativeImageContentScale())
        assertEquals(ContentScale.Fit, image("scale-down").sharedNativeImageContentScale())
        assertEquals(ContentScale.Fit, image("none").sharedNativeImageContentScale())
    }

    @Test
    fun `shared native image color matrix applies only for invert 100 percent filters`() {
        assertNull(image("contain", filter = null).sharedNativeImageColorMatrix())
        assertNull(image("contain", filter = "grayscale(100%)").sharedNativeImageColorMatrix())
        assertEquals(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ).toList(),
            image("contain", filter = "invert(100%)").sharedNativeImageColorMatrix()!!.toList()
        )
    }
}
