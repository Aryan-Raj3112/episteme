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
import com.aryan.reader.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed class RenderResult {
    data class Success(val svg: String) : RenderResult()
    data class Failure(val altText: String) : RenderResult()
}

class MathMLRenderer(private val context: Context) {

    /** Shared with SingleFileImporter so all diagnostics filter under one tag. */
    private val diagTag = "MdMathDiag"

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    @Volatile
    private var isDestroyed = false
    private var isMathJaxReady = false
    @Volatile
    private var isSetupStarted = false
    private val readySignal = CompletableDeferred<Boolean>()

    sealed class Job {
        abstract val continuation: (RenderResult) -> Unit
        abstract val altSource: String

        /** Renders a MathML payload via [android.webkit]. */
        data class MathMl(
            val mathML: String,
            override val continuation: (RenderResult) -> Unit
        ) : Job() {
            override val altSource: String get() = mathML
        }

        /** Renders a raw LaTeX/TeX string via MathJax's TeX input. */
        data class Tex(
            val tex: String,
            val display: Boolean,
            override val continuation: (RenderResult) -> Unit
        ) : Job() {
            override val altSource: String get() = tex
        }
    }

    private val jobQueue = mutableListOf<Job>()
    private var isProcessing = false
    private var pendingBatch: kotlin.coroutines.Continuation<Map<String, String>>? = null
    private val batchLock = Any()

    companion object {
        /** Upper bound for one batch render so import can never hang forever. */
        private const val BATCH_TIMEOUT_MS = 120_000L
    }

    suspend fun awaitReady(): Boolean {
        if (isDestroyed) {
            Timber.tag(diagTag).w("READY: awaitReady called but renderer already destroyed")
            return false
        }
        ensureWebViewStarted()
        Timber.tag(diagTag).i("READY: waiting for WebView + MathJax initialization...")
        return withTimeoutOrNull(10_000) {
            readySignal.await()
        } ?: run {
            Timber.tag(diagTag).e("READY: TIMED OUT after 10s (webViewCreated=${webView != null})")
            destroy()
            false
        }
    }

    private fun ensureWebViewStarted() {
        if (isDestroyed || isSetupStarted) return
        isSetupStarted = true
        handler.post {
            if (!isDestroyed) {
                setupWebView()
            }
        }
    }

    private fun setupWebView() {
        try {
            if (isDestroyed) return

            webView = WebView(context).apply {
                @SuppressLint("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
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
                        // Surface WebView console errors (e.g. JS syntax errors in
                        // injected scripts) under the diagnostic tag.
                        Timber.tag(diagTag).d(
                            "WEBVIEW-CONSOLE [${consoleMessage.messageLevel()}]: " +
                                "${consoleMessage.message().take(300)}"
                        )
                        return true
                    }
                }

                loadUrl("file:///android_asset/MathML-template.html")
                Timber.tag(diagTag).i("WEBVIEW: template page load started")
            }
        } catch (e: Exception) {
            Timber.tag(diagTag).e(e, "WEBVIEW: failed to initialize")
            webView = null
            readySignal.complete(false)
        }
    }

    suspend fun render(mathML: String, originalAltText: String): RenderResult {
        if (isDestroyed) {
            return RenderResult.Failure(originalAltText)
        }
        if (!awaitReady()) {
            Timber.e("WebView is not available or failed to initialize. Failing render.")
            return RenderResult.Failure(originalAltText)
        }
        return suspendCancellableCoroutine { continuation ->
            val job = Job.MathMl(mathML) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            enqueue(job)
            continuation.invokeOnCancellation {
                synchronized(jobQueue) {
                    jobQueue.remove(job)
                }
            }
        }
    }

    /**
     * Renders raw LaTeX/TeX (e.g. extracted from Markdown `$...$`/`$$...$$`
     * spans) through MathJax's TeX input instead of the MathML input.
     */
    suspend fun renderTeX(tex: String, display: Boolean, originalAltText: String): RenderResult {
        if (isDestroyed) {
            return RenderResult.Failure(originalAltText)
        }
        if (!awaitReady()) {
            Timber.e("WebView is not available or failed to initialize. Failing render.")
            return RenderResult.Failure(originalAltText)
        }
        return suspendCancellableCoroutine { continuation ->
            val job = Job.Tex(tex, display) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            enqueue(job)
            continuation.invokeOnCancellation {
                synchronized(jobQueue) {
                    jobQueue.remove(job)
                }
            }
        }
    }

    private fun enqueue(job: Job) {
        synchronized(jobQueue) {
            jobQueue.add(job)
            if (!isProcessing) {
                processNextJob()
            }
        }
    }

    /**
     * Renders many TeX equations in a single JS round trip. Used by Markdown
     * import where a document can contain hundreds of equations; per-equation
     * queueing would pay one WebView round trip each.
     *
     * @return SVG per input id; failed equations map to an empty string.
     */
    suspend fun renderTeXBatch(equations: List<Triple<String, String, Boolean>>): Map<String, String> {
        Timber.tag(diagTag).i("BATCH: enter | equations=${equations.size} | destroyed=$isDestroyed | nativeLibReady=true")
        if (equations.isEmpty()) return emptyMap()
        if (isDestroyed) {
            Timber.tag(diagTag).e("BATCH: renderer destroyed before dispatch")
            return equations.associate { it.first to "" }
        }
        if (!awaitReady()) {
            Timber.tag(diagTag).e("BATCH: aborting; MathJax/WebView not ready")
            return equations.associate { it.first to "" }
        }
        Timber.tag(diagTag).i("BATCH: ready confirmed | dispatching ${equations.size} equations")
        return withTimeoutOrNull(BATCH_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val alreadyDispatching = synchronized(batchLock) {
                    if (pendingBatch != null) {
                        true
                    } else {
                        pendingBatch = continuation
                        false
                    }
                }
                if (alreadyDispatching) {
                    // Only one batch may be in flight; fail this one fast.
                    continuation.resume(equations.associate { it.first to "" })
                    return@suspendCancellableCoroutine
                }
                handler.post { executeBatchRender(equations) }
                continuation.invokeOnCancellation {
                    synchronized(batchLock) { pendingBatch = null }
                }
            }
        } ?: run {
            Timber.tag(diagTag).e("BATCH: TIMED OUT after ${BATCH_TIMEOUT_MS}ms; failing batch so import can proceed")
            equations.associate { it.first to "" }
        }
    }

    private fun executeBatchRender(equations: List<Triple<String, String, Boolean>>) {
        if (isDestroyed) {
            Timber.tag(diagTag).w("BATCH: execute aborted; renderer destroyed")
            completeBatch(emptyMap())
            return
        }
        if (!isMathJaxReady) {
            Timber.tag(diagTag).d("BATCH: MathJax not ready yet; retrying in 100ms")
            handler.postDelayed({ executeBatchRender(equations) }, 100)
            return
        }
        Timber.tag(diagTag).i("BATCH: executing JS | equations=${equations.size} | webViewCreated=${webView != null}")
        // The TeX payload is passed as base64 + JSON.parse instead of being
        // inlined into a JS string literal: TeX contains backslash commands
        // (\u... is an invalid JS unicode escape) and multi-line $$ blocks
        // (raw newlines break "..." literals), both of which caused silent
        // SyntaxErrors that left the batch promise never resolving.
        val payloadJson = org.json.JSONArray().apply {
            equations.forEach { (id, tex, display) ->
                put(
                    org.json.JSONObject().apply {
                        put("id", id)
                        put("tex", tex)
                        put("display", display)
                    }
                )
            }
        }.toString()
        val payloadBase64 = android.util.Base64.encodeToString(
            payloadJson.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        val script = """
        (function() {
            var equations = JSON.parse(decodeURIComponent(escape(window.atob("$payloadBase64"))));
            Promise.all(equations.map(function(e) {
                return MathJax.tex2svgPromise(e.tex, { display: e.display }).then(function(node) {
                    var svgElement = node.querySelector('svg');
                    if (svgElement) {
                        svgElement.style.fill = 'currentColor';
                        return { id: e.id, svg: svgElement.outerHTML };
                    }
                    return { id: e.id, svg: '' };
                }).catch(function(err) {
                    console.error("MATH_DIAGNOSTIC: Batch TeX conversion error for " + e.id, err);
                    return { id: e.id, svg: '' };
                });
            })).then(function(results) {
                var map = {};
                results.forEach(function(r) { map[r.id] = r.svg; });
                AndroidBridge.onBatchReady(JSON.stringify(map));
            }).catch(function(err) {
                console.error("MATH_DIAGNOSTIC: Batch promise error", err);
                AndroidBridge.onBatchReady('{}');
            });
        })();
    """.trimIndent()
        val batchStart = System.currentTimeMillis()
        if (webView == null) {
            Timber.tag(diagTag).e("BATCH: webView is null; cannot evaluate script (would hang) -> failing fast")
            completeBatch(emptyMap())
            return
        }
        webView?.evaluateJavascript(script) { result ->
            // Fires when the JS finishes evaluating; a null result means the
            // script itself failed to run (e.g. a syntax error).
            Timber.tag(diagTag).i(
                "BATCH: JS evaluation finished | scriptResultNull=${result == null} | " +
                    "elapsed=${System.currentTimeMillis() - batchStart}ms"
            )
        }
    }

    private fun completeBatch(svgByEquationId: Map<String, String>) {
        val continuation = synchronized(batchLock) {
            val pending = pendingBatch
            pendingBatch = null
            pending
        }
        Timber.tag(diagTag).i("BATCH: completeBatch | entries=${svgByEquationId.size} | continuationPending=${continuation != null}")
        continuation?.resume(svgByEquationId)
    }
    private fun processNextJob() {
        if (isDestroyed) {
            isProcessing = false
            return
        }
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
        if (isDestroyed) {
            isProcessing = false
            return
        }
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

        when (job) {
            is Job.MathMl -> webView?.evaluateJavascript(buildMathMlScript(job.mathML), null)
            is Job.Tex -> webView?.evaluateJavascript(buildTexScript(job.tex, job.display), null)
        }
    }

    private fun buildMathMlScript(mathML: String): String {
        // Escape backticks in the MathML string to prevent breaking the JS template literal
        val mathMLForJs = mathML.replace("`", "\\`")
        return """
        (function() {
            var mathDiagnosticsEnabled = ${BuildConfig.DEBUG};
            function mathLog() {
                if (mathDiagnosticsEnabled) console.log.apply(console, arguments);
            }
            function mathError() {
                if (mathDiagnosticsEnabled) console.error.apply(console, arguments);
            }
            mathLog("MATH_DIAGNOSTIC: Starting MathML to SVG conversion.");
            const mathMLContent = `${mathMLForJs}`;
            mathLog("MATH_DIAGNOSTIC: Input MathML: " + mathMLContent);
            MathJax.mathml2svgPromise(mathMLContent).then(function (node) {
                mathLog("MATH_DIAGNOSTIC: mathml2svgPromise successful.");
                var svgElement = node.querySelector('svg');
                if (svgElement) {
                    svgElement.style.fill = 'currentColor';
                    var svgOutput = svgElement.outerHTML;
                    var width = svgElement.getAttribute('width');
                    var height = svgElement.getAttribute('height');
                    var viewBox = svgElement.getAttribute('viewBox');
                    mathLog('MATH_SIZE_DIAGNOSTIC: Generated SVG details -> width: ' + width + ', height: ' + height + ', viewBox: ' + viewBox + ', length: ' + svgOutput.length);
                    mathLog("MATH_DIAGNOSTIC: SVG generated: " + svgOutput);
                    AndroidBridge.onSvgReady(svgOutput);
                } else {
                    mathError("MATH_DIAGNOSTIC: SVG element not found in MathJax output.");
                    AndroidBridge.onSvgReady('');
                }
            }).catch((err) => {
                mathError("MATH_DIAGNOSTIC: MathJax conversion error:", err);
                AndroidBridge.onSvgReady('');
            });
        })();
    """.trimIndent()
    }

    private fun buildTexScript(tex: String, display: Boolean): String {
        // Escape backticks and backslashes so the TeX survives the JS template literal
        val texForJs = tex.replace("\\", "\\\\").replace("`", "\\`").replace("${'$'}", "\\${'$'}")
        return """
        (function() {
            var mathDiagnosticsEnabled = ${BuildConfig.DEBUG};
            function mathLog() {
                if (mathDiagnosticsEnabled) console.log.apply(console, arguments);
            }
            function mathError() {
                if (mathDiagnosticsEnabled) console.error.apply(console, arguments);
            }
            mathLog("MATH_DIAGNOSTIC: Starting TeX to SVG conversion.");
            const texContent = `${texForJs}`;
            mathLog("MATH_DIAGNOSTIC: Input TeX: " + texContent);
            MathJax.tex2svgPromise(texContent, { display: $display }).then(function (node) {
                mathLog("MATH_DIAGNOSTIC: tex2svgPromise successful.");
                var svgElement = node.querySelector('svg');
                if (svgElement) {
                    svgElement.style.fill = 'currentColor';
                    var svgOutput = svgElement.outerHTML;
                    var width = svgElement.getAttribute('width');
                    var height = svgElement.getAttribute('height');
                    var viewBox = svgElement.getAttribute('viewBox');
                    mathLog('MATH_SIZE_DIAGNOSTIC: Generated SVG details -> width: ' + width + ', height: ' + height + ', viewBox: ' + viewBox + ', length: ' + svgOutput.length);
                    AndroidBridge.onSvgReady(svgOutput);
                } else {
                    mathError("MATH_DIAGNOSTIC: SVG element not found in MathJax output.");
                    AndroidBridge.onSvgReady('');
                }
            }).catch((err) => {
                mathError("MATH_DIAGNOSTIC: MathJax TeX conversion error:", err);
                AndroidBridge.onSvgReady('');
            });
        })();
    """.trimIndent()
    }


    private fun completeCurrentJob(result: RenderResult) {
        val job = synchronized(jobQueue) {
            if (jobQueue.isNotEmpty()) jobQueue.removeAt(0) else null
        }
        job?.continuation?.invoke(result)
        processNextJob()
    }

    fun destroy() {
        isDestroyed = true
        if (!readySignal.isCompleted) {
            readySignal.complete(false)
        }
        completeBatch(emptyMap())
        val pendingJobs = synchronized(jobQueue) {
            val copy = jobQueue.toList()
            jobQueue.clear()
            isProcessing = false
            copy
        }
        pendingJobs.forEach { job ->
            job.continuation(RenderResult.Failure(fallbackAltText(job)))
        }
        handler.removeCallbacksAndMessages(null)
        handler.post {
            webView?.releaseMathRendererResources()
            webView = null
            Timber.d("MathMLRenderer WebView destroyed.")
        }
    }

    private fun extractAltText(mathML: String): String =
        mathML.substringAfter("alttext=\"", "").substringBefore("\"")
            .ifBlank { "MathML rendering failed" }

    /** Best-effort alt text per job kind, used when a render fails or is dropped. */
    private fun fallbackAltText(job: Job): String = when (job) {
        is Job.MathMl -> extractAltText(job.mathML)
        is Job.Tex -> job.tex.ifBlank { "Equation" }
    }

    private fun WebView.releaseMathRendererResources() {
        try {
            stopLoading()
            removeJavascriptInterface("AndroidBridge")
            webChromeClient = null
            webViewClient = WebViewClient()
            loadDataWithBaseURL(null, "", "text/html", "UTF-8", null)
            clearHistory()
            removeAllViews()
            destroy()
        } catch (e: Exception) {
            Timber.w(e, "Failed to fully release MathML WebView resources")
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
                val altText = job?.let(::fallbackAltText) ?: "Math rendering failed"
                completeCurrentJob(RenderResult.Failure(altText))
            }
        }

        @Suppress("unused")
        @JavascriptInterface
        fun onBatchReady(json: String) {
            Timber.tag(diagTag).i("BATCH: onBatchReady received from JS | jsonChars=${json.length}")
            val svgByEquationId = parseBatchJson(json)
            Timber.tag(diagTag).i("BATCH: parsed | entries=${svgByEquationId.size} | blank=${svgByEquationId.values.count { it.isBlank() }}")
            handler.post { completeBatch(svgByEquationId) }
        }

        private fun parseBatchJson(json: String): Map<String, String> = try {
            val parsed = org.json.JSONObject(json.ifBlank { "{}" })
            val map = mutableMapOf<String, String>()
            parsed.keys().forEach { key -> map[key] = parsed.optString(key, "") }
            map
        } catch (error: Exception) {
            Timber.tag(diagTag).e(error, "BATCH: failed to parse batch SVG JSON")
            emptyMap()
        }

        @Suppress("unused")
        @JavascriptInterface
        fun onMathJaxReady() {
            isMathJaxReady = true
            Timber.tag(diagTag).i("READY: onMathJaxReady fired")
            if (!readySignal.isCompleted) {
                readySignal.complete(true)
            }
        }
    }
}
