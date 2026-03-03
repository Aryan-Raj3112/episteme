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
import android.view.KeyEvent
import android.view.View
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aryan.reader.RenderMode

@Composable
fun EpubReaderSystemUiController(
    window: Window?,
    view: View,
    showBars: Boolean,
    initialIsAppearanceLightStatusBars: Boolean,
    initialSystemBarsBehavior: Int
) {
    val isDarkTheme = isSystemInDarkTheme()

    // 1. Handle Immersive Mode (Enter/Exit)
    DisposableEffect(window, view, initialIsAppearanceLightStatusBars, initialSystemBarsBehavior) {
        if (window == null) {
            Timber.w("Window is null, cannot control system UI.")
            return@DisposableEffect onDispose {}
        }
        val insetsController = WindowCompat.getInsetsController(window, view)
        Timber.d("Applying immersive mode.")

        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController.hide(WindowInsetsCompat.Type.navigationBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            Timber.d("Restoring system UI.")
            WindowCompat.setDecorFitsSystemWindows(window, true)
            insetsController.show(WindowInsetsCompat.Type.navigationBars())
            insetsController.isAppearanceLightStatusBars = initialIsAppearanceLightStatusBars
            insetsController.systemBarsBehavior = initialSystemBarsBehavior
        }
    }

    // 2. Handle Status Bar Appearance (Dark/Light theme)
    LaunchedEffect(window, view, isDarkTheme) {
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    // 3. Handle Show/Hide Bars dynamically
    LaunchedEffect(showBars, window, view) {
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            if (showBars) {
                insetsController.show(WindowInsetsCompat.Type.navigationBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }
}

fun Modifier.volumeScrollHandler(
    volumeScrollEnabled: Boolean,
    renderMode: RenderMode,
    isTtsActive: Boolean,
    isMusicActive: Boolean,
    currentScrollY: Int,
    currentScrollHeight: Int,
    currentClientHeight: Int,
    currentChapterIndex: Int,
    totalChapters: Int,
    onScrollBy: (Int) -> Unit,
    onNavigateChapter: (offset: Int, scrollTarget: ChapterScrollPosition) -> Unit,
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {}
): Modifier = this.onPreviewKeyEvent { keyEvent ->
    val shouldHandle = volumeScrollEnabled &&
            !isTtsActive &&
            !isMusicActive

    if (!shouldHandle) return@onPreviewKeyEvent false

    val isVolumeKey = keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_VOLUME_UP

    if (!isVolumeKey) return@onPreviewKeyEvent false

    if (keyEvent.type == KeyEventType.KeyDown) {
        val isNext = keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        if (renderMode == RenderMode.VERTICAL_SCROLL) {
            val direction = if (isNext) 1 else -1
            val isAtBottom = (currentScrollY + currentClientHeight) >= (currentScrollHeight - 2)

            if (direction == -1 && currentScrollY == 0) {
                if (currentChapterIndex > 0) onNavigateChapter(-1, ChapterScrollPosition.END)
            } else if (direction == 1 && isAtBottom) {
                if (currentChapterIndex < totalChapters - 1) onNavigateChapter(1, ChapterScrollPosition.START)
            } else {
                val scrollAmount = (currentClientHeight * 0.25).toInt() * direction
                onScrollBy(scrollAmount)
            }
        } else {
            if (isNext) onNextPage() else onPrevPage()
        }
    }
    true
}