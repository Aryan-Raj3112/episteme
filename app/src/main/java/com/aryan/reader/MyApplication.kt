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
package com.aryan.reader

import android.app.Application
import android.webkit.WebView
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.aryan.reader.paginatedreader.SvgStringFetcher
import timber.log.Timber // Add this

class MyApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    override fun newImageLoader(): ImageLoader {
        Timber.d("MyApplication: Creating custom ImageLoader with SvgStringFetcher.")
        return ImageLoader.Builder(this)
            .components {
                add(SvgStringFetcher.Factory())
                add(SvgDecoder.Factory())
            }
            .build()
    }
}