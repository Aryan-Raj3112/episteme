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

package com.aryan.reader.paginatedreader

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass


@Serializable
sealed interface SemanticBlock {
    val elementId: String?
    val cfi: String?
    val style: CssStyle
    val blockIndex: Int
}

@Serializable
data class SemanticSpan(
    @ProtoNumber(1) val start: Int,
    @ProtoNumber(2) val end: Int,
    @ProtoNumber(3) val style: CssStyle,
    @ProtoNumber(4) val linkHref: String? = null,
    @ProtoNumber(5) val tag: String,
    @ProtoNumber(6) val elementId: String? = null // Add this
)

fun SemanticBlock.withElementId(id: String): SemanticBlock {
    if (this.elementId != null) return this
    return when (this) {
        is SemanticParagraph -> this.copy(elementId = id)
        is SemanticHeader -> this.copy(elementId = id)
        is SemanticListItem -> this.copy(elementId = id)
        is SemanticList -> this.copy(elementId = id)
        is SemanticImage -> this.copy(elementId = id)
        is SemanticMath -> this.copy(elementId = id)
        is SemanticSpacer -> this.copy(elementId = id)
        is SemanticTable -> this.copy(elementId = id)
        is SemanticFlexContainer -> this.copy(elementId = id)
        is SemanticWrappingBlock -> this.copy(elementId = id)
        is SemanticTextBlock -> this
    }
}

interface SemanticTextBlock : SemanticBlock {
    val text: String
    val spans: List<SemanticSpan>
    val startCharOffsetInSource: Int
}

@Serializable
data class SemanticParagraph(
    @ProtoNumber(1) override val text: String,
    @ProtoNumber(2) override val spans: List<SemanticSpan>,
    @ProtoNumber(3) override val style: CssStyle,
    @ProtoNumber(4) override val elementId: String?,
    @ProtoNumber(5) override val cfi: String?,
    @ProtoNumber(6) override val startCharOffsetInSource: Int = 0,
    @ProtoNumber(7) override val blockIndex: Int = 0
) : SemanticTextBlock

@Serializable
data class SemanticHeader(
    @ProtoNumber(1) val level: Int,
    @ProtoNumber(2) override val text: String,
    @ProtoNumber(3) override val spans: List<SemanticSpan>,
    @ProtoNumber(4) override val style: CssStyle,
    @ProtoNumber(5) override val elementId: String?,
    @ProtoNumber(6) override val cfi: String?,
    @ProtoNumber(7) override val startCharOffsetInSource: Int = 0,
    @ProtoNumber(8) override val blockIndex: Int = 0
) : SemanticTextBlock

@Serializable
data class SemanticListItem(
    @ProtoNumber(1) override val text: String,
    @ProtoNumber(2) override val spans: List<SemanticSpan>,
    @ProtoNumber(3) override val style: CssStyle,
    @ProtoNumber(4) override val elementId: String?,
    @ProtoNumber(5) override val cfi: String?,
    @ProtoNumber(6) override val startCharOffsetInSource: Int = 0,
    @ProtoNumber(7) val itemMarkerImage: String?,
    @ProtoNumber(8) override val blockIndex: Int = 0
) : SemanticTextBlock

@Serializable
data class SemanticList(
    @ProtoNumber(1) val items: List<SemanticListItem>,
    @ProtoNumber(2) val isOrdered: Boolean,
    @ProtoNumber(3) override val style: CssStyle,
    @ProtoNumber(4) override val elementId: String?,
    @ProtoNumber(5) override val cfi: String?,
    @ProtoNumber(6) override val blockIndex: Int = 0
) : SemanticBlock

@Serializable
data class SemanticImage(
    @ProtoNumber(1) val path: String, // Will store the absolute path
    @ProtoNumber(2) val altText: String?,
    @ProtoNumber(3) val intrinsicWidth: Float?,
    @ProtoNumber(4) val intrinsicHeight: Float?,
    @ProtoNumber(5) override val style: CssStyle,
    @ProtoNumber(6) override val elementId: String?,
    @ProtoNumber(7) override val cfi: String?,
    @ProtoNumber(8) override val blockIndex: Int = 0
) : SemanticBlock

@Serializable
data class SemanticMath(
    @ProtoNumber(1) val svgContent: String?,
    @ProtoNumber(2) val altText: String?,
    @ProtoNumber(3) val svgWidth: String?,
    @ProtoNumber(4) val svgHeight: String?,
    @ProtoNumber(5) val svgViewBox: String?,
    @ProtoNumber(6) val isFromMathJax: Boolean,
    @ProtoNumber(7) override val style: CssStyle,
    @ProtoNumber(8) override val elementId: String?,
    @ProtoNumber(9) override val cfi: String?,
    @ProtoNumber(10) override val blockIndex: Int = 0
) : SemanticBlock

@Serializable
data class SemanticSpacer(
    @ProtoNumber(1) override val style: CssStyle,
    @ProtoNumber(2) override val elementId: String?,
    @ProtoNumber(3) override val cfi: String?,
    @ProtoNumber(4) val isExplicitLineBreak: Boolean = false,
    @ProtoNumber(5) override val blockIndex: Int = 0
) : SemanticBlock

@Serializable
data class SemanticTableCell(
    @ProtoNumber(1) val content: List<SemanticBlock>,
    @ProtoNumber(2) val isHeader: Boolean,
    @ProtoNumber(3) val colspan: Int,
    @ProtoNumber(4) val style: CssStyle
)

@Serializable
data class SemanticTable(
    @ProtoNumber(1) val rows: List<List<SemanticTableCell>>,
    @ProtoNumber(2) override val style: CssStyle,
    @ProtoNumber(3) override val elementId: String?,
    @ProtoNumber(4) override val cfi: String?,
    @ProtoNumber(5) override val blockIndex: Int = 0
) : SemanticBlock

@Serializable
data class SemanticFlexContainer(
    @ProtoNumber(1) val children: List<SemanticBlock>,
    @ProtoNumber(2) override val style: CssStyle,
    @ProtoNumber(3) override val elementId: String?,
    @ProtoNumber(4) override val cfi: String?,
    @ProtoNumber(5) override val blockIndex: Int = 0
) : SemanticBlock

@Serializable
data class SemanticWrappingBlock(
    @ProtoNumber(1) val floatedImage: SemanticImage,
    @ProtoNumber(2) val paragraphsToWrap: List<SemanticParagraph>,
    @ProtoNumber(3) override val style: CssStyle,
    @ProtoNumber(4) override val elementId: String?,
    @ProtoNumber(5) override val cfi: String?,
    @ProtoNumber(6) override val blockIndex: Int = 0
) : SemanticBlock

val semanticBlockModule = SerializersModule {
    polymorphic(SemanticBlock::class) {
        subclass(SemanticParagraph::class)
        subclass(SemanticHeader::class)
        subclass(SemanticListItem::class)
        subclass(SemanticList::class)
        subclass(SemanticImage::class)
        subclass(SemanticMath::class)
        subclass(SemanticSpacer::class)
        subclass(SemanticTable::class)
        subclass(SemanticFlexContainer::class)
        subclass(SemanticWrappingBlock::class)
    }
}