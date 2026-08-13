/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
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
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.epub

import android.graphics.Bitmap
import com.aryan.reader.shared.reader.MobileEpubPageTarget
import com.aryan.reader.shared.reader.MobileEpubTocEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File
import com.aryan.reader.shared.reader.mobileEpubContentFilePath
import com.aryan.reader.shared.reader.isMobileEpubExtractionCacheReadable

typealias EpubTocEntry = MobileEpubTocEntry
typealias EpubPageTarget = MobileEpubPageTarget

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
    val chaptersForPagination: List<EpubChapter> = chapters,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val description: String? = null,
)

fun epubContentFilePath(path: String): String = mobileEpubContentFilePath(path)

fun EpubChapter.contentFilePath(): String = epubContentFilePath(htmlFilePath)

fun EpubBook.hasReadableExtractedContent(): Boolean {
    val hasBasePath = extractionBasePath.isNotBlank()
    val extractionDir = File(extractionBasePath)
    val hasDirectory = hasBasePath && extractionDir.isDirectory
    return isMobileEpubExtractionCacheReadable(
        extractionBasePathPresent = hasBasePath,
        extractionDirectoryPresent = hasDirectory,
        chapterCount = chapters.size,
        extractionDirectoryHasEntries = hasDirectory && extractionDir.list()?.isNotEmpty() == true,
        allChapterFilesPresent = hasDirectory && chapters.all { chapter ->
            File(extractionDir, chapter.contentFilePath()).isFile
        }
    )
}
