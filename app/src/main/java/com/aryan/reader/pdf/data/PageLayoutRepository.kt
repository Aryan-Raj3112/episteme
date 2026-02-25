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
package com.aryan.reader.pdf.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import timber.log.Timber

sealed class VirtualPage {
    data class PdfPage(val pdfIndex: Int) : VirtualPage()
    data class BlankPage(val id: String, val width: Int, val height: Int, val wasManuallyAdded: Boolean = false) : VirtualPage()
}

class PageLayoutRepository(private val context: Context) {
    private fun getFile(bookId: String): File {
        val safeId = bookId.replace("/", "_")
        val dir = File(context.filesDir, "page_layouts")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "layout_$safeId.json")
    }

    suspend fun saveLayout(bookId: String, pages: List<VirtualPage>) = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray()
        pages.forEach { page ->
            val obj = JSONObject()
            when (page) {
                is VirtualPage.PdfPage -> {
                    obj.put("type", "pdf")
                    obj.put("index", page.pdfIndex)
                }
                is VirtualPage.BlankPage -> {
                    obj.put("type", "blank")
                    obj.put("id", page.id)
                    obj.put("w", page.width)
                    obj.put("h", page.height)
                    obj.put("manual", page.wasManuallyAdded)
                }
            }
            jsonArray.put(obj)
        }
        getFile(bookId).writeText(jsonArray.toString())
    }

    suspend fun loadLayout(bookId: String, totalPdfPages: Int): List<VirtualPage> = withContext(Dispatchers.IO) {
        val file = getFile(bookId)
        if (!file.exists()) {
            return@withContext (0 until totalPdfPages).map { VirtualPage.PdfPage(it) }
        }

        try {
            val json = file.readText()
            val array = JSONArray(json)
            val list = mutableListOf<VirtualPage>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val type = obj.optString("type", "pdf")
                if (type == "pdf") {
                    list.add(VirtualPage.PdfPage(obj.getInt("index")))
                } else {
                    val w = obj.optInt("w", 595)
                    val h = obj.optInt("h", 842)
                    val isManual = obj.optBoolean("manual", false)
                    list.add(VirtualPage.BlankPage(obj.getString("id"), w, h, isManual))
                }
            }
            list
        } catch (_: Exception) {
            (0 until totalPdfPages).map { VirtualPage.PdfPage(it) }
        }
    }

    suspend fun getLayoutOrNull(bookId: String): List<VirtualPage>? = withContext(Dispatchers.IO) {
        val file = getFile(bookId)
        Timber.tag("PdfExportDebug").d("PageLayoutRepo: Looking for layout at ${file.absolutePath}")
        Timber.tag("PdfExportDebug").d("PageLayoutRepo: File exists: ${file.exists()}")

        if (!file.exists()) {
            Timber.tag("PdfExportDebug").w("PageLayoutRepo: No layout file for book $bookId")
            return@withContext null
        }

        try {
            val json = file.readText()
            Timber.tag("PdfExportDebug").v("PageLayoutRepo: Layout JSON: ${json.take(300)}")

            val array = JSONArray(json)
            val list = mutableListOf<VirtualPage>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val type = obj.optString("type", "pdf")
                if (type == "pdf") {
                    list.add(VirtualPage.PdfPage(obj.getInt("index")))
                } else {
                    val w = obj.optInt("w", 595)
                    val h = obj.optInt("h", 842)
                    list.add(VirtualPage.BlankPage(obj.getString("id"), w, h))
                }
            }
            Timber.tag("PdfExportDebug").i("PageLayoutRepo: Parsed ${list.size} virtual pages (${
                list.count { it is VirtualPage.PdfPage }
            } PDF, ${list.count { it is VirtualPage.BlankPage }} blank)")
            list
        } catch (e: Exception) {
            Timber.tag("PdfExportDebug").e(e, "PageLayoutRepo: Failed to parse layout")
            null
        }
    }

    fun getLayoutFile(bookId: String): File {
        val safeId = bookId.replace("/", "_")
        val dir = File(context.filesDir, "page_layouts")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "layout_$safeId.json")
        Timber.tag("PdfExportDebug").v("PageLayoutRepo: Layout file path: ${file.absolutePath}")
        return file
    }
}