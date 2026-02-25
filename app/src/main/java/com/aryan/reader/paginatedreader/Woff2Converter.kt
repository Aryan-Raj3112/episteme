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

object Woff2Converter {

    init {
        System.loadLibrary("native-lib")
    }

    /**
     * Converts a WOFF2 font file into a TTF font file.
     *
     * @param woff2Data The raw byte array of the WOFF2 file.
     * @return A byte array of the converted TTF file, or null if conversion fails.
     */
    external fun convertWoff2ToTtf(woff2Data: ByteArray): ByteArray?
}