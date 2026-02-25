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
package com.aryan.reader.epub

import android.graphics.Bitmap
import com.aryan.reader.epub.EpubParser.EpubPageTarget
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class EpubTocEntry(
    val label: String,
    val absolutePath: String,
    val fragmentId: String?,
    val depth: Int
)

@Serializable
data class EpubBook(
    val fileName: String,
    val title: String,
    val author: String,
    val language: String,
    @Serializable(with = BitmapSerializer::class) val coverImage: Bitmap?,
    val chapters: List<EpubChapter> = emptyList(),
    val images: List<EpubImage> = emptyList(),
    val pageList: List<EpubPageTarget> = emptyList(),
    val tableOfContents: List<EpubTocEntry> = emptyList(),
    val extractionBasePath: String = "",
    val css: Map<String, String> = emptyMap(),
    @Transient
    val chaptersForPagination: List<EpubChapter> = chapters
)