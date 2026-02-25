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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EpubChapter @OptIn(ExperimentalSerializationApi::class) constructor(
    @ProtoNumber(1) val chapterId: String,
    @ProtoNumber(2) val absPath: String,
    @ProtoNumber(3) val title: String,
    @ProtoNumber(4) val htmlFilePath: String,
    @ProtoNumber(5) val plainTextContent: String,
    @ProtoNumber(6) val htmlContent: String,
    @ProtoNumber(7) val depth: Int = 0,
    @ProtoNumber(8) val isInToc: Boolean = true
)