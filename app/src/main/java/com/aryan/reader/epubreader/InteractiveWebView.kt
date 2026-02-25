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

import android.annotation.SuppressLint
import android.content.Context
import timber.log.Timber
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import org.json.JSONObject

enum class DragOperation { NONE, PULLING_DOWN_FROM_TOP, PULLING_UP_FROM_BOTTOM }

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
    }

    private var startY: Float = 0f
    private var initialDragY: Float = 0f
    private var currentDragOperation: DragOperation = DragOperation.NONE

    private val scrollStopHandler = Handler(Looper.getMainLooper())
    private var scrollStopRunnable: Runnable? = null
    private var mCustomCallback: ActionMode.Callback? = null

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                Timber.d("onSingleTapConfirmed")
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
                    Timber.d("Drag operation started ($currentDragOperation), disabling text selection."
                    )
                    evaluateJavascript(
                        "javascript:if(window.setTextSelectionEnabled) window.setTextSelectionEnabled(false);",
                        null
                    )
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
                }
            }
        }

        parent?.requestDisallowInterceptTouchEvent(currentDragOperation != DragOperation.NONE)

        if (overscrollEventHandled) {
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun startActionMode(originalCallback: ActionMode.Callback, type: Int): ActionMode? {
        if (type == ActionMode.TYPE_FLOATING) {
            if (mCustomCallback == null) {
                mCustomCallback = object : ActionMode.Callback2() {

                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        Timber.d("CustomSelection: onCreateActionMode")
                        menu.clear()
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                        Timber.d("CustomSelection: onPrepareActionMode")
                        menu.clear()

                        val jsToGetSelectionDetails = """
                        (function() {
                            var selection = window.getSelection();
                            var selectedText = selection.toString().trim();
                            if (selectedText.length === 0 || selection.rangeCount === 0) {
                                return null;
                            }
                            var range = selection.getRangeAt(0);
                            var rect = range.getBoundingClientRect();

                            // If getBoundingClientRect returns all zeros, try getClientRects()
                            if (rect.width === 0 && rect.height === 0 && rect.top === 0 && rect.left === 0) {
                                var clientRects = range.getClientRects();
                                if (clientRects.length > 0) {
                                    rect = clientRects[0]; // Use the first rect
                                } else {
                                    return null; // No valid rect found
                                }
                            }
                            
                            // Ensure the rect has some dimension
                            if (rect.width === 0 && rect.height === 0) {
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

                        this@InteractiveWebView.evaluateJavascript(jsToGetSelectionDetails) { jsonResult ->
                            if (jsonResult == null || jsonResult == "null" || jsonResult.equals("\"null\"", ignoreCase = true)) {
                                Timber.d("CustomSelection: JS returned null or invalid for selection details.")
                                onHideCustomSelectionMenu()
                                mode.finish()
                                return@evaluateJavascript
                            }

                            try {
                                val unquotedJsonResult = jsonResult.removeSurrounding("\"")
                                    .replace("\\\"", "\"")
                                    .replace("\\\\", "\\")

                                val selectionDetails = JSONObject(unquotedJsonResult)
                                val selectedText = selectionDetails.getString("text")

                                if (selectedText.isBlank()) {
                                    Timber.d("CustomSelection: Selected text is blank after JS processing.")
                                    onHideCustomSelectionMenu()
                                    mode.finish()
                                    return@evaluateJavascript
                                }

                                val jsLeft = selectionDetails.getDouble("left")
                                val jsTop = selectionDetails.getDouble("top")
                                val jsRight = selectionDetails.getDouble("right")
                                val jsBottom = selectionDetails.getDouble("bottom")
                                val jsWidth = selectionDetails.getDouble("width")
                                val jsHeight = selectionDetails.getDouble("height")

                                if (jsWidth == 0.0 && jsHeight == 0.0) {
                                    Timber.d("CustomSelection: JS returned a zero-area rect (width=0, height=0). Left: $jsLeft, Top: $jsTop")
                                    onHideCustomSelectionMenu()
                                    mode.finish()
                                    return@evaluateJavascript
                                }

                                val density = context.resources.displayMetrics.density

                                val webViewLocation = IntArray(2)
                                this@InteractiveWebView.getLocationOnScreen(webViewLocation)
                                val webViewX = webViewLocation[0]
                                val webViewY = webViewLocation[1]

                                val selectionRectScreen = Rect(
                                    (webViewX + jsLeft * density).toInt(),
                                    (webViewY + jsTop * density).toInt(),
                                    (webViewX + jsRight * density).toInt(),
                                    (webViewY + jsBottom * density).toInt()
                                )

                                if (selectionRectScreen.isEmpty || selectionRectScreen.width() <= 0 || selectionRectScreen.height() <= 0) {
                                    Timber.d("CustomSelection: Calculated selectionRectScreen is empty or invalid: $selectionRectScreen. JS LTRB: $jsLeft, $jsTop, $jsRight, $jsBottom. WebViewLoc: $webViewX, $webViewY")
                                    onHideCustomSelectionMenu()
                                    mode.finish()
                                    return@evaluateJavascript
                                }

                                Timber.d("CustomSelection: Selected text: '$selectedText', JS Rect: {L:$jsLeft, T:$jsTop, R:$jsRight, B:$jsBottom}, Screen Rect: $selectionRectScreen")

                                onShowCustomSelectionMenu(selectedText, selectionRectScreen) {
                                    mode.finish()
                                }

                            } catch (e: Exception) {
                                Timber.e(e, "CustomSelection: Error parsing selection details from JS: '$jsonResult', raw: '$jsonResult'")
                                onHideCustomSelectionMenu()
                                mode.finish()
                            }
                        }
                        return true
                    }

                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                        Timber.d("CustomSelection: onActionItemClicked (should not be called as menu is empty)")
                        return false
                    }

                    override fun onDestroyActionMode(mode: ActionMode) {
                        Timber.d("CustomSelection: onDestroyActionMode for mode: $mode")
                        onHideCustomSelectionMenu()
                    }

                    override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                        super.onGetContentRect(mode, view, outRect)
                        Timber.d("CustomSelection: onGetContentRect called by system. outRect: $outRect")
                    }
                }
            }
            return super.startActionMode(mCustomCallback, type)
        }
        return super.startActionMode(originalCallback, type)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)

        scrollStopRunnable?.let { scrollStopHandler.removeCallbacks(it) }
        scrollStopRunnable = Runnable {
            evaluateJavascript("(function() { return window.getSelection().toString(); })();") { result ->
                val selectedText = result?.removeSurrounding("\"")
                if (!selectedText.isNullOrBlank()) {
                    Timber.d("Selection exists after scroll. Restarting action mode.")
                    mCustomCallback?.let {
                        startActionMode(it, ActionMode.TYPE_FLOATING)
                    }
                }
            }
        }
        scrollStopRunnable?.let { scrollStopHandler.postDelayed(it, 250) }
    }
}