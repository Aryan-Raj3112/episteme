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
package com.aryan.reader.paginatedreader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed class RenderResult {
    data class Success(val svg: String) : RenderResult()
    data class Failure(val altText: String) : RenderResult()
}

class MathMLRenderer(private val context: Context) {

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isMathJaxReady = false
    private val readySignal = CompletableDeferred<Boolean>()

    sealed class Job {
        data class Render(
            val mathML: String,
            val continuation: (RenderResult) -> Unit
        ) : Job()
    }

    private val jobQueue = mutableListOf<Job.Render>()
    private var isProcessing = false

    init {
        handler.post {
            setupWebView()
        }
    }

    suspend fun awaitReady(): Boolean {
        Timber.d("awaitReady: Waiting for WebView and MathJax initialization...")
        return withTimeoutOrNull(10_000) {
            readySignal.await()
        } ?: run {
            Timber.e("awaitReady: Timed out waiting for renderer to become ready.")
            destroy()
            false
        }
    }

    private fun setupWebView() {
        try {
            WebView.setWebContentsDebuggingEnabled(true)

            webView = WebView(context).apply {
                @SuppressLint("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.domStorageEnabled = true
                addJavascriptInterface(WebAppInterface { svg ->
                    completeCurrentJob(RenderResult.Success(svg))
                }, "AndroidBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Timber.d("WebView page finished loading: $url")
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Timber.d("${consoleMessage.message()} -- From line " +
                                    "${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                        )
                        return true
                    }
                }

                loadUrl("file:///android_asset/MathML-template.html")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize WebView")
            webView = null
            readySignal.complete(false)
        }
    }

    suspend fun render(mathML: String, originalAltText: String): RenderResult {
        if (!awaitReady()) {
            Timber.e("WebView is not available or failed to initialize. Failing render.")
            return RenderResult.Failure(originalAltText)
        }
        return suspendCancellableCoroutine { continuation ->
            val job = Job.Render(mathML) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            // Add job to the queue and start processing if not already
            synchronized(jobQueue) {
                jobQueue.add(job)
                if (!isProcessing) {
                    processNextJob()
                }
            }
            continuation.invokeOnCancellation {
                synchronized(jobQueue) {
                    jobQueue.remove(job)
                }
            }
        }
    }

    private fun processNextJob() {
        synchronized(jobQueue) {
            if (jobQueue.isEmpty()) {
                isProcessing = false
                return
            }
            isProcessing = true
        }
        handler.post {
            executeRender()
        }
    }

    private fun executeRender() {
        if (!isMathJaxReady) {
            Timber.d("executeRender called but MathJax not ready yet. Retrying...")
            handler.postDelayed({ executeRender() }, 100)
            return
        }

        val job = synchronized(jobQueue) { jobQueue.firstOrNull() }
        if (job == null) {
            isProcessing = false
            return
        }

        // Escape backticks in the MathML string to prevent breaking the JS template literal
        val mathMLForJs = job.mathML.replace("`", "\\`")
        val script = """
        (function() {
            console.log("MATH_DIAGNOSTIC: Starting MathML to SVG conversion.");
            const mathMLContent = `${mathMLForJs}`;
            console.log("MATH_DIAGNOSTIC: Input MathML: " + mathMLContent);
            MathJax.mathml2svgPromise(mathMLContent).then(function (node) {
                console.log("MATH_DIAGNOSTIC: mathml2svgPromise successful.");
                var svgElement = node.querySelector('svg');
                if (svgElement) {
                    svgElement.style.fill = 'currentColor';
                    var svgOutput = svgElement.outerHTML;
                    var width = svgElement.getAttribute('width');
                    var height = svgElement.getAttribute('height');
                    var viewBox = svgElement.getAttribute('viewBox');
                    console.log('MATH_SIZE_DIAGNOSTIC: Generated SVG details -> width: ' + width + ', height: ' + height + ', viewBox: ' + viewBox + ', length: ' + svgOutput.length);
                    console.log("MATH_DIAGNOSTIC: SVG generated: " + svgOutput);
                    AndroidBridge.onSvgReady(svgOutput);
                } else {
                    console.error("MATH_DIAGNOSTIC: SVG element not found in MathJax output.");
                    AndroidBridge.onSvgReady('');
                }
            }).catch((err) => {
                console.error("MATH_DIAGNOSTIC: MathJax conversion error:", err);
                AndroidBridge.onSvgReady('');
            });
        })();
    """.trimIndent()

        webView?.evaluateJavascript(script, null)
    }


    private fun completeCurrentJob(result: RenderResult) {
        val job = synchronized(jobQueue) {
            if (jobQueue.isNotEmpty()) jobQueue.removeAt(0) else null
        }
        job?.continuation?.invoke(result)
        processNextJob()
    }

    fun destroy() {
        handler.post {
            webView?.destroy()
            webView = null
            Timber.d("MathMLRenderer WebView destroyed.")
        }
        synchronized(jobQueue) {
            jobQueue.clear()
            isProcessing = false
        }
    }

    private inner class WebAppInterface(private val onResult: (String) -> Unit) {
        @Suppress("unused")
        @JavascriptInterface
        fun onSvgReady(svg: String) {
            if (svg.isNotBlank()) {
                Timber.d("onSvgReady SUCCESS. Received SVG length: ${svg.length}")
                onResult(svg)
            } else {
                Timber.e("onSvgReady FAILURE. Received empty SVG.")
                val job = synchronized(jobQueue) { jobQueue.firstOrNull() }
                val altText = job?.mathML?.substringAfter("alttext=\"", "")?.substringBefore("\"") ?: "MathML rendering failed"
                completeCurrentJob(RenderResult.Failure(altText))
            }
        }

        @Suppress("unused")
        @JavascriptInterface
        fun onMathJaxReady() {
            isMathJaxReady = true
            Timber.d("onMathJaxReady: MathJax is ready.")
            if (!readySignal.isCompleted) {
                readySignal.complete(true)
            }
        }
    }
}