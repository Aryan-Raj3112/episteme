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
package com.aryan.reader.epubreader

import timber.log.Timber
import android.webkit.JavascriptInterface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import kotlin.math.min

val DRAG_TO_CHANGE_CHAPTER_THRESHOLD_DP = 100.dp
val PAGE_INFO_BAR_HEIGHT = 25.dp

@Composable
fun ChapterChangeIndicator(
    text: String,
    progress: Float,
    isPullingDown: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha = min(1f, progress * 1.5f)
    if (alpha > 0.1f) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .alpha(alpha)
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.5f),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isPullingDown) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(20.dp * min(1f, progress + 0.2f))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (progress >= 1.0f) text else "Pull further... (${(progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

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