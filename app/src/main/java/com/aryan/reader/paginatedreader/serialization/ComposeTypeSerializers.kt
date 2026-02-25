/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Electronic mail: epistemereader@gmail.com
 */
@file:OptIn(ExperimentalSerializationApi::class)
package com.aryan.reader.paginatedreader.serialization

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.aryan.reader.paginatedreader.FontFamilyMapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure


object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Color") {
        element<Long>("value")
    }

    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeStructure(descriptor) {
            encodeLongElement(descriptor, 0, value.value.toLong())
        }
    }

    override fun deserialize(decoder: Decoder): Color {
        return decoder.decodeStructure(descriptor) {
            var colorValue = 0L
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> colorValue = decodeLongElement(descriptor, 0)
                    -1 -> break
                    else -> error("Unexpected index: $index")
                }
            }
            Color(colorValue.toULong())
        }
    }
}

object DpSerializer : KSerializer<Dp> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Dp") {
        element<Float>("value")
    }

    override fun serialize(encoder: Encoder, value: Dp) {
        encoder.encodeStructure(descriptor) {
            if (value != Dp.Unspecified) {
                encodeFloatElement(descriptor, 0, value.value)
            }
        }
    }

    override fun deserialize(decoder: Decoder): Dp {
        return decoder.decodeStructure(descriptor) {
            var dpValue: Float? = null
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> dpValue = decodeFloatElement(descriptor, 0)
                    -1 -> break
                    else -> error("Unexpected index: $index")
                }
            }
            dpValue?.dp ?: Dp.Unspecified
        }
    }
}

object TextUnitTypeSerializer : KSerializer<TextUnitType> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TextUnitType")
    override fun serialize(encoder: Encoder, value: TextUnitType) {
        val typeString = when (value) {
            TextUnitType.Sp -> "Sp"
            TextUnitType.Em -> "Em"
            else -> "Unspecified"
        }
        encoder.encodeString(typeString)
    }

    override fun deserialize(decoder: Decoder): TextUnitType {
        return when (decoder.decodeString()) {
            "Sp" -> TextUnitType.Sp
            "Em" -> TextUnitType.Em
            else -> TextUnitType.Unspecified
        }
    }
}

@Serializable
@SerialName("TextUnit")
private data class TextUnitSurrogate(val value: Float, @Serializable(with = TextUnitTypeSerializer::class) val type: TextUnitType)

object TextUnitSerializer : KSerializer<TextUnit> {
    override val descriptor: SerialDescriptor = TextUnitSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: TextUnit) {
        if (value.isSpecified) {
            val surrogate = TextUnitSurrogate(value.value, value.type)
            encoder.encodeSerializableValue(TextUnitSurrogate.serializer(), surrogate)
        }
    }

    override fun deserialize(decoder: Decoder): TextUnit {
        return try {
            val surrogate = decoder.decodeSerializableValue(TextUnitSurrogate.serializer())
            TextUnit(surrogate.value, surrogate.type)
        } catch (_: Exception) {
            TextUnit.Unspecified
        }
    }
}

object FontWeightSerializer : KSerializer<FontWeight?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FontWeight")
    override fun serialize(encoder: Encoder, value: FontWeight?) = value?.let { encoder.encodeInt(it.weight) } ?: encoder.encodeNull()
    override fun deserialize(decoder: Decoder): FontWeight? = if (decoder.decodeNotNullMark()) FontWeight(decoder.decodeInt()) else null
}

object FontStyleSerializer : KSerializer<FontStyle?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FontStyle")
    override fun serialize(encoder: Encoder, value: FontStyle?) {
        val intValue = when (value) {
            FontStyle.Normal -> 0
            FontStyle.Italic -> 1
            else -> -1
        }
        encoder.encodeInt(intValue)
    }

    override fun deserialize(decoder: Decoder): FontStyle? {
        return when (decoder.decodeInt()) {
            0 -> FontStyle.Normal
            1 -> FontStyle.Italic
            else -> null
        }
    }
}

object BaselineShiftSerializer : KSerializer<BaselineShift?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BaselineShift")
    override fun serialize(encoder: Encoder, value: BaselineShift?) = value?.let { encoder.encodeFloat(it.multiplier) } ?: encoder.encodeNull()
    override fun deserialize(decoder: Decoder): BaselineShift? = if (decoder.decodeNotNullMark()) BaselineShift(decoder.decodeFloat()) else null
}

object TextDecorationSerializer : KSerializer<TextDecoration?> {
    @Serializable
    private data class TextDecorationSurrogate(val hasUnderline: Boolean, val hasLineThrough: Boolean)

    override val descriptor: SerialDescriptor = TextDecorationSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: TextDecoration?) {
        if (value == null) {
            encoder.encodeNull()
            return
        }
        val surrogate = TextDecorationSurrogate(
            hasUnderline = value.contains(TextDecoration.Underline),
            hasLineThrough = value.contains(TextDecoration.LineThrough)
        )
        encoder.encodeSerializableValue(TextDecorationSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): TextDecoration? {
        if (decoder.decodeNotNullMark()) {
            val surrogate = decoder.decodeSerializableValue(TextDecorationSurrogate.serializer())
            var decoration: TextDecoration? = null
            if (surrogate.hasUnderline) {
                decoration = TextDecoration.Underline
            }
            if (surrogate.hasLineThrough) {
                decoration = (decoration ?: TextDecoration.None) + TextDecoration.LineThrough
            }
            return decoration
        }
        return null
    }
}

@Serializable
private data class ShadowSurrogate(
    @Serializable(with = ColorSerializer::class) val color: Color,
    val offsetX: Float,
    val offsetY: Float,
    val blurRadius: Float
)

object ShadowSerializer : KSerializer<Shadow?> {
    override val descriptor: SerialDescriptor = ShadowSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Shadow?) {
        if (value == null) {
            encoder.encodeNull()
            return
        }
        val surrogate = ShadowSurrogate(value.color, value.offset.x, value.offset.y, value.blurRadius)
        encoder.encodeSerializableValue(ShadowSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): Shadow? {
        if (decoder.decodeNotNullMark()) {
            val surrogate = decoder.decodeSerializableValue(ShadowSurrogate.serializer())
            return Shadow(surrogate.color, Offset(surrogate.offsetX, surrogate.offsetY), surrogate.blurRadius)
        }
        return null
    }
}

@Serializable
private data class SpanStyleSurrogate(
    @Serializable(with = ColorSerializer::class) val color: Color = Color.Unspecified,
    @Serializable(with = TextUnitSerializer::class) val fontSize: TextUnit = TextUnit.Unspecified,
    @Serializable(with = FontWeightSerializer::class) val fontWeight: FontWeight? = null,
    @Serializable(with = FontStyleSerializer::class) val fontStyle: FontStyle? = null,
    @Serializable(with = FontFamilySerializer::class) val fontFamily: FontFamily? = null,
    val fontFeatureSettings: String? = null,
    @Serializable(with = TextUnitSerializer::class) val letterSpacing: TextUnit = TextUnit.Unspecified,
    @Serializable(with = BaselineShiftSerializer::class) val baselineShift: BaselineShift? = null,
    @Serializable(with = TextDecorationSerializer::class) val textDecoration: TextDecoration? = null,
    @Serializable(with = ColorSerializer::class) val background: Color = Color.Unspecified,
    @Serializable(with = ShadowSerializer::class) val shadow: Shadow? = null
)

object SpanStyleSerializer : KSerializer<SpanStyle> {
    override val descriptor: SerialDescriptor = SpanStyleSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: SpanStyle) {
        val surrogate = SpanStyleSurrogate(
            color = value.color,
            fontSize = value.fontSize,
            fontWeight = value.fontWeight,
            fontStyle = value.fontStyle,
            fontFamily = value.fontFamily,
            fontFeatureSettings = value.fontFeatureSettings,
            letterSpacing = value.letterSpacing,
            baselineShift = value.baselineShift,
            textDecoration = value.textDecoration,
            background = value.background,
            shadow = value.shadow
        )
        encoder.encodeSerializableValue(SpanStyleSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): SpanStyle {
        val surrogate = decoder.decodeSerializableValue(SpanStyleSurrogate.serializer())
        return SpanStyle(
            color = surrogate.color,
            fontSize = surrogate.fontSize,
            fontWeight = surrogate.fontWeight,
            fontStyle = surrogate.fontStyle,
            fontFamily = surrogate.fontFamily,
            fontFeatureSettings = surrogate.fontFeatureSettings,
            letterSpacing = surrogate.letterSpacing,
            baselineShift = surrogate.baselineShift,
            textDecoration = surrogate.textDecoration,
            background = surrogate.background,
            shadow = surrogate.shadow
        )
    }
}

object TextAlignSerializer : KSerializer<TextAlign?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TextAlign")
    override fun serialize(encoder: Encoder, value: TextAlign?) {
        val intValue = when(value) {
            TextAlign.Left -> 1
            TextAlign.Right -> 2
            TextAlign.Center -> 3
            TextAlign.Justify -> 4
            TextAlign.Start -> 5
            TextAlign.End -> 6
            else -> 0 // null or unspecified
        }
        encoder.encodeInt(intValue)
    }

    override fun deserialize(decoder: Decoder): TextAlign? {
        return when(decoder.decodeInt()) {
            1 -> TextAlign.Left
            2 -> TextAlign.Right
            3 -> TextAlign.Center
            4 -> TextAlign.Justify
            5 -> TextAlign.Start
            6 -> TextAlign.End
            else -> null
        }
    }
}

object TextDirectionSerializer : KSerializer<TextDirection?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TextDirection")
    override fun serialize(encoder: Encoder, value: TextDirection?) {
        val intValue = when(value) {
            TextDirection.Ltr -> 1
            TextDirection.Rtl -> 2
            TextDirection.Content -> 3
            TextDirection.ContentOrLtr -> 4
            TextDirection.ContentOrRtl -> 5
            else -> 0 // null
        }
        encoder.encodeInt(intValue)
    }

    override fun deserialize(decoder: Decoder): TextDirection? {
        return when(decoder.decodeInt()) {
            1 -> TextDirection.Ltr
            2 -> TextDirection.Rtl
            3 -> TextDirection.Content
            4 -> TextDirection.ContentOrLtr
            5 -> TextDirection.ContentOrRtl
            else -> null
        }
    }
}

object LineBreakSerializer : KSerializer<LineBreak?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LineBreak")
    override fun serialize(encoder: Encoder, value: LineBreak?) {
        val intValue = when (value) {
            LineBreak.Simple -> 1
            LineBreak.Paragraph -> 2
            else -> 0
        }
        encoder.encodeInt(intValue)
    }
    override fun deserialize(decoder: Decoder): LineBreak? {
        return when(decoder.decodeInt()) {
            1 -> LineBreak.Simple
            2 -> LineBreak.Paragraph
            else -> null
        }
    }
}

object HyphensSerializer : KSerializer<Hyphens?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Hyphens")
    override fun serialize(encoder: Encoder, value: Hyphens?) {
        val intValue = when(value) {
            Hyphens.None -> 1
            Hyphens.Auto -> 2
            else -> 0 // null or unspecified
        }
        encoder.encodeInt(intValue)
    }
    override fun deserialize(decoder: Decoder): Hyphens? {
        return when(decoder.decodeInt()) {
            1 -> Hyphens.None
            2 -> Hyphens.Auto
            else -> null
        }
    }
}

@Serializable
private data class TextIndentSurrogate(
    @Serializable(with = TextUnitSerializer::class) val firstLine: TextUnit,
    @Serializable(with = TextUnitSerializer::class) val restLine: TextUnit
)

object TextIndentSerializer : KSerializer<TextIndent?> {
    override val descriptor: SerialDescriptor = TextIndentSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: TextIndent?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeSerializableValue(TextIndentSurrogate.serializer(), TextIndentSurrogate(value.firstLine, value.restLine))
        }
    }
    override fun deserialize(decoder: Decoder): TextIndent? {
        return if (decoder.decodeNotNullMark()) {
            val surrogate = decoder.decodeSerializableValue(TextIndentSurrogate.serializer())
            TextIndent(surrogate.firstLine, surrogate.restLine)
        } else {
            null
        }
    }
}

@Serializable
private data class ParagraphStyleSurrogate(
    @Serializable(with = TextAlignSerializer::class) val textAlign: TextAlign? = null,
    @Serializable(with = TextDirectionSerializer::class) val textDirection: TextDirection? = null,
    @Serializable(with = TextUnitSerializer::class) val lineHeight: TextUnit = TextUnit.Unspecified,
    @Serializable(with = TextIndentSerializer::class) val textIndent: TextIndent? = null,
    @Serializable(with = LineBreakSerializer::class) val lineBreak: LineBreak? = null,
    @Serializable(with = HyphensSerializer::class) val hyphens: Hyphens? = null
)

object ParagraphStyleSerializer : KSerializer<ParagraphStyle> {
    override val descriptor: SerialDescriptor = ParagraphStyleSurrogate.serializer().descriptor
    override fun serialize(encoder: Encoder, value: ParagraphStyle) {
        val surrogate = ParagraphStyleSurrogate(
            textAlign = value.textAlign,
            textDirection = value.textDirection,
            lineHeight = value.lineHeight,
            textIndent = value.textIndent,
            lineBreak = value.lineBreak,
            hyphens = value.hyphens
        )
        encoder.encodeSerializableValue(ParagraphStyleSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): ParagraphStyle {
        val surrogate = decoder.decodeSerializableValue(ParagraphStyleSurrogate.serializer())
        return ParagraphStyle(
            textAlign = surrogate.textAlign ?: TextAlign.Unspecified,
            textDirection = surrogate.textDirection ?: TextDirection.Unspecified,
            lineHeight = surrogate.lineHeight,
            textIndent = surrogate.textIndent,
            lineBreak = surrogate.lineBreak ?: LineBreak.Unspecified,
            hyphens = surrogate.hyphens ?: Hyphens.Unspecified
        )
    }
}

object AnnotatedStringSerializer : KSerializer<AnnotatedString> {
    @Serializable
    private data class RangeSurrogate<T>(val item: T, val start: Int, val end: Int, val tag: String)

    @Serializable
    private data class AnnotatedStringSurrogate(
        val text: String,
        val spanStyles: List<RangeSurrogate<@Serializable(with = SpanStyleSerializer::class) SpanStyle>>,
        val paragraphStyles: List<RangeSurrogate<@Serializable(with = ParagraphStyleSerializer::class) ParagraphStyle>>,
        val stringAnnotations: List<RangeSurrogate<String>>
    )

    override val descriptor: SerialDescriptor = AnnotatedStringSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: AnnotatedString) {
        val surrogate = AnnotatedStringSurrogate(
            text = value.text,
            spanStyles = value.spanStyles.map { RangeSurrogate(it.item, it.start, it.end, it.tag) },
            paragraphStyles = value.paragraphStyles.map { RangeSurrogate(it.item, it.start, it.end, it.tag) },
            stringAnnotations = value.getStringAnnotations(0, value.length).map { RangeSurrogate(it.item, it.start, it.end, it.tag) }
        )
        encoder.encodeSerializableValue(AnnotatedStringSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): AnnotatedString {
        val surrogate = decoder.decodeSerializableValue(AnnotatedStringSurrogate.serializer())
        return AnnotatedString.Builder(surrogate.text).apply {
            surrogate.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
            surrogate.paragraphStyles.forEach { addStyle(it.item, it.start, it.end) }
            surrogate.stringAnnotations.forEach { addStringAnnotation(it.tag, it.item, it.start, it.end) }
        }.toAnnotatedString()
    }
}

object FontFamilySerializer : KSerializer<FontFamily?> {
    override val descriptor = PrimitiveSerialDescriptor("FontFamily", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FontFamily?) {
        val name = FontFamilyMapper.fontFamilyToName(value ?: return encoder.encodeNull())
        if (name != null) {
            encoder.encodeString(name)
        } else {
            encoder.encodeNull()
        }
    }

    override fun deserialize(decoder: Decoder): FontFamily? {
        if (decoder.decodeNotNullMark()) {
            return FontFamilyMapper.nameToFontFamily(decoder.decodeString())
        }
        return null
    }
}