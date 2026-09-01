package com.aryan.reader.paginatedreader.serialization

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.aryan.reader.paginatedreader.FontFamilyMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.protobuf.ProtoBuf

class FontFamilySerializerTest {
    private val proto = ProtoBuf

    // A list-backed family mirrors what loading a book-embedded @font-face file produces:
    // an instance with no reverse mapping in FontFamilyMapper, which used to trigger
    // encodeNull on an optional ProtoBuf property.
    private val customEmbeddedFamily = FontFamily(listOf(Font(0)))

    @Test
    fun genericFamiliesRoundTripThroughProtoBuf() {
        listOf(
            FontFamily.Serif,
            FontFamily.SansSerif,
            FontFamily.Monospace,
            FontFamily.Cursive,
            FontFamily.Default
        ).forEach { family ->
            val bytes = proto.encodeToByteArray(FontFamilySerializer, family)
            assertEquals(family, proto.decodeFromByteArray(FontFamilySerializer, bytes))
        }
    }

    @Test
    fun unmappedCustomFamilyDoesNotThrowAndDegradesToUnspecified() {
        val bytes = proto.encodeToByteArray(FontFamilySerializer, customEmbeddedFamily)
        assertNull(proto.decodeFromByteArray(FontFamilySerializer, bytes))
    }

    @Test
    fun nullFamilyEncodesWithoutEncodeNull() {
        val bytes = proto.encodeToByteArray(FontFamilySerializer, null)
        assertNull(proto.decodeFromByteArray(FontFamilySerializer, bytes))
    }

    @Test
    fun spanStyleWithCustomFamilySurvivesProtoBufRoundTrip() {
        val style = SpanStyle(
            color = androidx.compose.ui.graphics.Color.Red,
            fontFamily = customEmbeddedFamily,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        val restored = proto.decodeFromByteArray(
            SpanStyleSerializer,
            proto.encodeToByteArray(SpanStyleSerializer, style)
        )
        assertEquals(style.color, restored.color)
        assertEquals(style.fontWeight, restored.fontWeight)
        assertNull(restored.fontFamily)
    }
}
