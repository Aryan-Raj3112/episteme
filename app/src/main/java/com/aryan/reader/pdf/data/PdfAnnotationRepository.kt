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
package com.aryan.reader.pdf.data

import android.content.Context
import com.aryan.reader.logCloudAnnotationSyncTrace
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSidecarCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class PdfAnnotationRepository(private val context: Context) {

    private fun getFile(bookId: String): File {
        val safeBookId = bookId.replace("/", "_")
        val dir = File(context.filesDir, "annotations")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "annotation_$safeBookId.json")
    }

    private fun getDeletedFile(bookId: String): File {
        val safeBookId = bookId.replace("/", "_")
        val dir = File(context.filesDir, "annotations")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "deleted_annotation_$safeBookId.json")
    }

    suspend fun saveAnnotations(bookId: String, annotations: Map<Int, List<PdfAnnotation>>) {
        withContext(Dispatchers.IO) {
            try {
                Timber.tag("AnnotationSync").d("Start saving local JSON for $bookId. Count: ${annotations.size}")

                if (annotations.isEmpty()) {
                    val file = getFile(bookId)
                    if (file.exists()) file.delete()
                    return@withContext
                }

                val json = AnnotationSerializer.toJson(annotations)
                val file = getFile(bookId)
                if (file.exists() && file.readText() == json) {
                    logCloudAnnotationSyncTrace {
                        "android.repository.save_ink_noop book=$bookId count=${annotations.values.sumOf { it.size }} " +
                            "bytes=${file.length()} ts=${file.lastModified()}"
                    }
                    Timber.tag("AnnotationSync").d("Skipping unchanged annotation JSON for $bookId.")
                    return@withContext
                }
                file.writeText(json)
                logCloudAnnotationSyncTrace {
                    "android.repository.save_ink book=$bookId count=${annotations.values.sumOf { it.size }} " +
                        "bytes=${file.length()} ts=${file.lastModified()}"
                }

                Timber.tag("AnnotationSync").d("Finished saving local JSON for $bookId. Path: ${file.absolutePath}, Size: ${file.length()}")
            } catch (e: Exception) {
                Timber.tag("AnnotationSync").e(e, "Failed to save local annotations")
            }
        }
    }

    suspend fun loadAnnotations(bookId: String): Map<Int, List<PdfAnnotation>> {
        return withContext(Dispatchers.IO) {
            try {
                val file = getFile(bookId)
                if (file.exists()) {
                    val json = file.readText()
                    Timber.tag("AnnotationSync").d("Loaded local JSON for $bookId. Size: ${file.length()}")
                    AnnotationSerializer.fromJson(json)
                } else {
                    Timber.tag("AnnotationSync").d("No local annotation file found for $bookId")
                    emptyMap()
                }
            } catch (e: Exception) {
                Timber.tag("AnnotationSync").e(e, "Failed to load local annotations")
                emptyMap()
            }
        }
    }

    fun getAnnotationFileForSync(bookId: String): File? {
        val file = getFile(bookId)
        val valid = file.exists() && file.length() > 0

        Timber.tag("AnnotationSync").d("Checking file for sync: $bookId. Exists: ${file.exists()}, Size: ${file.length()} bytes. Valid: $valid")

        return if (valid) file else null
    }

    suspend fun markAnnotationsDeleted(
        bookId: String,
        annotationIds: Collection<String>,
        deletedAt: Long = System.currentTimeMillis()
    ) {
        val ids = annotationIds.mapNotNull { it.takeIf(String::isNotBlank) }.toSet()
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            val file = getDeletedFile(bookId)
            val existing = if (file.isFile) {
                SharedPdfAnnotationSidecarCodec.annotationDeletionsFromJson(file.readText())
            } else {
                emptyMap()
            }
            val next = existing.toMutableMap()
            ids.forEach { id -> next[id] = maxOf(next[id] ?: 0L, deletedAt) }
            val json = SharedPdfAnnotationSidecarCodec.annotationDeletionsJson(next)
            if (file.isFile && file.readText() == json) return@withContext
            file.writeText(json)
            logCloudAnnotationSyncTrace {
                "android.repository.mark_deleted_ink book=$bookId ids=${ids.sorted()} " +
                    "bytes=${file.length()} ts=${file.lastModified()}"
            }
        }
    }

    suspend fun replaceDeletedAnnotations(
        bookId: String,
        deletions: Map<String, Long>,
        timestamp: Long? = null
    ) {
        withContext(Dispatchers.IO) {
            val file = getDeletedFile(bookId)
            if (deletions.isEmpty()) {
                if (file.exists()) file.delete()
                return@withContext
            }
            val json = SharedPdfAnnotationSidecarCodec.annotationDeletionsJson(deletions)
            if (!file.isFile || file.readText() != json) {
                file.writeText(json)
            }
            timestamp?.takeIf { it > 0L }?.let(file::setLastModified)
            logCloudAnnotationSyncTrace {
                "android.repository.replace_deleted_ink book=$bookId count=${deletions.size} " +
                    "bytes=${file.length()} ts=${file.lastModified()}"
            }
        }
    }

    fun getDeletedAnnotationsFileForSync(bookId: String): File? {
        val file = getDeletedFile(bookId)
        val deletions = if (file.isFile) {
            SharedPdfAnnotationSidecarCodec.annotationDeletionsFromJson(file.readText())
        } else {
            emptyMap()
        }
        return if (deletions.isNotEmpty()) file else null
    }
}
