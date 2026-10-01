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
package com.aryan.reader.epubreader

import timber.log.Timber
import android.webkit.JavascriptInterface
import androidx.compose.ui.unit.dp
import org.json.JSONObject

val DRAG_TO_CHANGE_CHAPTER_THRESHOLD_DP = 100.dp

enum class ChapterScrollPosition {
    START, END
}

data class SelectionRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String
)

interface TextSelectionListener {
    fun onTextSelected(rect: SelectionRect)
    fun onSelectionCleared()
}

@Suppress("unused")
class TextSelectionJsInterface(private val listener: TextSelectionListener) {
    @JavascriptInterface
    fun onTextSelected(rectJson: String) {
        try {
            val json = JSONObject(rectJson)
            val rect = SelectionRect(
                x = json.getDouble("x").toFloat(),
                y = json.getDouble("y").toFloat(),
                width = json.getDouble("width").toFloat(),
                height = json.getDouble("height").toFloat(),
                text = json.getString("text")
            )
            listener.onTextSelected(rect)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing selection rect JSON")
        }
    }

    @JavascriptInterface
    fun onSelectionCleared() {
        listener.onSelectionCleared()
    }
}

class PageInfoBridge(
    private val onUpdate: (scrollY: Int, scrollHeight: Int, clientHeight: Int, activeFragmentId: String?) -> Unit
) {
    @JavascriptInterface
    fun updateScrollState(scrollY: Int, scrollHeight: Int, clientHeight: Int, activeFragmentId: String?) {
        val fragment = if (activeFragmentId == "null" || activeFragmentId.isNullOrBlank()) null else activeFragmentId
        Timber.tag("FRAG_NAV_DEBUG").d("Bridge received fragmentId: $fragment")
        onUpdate(scrollY, scrollHeight, clientHeight, fragment)
    }
}