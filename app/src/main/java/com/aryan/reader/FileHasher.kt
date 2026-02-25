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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

object FileHasher {
    /**
     * Calculates the SHA-256 hash of an input stream.
     * @param inputStreamProvider A lambda that provides the InputStream. This is important to ensure
     * the stream is opened on the correct thread.
     * @return The SHA-256 hash as a hex string, or null if an error occurs.
     */
    suspend fun calculateSha256(inputStreamProvider: () -> InputStream?): String? = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            inputStreamProvider()?.use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            } ?: return@withContext null // Return null if stream provider returns null

            // Convert byte array to hex string
            val hashBytes = digest.digest()
            val hexString = StringBuilder()
            for (byte in hashBytes) {
                hexString.append(String.format("%02x", byte))
            }
            hexString.toString()
        } catch (e: Exception) {
            // In a real app, you'd want to log this error
            e.printStackTrace()
            null
        }
    }
}