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
package com.aryan.reader.paginatedreader

import timber.log.Timber
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer

/**
 * A custom data class to wrap raw SVG string content.
 * This avoids conflicts with Coil's default String fetcher.
 */
data class SvgData(val content: String)

/**
 * A custom Coil Fetcher that handles loading SVG data from our [SvgData] class.
 */
class SvgStringFetcher(
    private val options: Options,
    private val data: SvgData,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        Timber.d("SvgStringFetcher: fetching SVG data from SvgData object.")
        val buffer = Buffer().writeUtf8(data.content)
        return SourceResult(
            source = ImageSource(buffer, options.context),
            mimeType = "image/svg+xml",
            dataSource = DataSource.MEMORY
        )
    }

    class Factory : Fetcher.Factory<SvgData> {
        override fun create(data: SvgData, options: Options, imageLoader: coil.ImageLoader): Fetcher {
            Timber.d("SvgStringFetcher.Factory: create called for SvgData.")
            return SvgStringFetcher(options, data)
        }
    }
}