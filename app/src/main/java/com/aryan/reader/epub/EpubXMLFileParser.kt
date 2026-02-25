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

import timber.log.Timber
import org.jsoup.Jsoup

/**
 * Parses an XML/HTML file from an EPUB archive, primarily to extract a title
 * and determine the effective path for WebView (including fragments).
 *
 * @property fileRelativePath The relative path of the HTML file within the EPUB's extraction directory.
 * @property data The raw data (content) of the HTML file.
 * @property fragmentId Optional ID of the fragment to link to within the HTML file.
 */
class EpubXMLFileParser(
    val fileRelativePath: String, // e.g., "OEBPS/chapter1.xhtml"
    val data: ByteArray,
    private val fragmentId: String? = null
) {

    companion object {
        private const val TAG = "EpubXMLFileParser"
    }

    /**
     * Represents the output of the parsing.
     *
     * @property title The extracted title of the HTML document (e.g., from h1-h6 tags).
     * @property effectiveHtmlPath The relative path to the HTML file, including any fragment identifier.
     *                             This path is relative to the book's extraction base.
     */
    data class Output(val title: String?, val effectiveHtmlPath: String)

    /**
     * Parses the HTML data to extract a title and construct the effective HTML path.
     *
     * @return [Output] The title and effective HTML path.
     */
    fun parseForTitleAndPath(): Output {
        Timber.d("Parsing for title and path: $fileRelativePath, fragment: $fragmentId")
        val document = Jsoup.parse(data.inputStream(), "UTF-8", "")
        val extractedTitle = document.selectFirst("h1, h2, h3, h4, h5, h6")?.text()?.trim()

        val pathWithFragment = if (fragmentId != null) {
            "$fileRelativePath#$fragmentId"
        } else {
            fileRelativePath
        }
        Timber.d("Effective HTML path: $pathWithFragment for file: $fileRelativePath")

        return Output(
            title = extractedTitle,
            effectiveHtmlPath = pathWithFragment
        )
    }
}