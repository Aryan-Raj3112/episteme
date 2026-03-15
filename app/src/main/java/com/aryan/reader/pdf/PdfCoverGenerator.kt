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
package com.aryan.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import timber.log.Timber
import androidx.core.graphics.createBitmap
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PdfCoverGenerator"

class PdfCoverGenerator(context: Context) {
    private val appContext = context.applicationContext
    private val pdfiumCore = PdfiumCoreKt(Dispatchers.IO)

    /**
     * Generates a Bitmap cover for the first page of a PDF.
     * This function is safe to call from any thread and performs its work on Dispatchers.IO.
     *
     * @param pdfUri The Uri of the PDF file.
     * @param targetHeight The desired height of the output Bitmap. Width is scaled proportionally.
     * @return A Bitmap of the first page, or null if an error occurs.
     */
    suspend fun generateCover(pdfUri: Uri, targetHeight: Int = 800): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                appContext.contentResolver.openFileDescriptor(pdfUri, "r").use { pfd ->
                    if (pfd == null) {
                        Timber.e("Failed to open ParcelFileDescriptor for URI: $pdfUri")
                        return@withContext null
                    }
                    pdfiumCore.newDocument(pfd).use { doc ->
                        if (doc.getPageCount() == 0) {
                            Timber.w("PDF has no pages, cannot generate cover: $pdfUri")
                            return@withContext null
                        }
                        doc.openPage(0)?.use { page ->
                            val originalWidth = page.getPageWidthPoint()
                            val originalHeight = page.getPageHeightPoint()
                            if (originalWidth <= 0 || originalHeight <= 0) {
                                Timber.e("Invalid page dimensions for cover: $pdfUri")
                                return@withContext null
                            }

                            val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()
                            val targetWidth = (targetHeight * aspectRatio).toInt()

                            if (targetWidth <= 0) {
                                Timber.e("Calculated invalid bitmap width for cover: $targetWidth")
                                return@withContext null
                            }

                            val bitmap = createBitmap(targetWidth, targetHeight)
                            page.renderPageBitmap(
                                bitmap = bitmap,
                                startX = 0, startY = 0,
                                drawSizeX = targetWidth, drawSizeY = targetHeight,
                                renderAnnot = false
                            )
                            bitmap
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error generating PDF cover for URI: $pdfUri")
                null
            }
        }
    }
}