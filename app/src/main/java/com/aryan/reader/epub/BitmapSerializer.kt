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
import android.graphics.BitmapFactory
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.ByteArrayOutputStream

/**
 * A [KSerializer] for [Bitmap] objects.
 * It serializes the bitmap to a byte array and deserializes it back to a bitmap.
 */
object BitmapSerializer : KSerializer<Bitmap> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Bitmap") {
        element<ByteArray>("bytes")
    }

    override fun serialize(encoder: Encoder, value: Bitmap) {
        val stream = ByteArrayOutputStream()
        value.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        encoder.encodeSerializableValue(ByteArraySerializer(), byteArray)
    }

    override fun deserialize(decoder: Decoder): Bitmap {
        val byteArray = decoder.decodeSerializableValue(ByteArraySerializer())
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }
}

