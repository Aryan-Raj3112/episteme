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

import android.annotation.SuppressLint
import android.content.Context
import timber.log.Timber
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.webkit.WebView
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.PopupMenu
import android.webkit.JavascriptInterface
import org.json.JSONObject

enum class DragOperation { NONE, PULLING_DOWN_FROM_TOP, PULLING_UP_FROM_BOTTOM }

internal fun readWebViewHitTestTypeOrNull(hitTestTypeProvider: () -> Int?): Int? {
    return try {
        hitTestTypeProvider()
    } catch (e: NullPointerException) {
        Timber.w(e, "WebView hit test state was unavailable for tap.")
        null
    }
}

internal fun isWebViewAnchorHitTestType(type: Int?): Boolean {
    return type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
        type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
}

@SuppressLint("ViewConstructor")
class InteractiveWebView(
    context: Context,
    private val onSingleTap: () -> Unit,
    private val onPotentialScroll: () -> Unit,
    private val onOverScrollTop: (dragAmount: Float) -> Unit,
    private val onOverScrollBottom: (dragAmount: Float) -> Unit,
    private val onReleaseOverScrollTop: () -> Unit,
    private val onReleaseOverScrollBottom: () -> Unit,
    private val onShowCustomSelectionMenu: (selectedText: String, selectionBounds: Rect, finishActionModeCallback: () -> Unit) -> Unit,
    private val onHideCustomSelectionMenu: () -> Unit
) : WebView(context) {

    companion object {
        private const val DRAG_SENSITIVITY_PX = 20f
        private const val SELECTION_MENU_INITIAL_DELAY_MS = 120L
        private const val SELECTION_MENU_RETRY_DELAY_MS = 140L
        private const val SELECTION_MENU_RETRY_COUNT = 8
    }

    private var startY: Float = 0f
    private var initialDragY: Float = 0f
    private var currentDragOperation: DragOperation = DragOperation.NONE

    private val scrollStopHandler = Handler(Looper.getMainLooper())
    private var scrollStopRunnable: Runnable? = null
    private var selectionMenuRunnable: Runnable? = null
    private var activeSelectionActionMode: ActionMode? = null
    private var selectionMenuShownForActiveMode = false

    init {
        addJavascriptInterface(ReaderSelectionBridge(), "ReaderSelectionBridge")
    }

    private fun clearPendingSelectionWork() {
        scrollStopRunnable?.let { scrollStopHandler.removeCallbacks(it) }
        scrollStopRunnable = null
        selectionMenuRunnable?.let { scrollStopHandler.removeCallbacks(it) }
        selectionMenuRunnable = null
    }

    private fun startLocalSelectionActionMode(scheduleMenu: Boolean = true): ActionMode {
        activeSelectionActionMode?.let { existingMode ->
            if (scheduleMenu) {
                scheduleCustomSelectionMenuFromCurrentSelection(existingMode)
            }
            return existingMode
        }

        lateinit var localMode: ActionMode
        localMode = LocalSelectionActionMode(this) {
            if (activeSelectionActionMode === localMode) {
                activeSelectionActionMode = null
            }
            selectionMenuShownForActiveMode = false
            onHideCustomSelectionMenu()
        }
        selectionMenuShownForActiveMode = false
        activeSelectionActionMode = localMode
        if (scheduleMenu) {
            scheduleCustomSelectionMenuFromCurrentSelection(localMode)
        }
        return localMode
    }

    private fun finishLocalSelectionActionMode() {
        activeSelectionActionMode?.finish()
        activeSelectionActionMode = null
    }

    private fun scheduleCustomSelectionMenuFromCurrentSelection(
        mode: ActionMode,
        delayMs: Long = SELECTION_MENU_INITIAL_DELAY_MS,
        remainingRetries: Int = SELECTION_MENU_RETRY_COUNT
    ) {
        selectionMenuRunnable?.let { scrollStopHandler.removeCallbacks(it) }
        selectionMenuRunnable = Runnable {
            selectionMenuRunnable = null
            showCustomSelectionMenuFromCurrentSelection(mode, remainingRetries)
        }
        scrollStopHandler.postDelayed(selectionMenuRunnable!!, delayMs)
    }

    private fun scheduleSelectionActionModeRefreshAfterTouch(
        delayMs: Long = SELECTION_MENU_INITIAL_DELAY_MS,
        remainingRetries: Int = SELECTION_MENU_RETRY_COUNT
    ) {
        selectionMenuRunnable?.let { scrollStopHandler.removeCallbacks(it) }
        selectionMenuRunnable = Runnable {
            selectionMenuRunnable = null
            activeSelectionActionMode?.let { mode ->
                showCustomSelectionMenuFromCurrentSelection(mode, SELECTION_MENU_RETRY_COUNT)
                return@Runnable
            }
            evaluateJavascript("(function() { var s = window.getSelection && window.getSelection(); return s ? s.toString().trim() : ''; })();") { result ->
                val selectedText = result?.removeSurrounding("\"")
                if (!selectedText.isNullOrBlank() && activeSelectionActionMode == null) {
                    Timber.d("CustomSelection: selection exists after touch-up. Starting local action mode.")
                    startLocalSelectionActionMode()
                } else {
                    activeSelectionActionMode?.let { mode ->
                        showCustomSelectionMenuFromCurrentSelection(mode, SELECTION_MENU_RETRY_COUNT)
                        return@evaluateJavascript
                    }
                    if (remainingRetries > 0) {
                        scheduleSelectionActionModeRefreshAfterTouch(
                            delayMs = SELECTION_MENU_RETRY_DELAY_MS,
                            remainingRetries = remainingRetries - 1
                        )
                    }
                }
            }
        }
        scrollStopHandler.postDelayed(selectionMenuRunnable!!, delayMs)
    }

    private fun showCustomSelectionMenuFromCurrentSelection(
        mode: ActionMode,
        remainingRetries: Int
    ) {
        val jsToGetSelectionDetails = """
            (function() {
                var selection = window.getSelection();
                var selectedText = selection.toString().trim();
                if (selectedText.length === 0 || selection.rangeCount === 0) {
                    return null;
                }
                var range = selection.getRangeAt(0);
                var viewportLeft = 0;
                var viewportTop = 0;
                var viewportRight = window.innerWidth || document.documentElement.clientWidth || 0;
                var viewportBottom = window.innerHeight || document.documentElement.clientHeight || 0;
                var rects = Array.prototype.slice.call(range.getClientRects ? range.getClientRects() : []);
                rects = rects.filter(function(rect) {
                    if (!rect || rect.width <= 0 || rect.height <= 0) return false;
                    return rect.right >= viewportLeft &&
                        rect.left <= viewportRight &&
                        rect.bottom >= viewportTop &&
                        rect.top <= viewportBottom;
                });
                var rect = null;
                if (rects.length > 0) {
                    var firstRect = rects[0];
                    rect = rects.reduce(function(acc, item) {
                        return {
                            left: Math.min(acc.left, item.left),
                            top: Math.min(acc.top, item.top),
                            right: Math.max(acc.right, item.right),
                            bottom: Math.max(acc.bottom, item.bottom)
                        };
                    }, {
                        left: firstRect.left,
                        top: firstRect.top,
                        right: firstRect.right,
                        bottom: firstRect.bottom
                    });
                    rect.width = rect.right - rect.left;
                    rect.height = rect.bottom - rect.top;
                } else {
                    rect = range.getBoundingClientRect ? range.getBoundingClientRect() : null;
                    if (!rect || rect.width <= 0 || rect.height <= 0) {
                        return null;
                    }
                }

                if (rect.width <= 0 || rect.height <= 0) {
                    return null;
                }

                return JSON.stringify({
                    text: selectedText,
                    left: rect.left,
                    top: rect.top,
                    right: rect.right,
                    bottom: rect.bottom,
                    width: rect.width,
                    height: rect.height
                });
            })();
        """.trimIndent()

        evaluateJavascript(jsToGetSelectionDetails) { jsonResult ->
            fun retryOrFinish(message: String) {
                Timber.d(message)
                if (selectionMenuShownForActiveMode) {
                    return
                }
                if (activeSelectionActionMode === mode && remainingRetries > 0) {
                    scheduleCustomSelectionMenuFromCurrentSelection(
                        mode = mode,
                        delayMs = SELECTION_MENU_RETRY_DELAY_MS,
                        remainingRetries = remainingRetries - 1
                    )
                } else {
                    mode.finish()
                }
            }

            if (activeSelectionActionMode !== mode) {
                return@evaluateJavascript
            }

            if (jsonResult == null || jsonResult == "null" || jsonResult.equals("\"null\"", ignoreCase = true)) {
                retryOrFinish("CustomSelection: JS returned null or invalid for selection details. Retries left: $remainingRetries")
                return@evaluateJavascript
            }

            try {
                val unquotedJsonResult = jsonResult.removeSurrounding("\"")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")

                val selectionDetails = JSONObject(unquotedJsonResult)
                val selectedText = selectionDetails.getString("text")

                if (selectedText.isBlank()) {
                    retryOrFinish("CustomSelection: Selected text is blank after JS processing. Retries left: $remainingRetries")
                    return@evaluateJavascript
                }

                val jsLeft = selectionDetails.getDouble("left")
                val jsTop = selectionDetails.getDouble("top")
                val jsRight = selectionDetails.getDouble("right")
                val jsBottom = selectionDetails.getDouble("bottom")
                val jsWidth = selectionDetails.getDouble("width")
                val jsHeight = selectionDetails.getDouble("height")

                if (jsWidth == 0.0 && jsHeight == 0.0) {
                    retryOrFinish("CustomSelection: JS returned a zero-area rect. Retries left: $remainingRetries. Left: $jsLeft, Top: $jsTop")
                    return@evaluateJavascript
                }

                val density = context.resources.displayMetrics.density

                val webViewLocation = IntArray(2)
                getLocationOnScreen(webViewLocation)
                val webViewX = webViewLocation[0]
                val webViewY = webViewLocation[1]

                val selectionRectScreen = Rect(
                    (webViewX + jsLeft * density).toInt(),
                    (webViewY + jsTop * density).toInt(),
                    (webViewX + jsRight * density).toInt(),
                    (webViewY + jsBottom * density).toInt()
                )

                if (selectionRectScreen.isEmpty || selectionRectScreen.width() <= 0 || selectionRectScreen.height() <= 0) {
                    retryOrFinish("CustomSelection: Calculated selectionRectScreen is empty or invalid: $selectionRectScreen. Retries left: $remainingRetries. JS LTRB: $jsLeft, $jsTop, $jsRight, $jsBottom. WebViewLoc: $webViewX, $webViewY")
                    return@evaluateJavascript
                }

                Timber.d("CustomSelection: Selected text: '$selectedText', JS Rect: {L:$jsLeft, T:$jsTop, R:$jsRight, B:$jsBottom}, Screen Rect: $selectionRectScreen")

                selectionMenuShownForActiveMode = true
                onShowCustomSelectionMenu(selectedText, selectionRectScreen) {
                    mode.finish()
                }
            } catch (e: Exception) {
                Timber.e(e, "CustomSelection: Error parsing selection details from JS: '$jsonResult', raw: '$jsonResult'")
                if (selectionMenuShownForActiveMode) {
                    return@evaluateJavascript
                }
                if (remainingRetries > 0) {
                    scheduleCustomSelectionMenuFromCurrentSelection(
                        mode = mode,
                        delayMs = SELECTION_MENU_RETRY_DELAY_MS,
                        remainingRetries = remainingRetries - 1
                    )
                } else {
                    mode.finish()
                }
            }
        }
    }

    private fun showCustomSelectionMenuFromSelectionDetailsJson(
        mode: ActionMode,
        rawJson: String
    ) {
        if (activeSelectionActionMode !== mode) return
        selectionMenuRunnable?.let { scrollStopHandler.removeCallbacks(it) }
        selectionMenuRunnable = null

        try {
            val selectionDetails = JSONObject(rawJson)
            val selectedText = selectionDetails.getString("text")
            if (selectedText.isBlank()) {
                return
            }

            val jsLeft = selectionDetails.getDouble("left")
            val jsTop = selectionDetails.getDouble("top")
            val jsRight = selectionDetails.getDouble("right")
            val jsBottom = selectionDetails.getDouble("bottom")
            val jsWidth = selectionDetails.getDouble("width")
            val jsHeight = selectionDetails.getDouble("height")

            if (jsWidth <= 0.0 || jsHeight <= 0.0) {
                return
            }

            val density = context.resources.displayMetrics.density
            val webViewLocation = IntArray(2)
            getLocationOnScreen(webViewLocation)
            val webViewX = webViewLocation[0]
            val webViewY = webViewLocation[1]

            val selectionRectScreen = Rect(
                (webViewX + jsLeft * density).toInt(),
                (webViewY + jsTop * density).toInt(),
                (webViewX + jsRight * density).toInt(),
                (webViewY + jsBottom * density).toInt()
            )

            if (selectionRectScreen.isEmpty || selectionRectScreen.width() <= 0 || selectionRectScreen.height() <= 0) {
                Timber.d("CustomSelection: Bridge supplied invalid rect: $selectionRectScreen. JS LTRB: $jsLeft, $jsTop, $jsRight, $jsBottom")
                return
            }

            Timber.d("CustomSelection: Bridge selected text '$selectedText', Screen Rect: $selectionRectScreen")
            selectionMenuShownForActiveMode = true
            onShowCustomSelectionMenu(selectedText, selectionRectScreen) {
                mode.finish()
            }
        } catch (e: Exception) {
            Timber.e(e, "CustomSelection: Error parsing bridge selection details: '$rawJson'")
        }
    }

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                Timber.d("onSingleTapConfirmed")

                val type = readWebViewHitTestTypeOrNull {
                    this@InteractiveWebView.hitTestResult?.type
                }

                if (isWebViewAnchorHitTestType(type)) {
                    Timber.d("Tap was on a link. Consuming tap, not toggling app bars.")
                    return true
                }

                onSingleTap()
                return true
            }
        })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        var overscrollEventHandled = false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                onPotentialScroll()

                val deltaYSinceActionDown = event.y - startY
                val oldDragOperation = currentDragOperation

                if (currentDragOperation == DragOperation.PULLING_DOWN_FROM_TOP) {
                    val dragDistance = event.y - initialDragY
                    onOverScrollTop(dragDistance.coerceAtLeast(0f))
                    overscrollEventHandled = true
                } else if (currentDragOperation == DragOperation.PULLING_UP_FROM_BOTTOM) {
                    val dragDistance = initialDragY - event.y
                    onOverScrollBottom(dragDistance.coerceAtLeast(0f))
                    overscrollEventHandled = true
                } else {
                    if (deltaYSinceActionDown > DRAG_SENSITIVITY_PX && !canScrollVertically(-1)) {
                        currentDragOperation = DragOperation.PULLING_DOWN_FROM_TOP
                        initialDragY = event.y
                        onOverScrollTop(0f)
                        overscrollEventHandled = true
                    } else if (deltaYSinceActionDown < -DRAG_SENSITIVITY_PX && !canScrollVertically(
                            1
                        )
                    ) {
                        currentDragOperation = DragOperation.PULLING_UP_FROM_BOTTOM
                        initialDragY = event.y
                        onOverScrollBottom(0f)
                        overscrollEventHandled = true
                    }
                }

                if (currentDragOperation != DragOperation.NONE && oldDragOperation == DragOperation.NONE) {
                    Timber.d("Drag operation started ($currentDragOperation), disabling text selection.")
                    evaluateJavascript(
                        "javascript:if(window.setTextSelectionEnabled) window.setTextSelectionEnabled(false);",
                        null
                    )

                    val cancelEvent = MotionEvent.obtain(event)
                    cancelEvent.action = MotionEvent.ACTION_CANCEL
                    super.onTouchEvent(cancelEvent)
                    cancelEvent.recycle()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDragging = currentDragOperation != DragOperation.NONE

                if (currentDragOperation == DragOperation.PULLING_DOWN_FROM_TOP) {
                    onReleaseOverScrollTop()
                    overscrollEventHandled = true
                } else if (currentDragOperation == DragOperation.PULLING_UP_FROM_BOTTOM) {
                    onReleaseOverScrollBottom()
                    overscrollEventHandled = true
                }

                currentDragOperation = DragOperation.NONE

                if (wasDragging) {
                    Timber.d("Drag operation ended, enabling text selection.")
                    evaluateJavascript("javascript:if(window.setTextSelectionEnabled) window.setTextSelectionEnabled(true);", null)
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    scheduleSelectionActionModeRefreshAfterTouch()
                }
            }
        }

        parent?.requestDisallowInterceptTouchEvent(currentDragOperation != DragOperation.NONE)

        if (overscrollEventHandled) {
            return true
        }
        return super.onTouchEvent(event)
    }

    // MIUI can crash inside FloatingToolbar when WindowInsets are null, so WebView
    // selections use the app's Compose popup without starting the platform toolbar.
    override fun startActionMode(originalCallback: ActionMode.Callback): ActionMode? {
        Timber.d("CustomSelection: handling primary action mode locally.")
        return startLocalSelectionActionMode()
    }

    override fun startActionMode(originalCallback: ActionMode.Callback, type: Int): ActionMode? {
        Timber.d("CustomSelection: handling action mode locally. Type: $type")
        return startLocalSelectionActionMode()
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)

        clearPendingSelectionWork()
        scrollStopRunnable = Runnable {
            evaluateJavascript("(function() { return window.getSelection().toString(); })();") { result ->
                val selectedText = result?.removeSurrounding("\"")
                if (!selectedText.isNullOrBlank()) {
                    Timber.d("Selection exists after scroll. Restarting action mode.")
                    startLocalSelectionActionMode()
                }
            }
        }
        scrollStopRunnable?.let { scrollStopHandler.postDelayed(it, 250) }
    }

    override fun onDetachedFromWindow() {
        clearPendingSelectionWork()
        finishLocalSelectionActionMode()
        super.onDetachedFromWindow()
    }

    override fun destroy() {
        clearPendingSelectionWork()
        finishLocalSelectionActionMode()
        super.destroy()
    }

    private class LocalSelectionActionMode(
        anchorView: View,
        private val onFinished: () -> Unit
    ) : ActionMode() {
        private val modeContext = anchorView.context
        private val menu: Menu = PopupMenu(modeContext, anchorView).menu
        private val menuInflater = MenuInflater(modeContext)
        private var title: CharSequence? = null
        private var subtitle: CharSequence? = null
        private var customView: View? = null
        private var finished = false

        override fun setTitle(title: CharSequence?) {
            this.title = title
        }

        override fun setTitle(resId: Int) {
            title = modeContext.getText(resId)
        }

        override fun setSubtitle(subtitle: CharSequence?) {
            this.subtitle = subtitle
        }

        override fun setSubtitle(resId: Int) {
            subtitle = modeContext.getText(resId)
        }

        override fun setCustomView(view: View?) {
            customView = view
        }

        override fun invalidate() = Unit

        override fun finish() {
            if (finished) return
            finished = true
            onFinished()
        }

        override fun getMenu(): Menu = menu

        override fun getTitle(): CharSequence? = title

        override fun getSubtitle(): CharSequence? = subtitle

        override fun getCustomView(): View? = customView

        override fun getMenuInflater(): MenuInflater = menuInflater
    }

    private inner class ReaderSelectionBridge {
        @JavascriptInterface
        fun onSelectionChanged(selectionJson: String) {
            post {
                val mode = startLocalSelectionActionMode(scheduleMenu = false)
                showCustomSelectionMenuFromSelectionDetailsJson(mode, selectionJson)
            }
        }
    }
}
