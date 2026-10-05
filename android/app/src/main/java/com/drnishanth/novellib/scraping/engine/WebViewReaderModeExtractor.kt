package com.drnishanth.novellib.scraping.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.InputStreamReader
import kotlin.coroutines.resume

class WebViewReaderModeExtractor(
    private val context: Context
) : ReaderModeExtractor {

    companion object {
        private const val CHUNK_SIZE = 24 * 1024 // 24 KiB chunks
        private const val MAX_TOTAL_SIZE = 2 * 1024 * 1024 // 2 MiB max output limit
        private const val TIMEOUT_MS = 12_000L

        private var cachedReadabilityScript: String? = null
        private var cachedBootstrapScript: String? = null

        private fun getReadabilityScript(context: Context): String {
            if (cachedReadabilityScript == null) {
                cachedReadabilityScript = context.assets.open("readability/readability.js").use {
                    InputStreamReader(it, Charsets.UTF_8).readText()
                }
            }
            return cachedReadabilityScript!!
        }

        private fun getBootstrapScript(context: Context): String {
            if (cachedBootstrapScript == null) {
                cachedBootstrapScript = context.assets.open("readability/bootstrap.js").use {
                    InputStreamReader(it, Charsets.UTF_8).readText()
                }
            }
            return cachedBootstrapScript!!
        }
    }

    override suspend fun extract(input: ReaderModeInput): ReaderModeResult = withContext(Dispatchers.Main) {
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            when (input) {
                is ReaderModeInput.RenderedWebView -> {
                    extractFromActiveWebView(input.webView)
                }
                is ReaderModeInput.HtmlSnapshot -> {
                    extractFromHtmlSnapshot(input.html, input.url)
                }
            }
        }

        result ?: ReaderModeResult(
            failure = ReaderModeFailure.TIMEOUT,
            confidence = ReaderModeConfidence.LOW
        )
    }

    private suspend fun extractFromActiveWebView(webView: WebView): ReaderModeResult {
        return try {
            val readabilityJs = getReadabilityScript(context)
            val bootstrapJs = getBootstrapScript(context)

            // 1. Inject Readability and bootstrap
            evaluateJsAsync(webView, readabilityJs)
            evaluateJsAsync(webView, bootstrapJs)

            // 2. Execute Readability parse on cloned document
            val initialJson = evaluateJsAsync(webView, "window.__novellib_readability_execute();")
            val cleanInitial = unquoteJson(initialJson)
            val summary = JSONObject(cleanInitial)

            if (!summary.optBoolean("success", false)) {
                val err = summary.optString("error", "UNKNOWN")
                val failure = when (err) {
                    "NOT_READERABLE" -> ReaderModeFailure.NOT_READERABLE
                    else -> ReaderModeFailure.INVALID_CONTENT
                }
                evaluateJsAsync(webView, "window.__novellib_readability_cleanup();")
                return ReaderModeResult(failure = failure, confidence = ReaderModeConfidence.LOW)
            }

            val totalLength = summary.optInt("totalLength", 0)
            if (totalLength <= 0 || totalLength > MAX_TOTAL_SIZE) {
                evaluateJsAsync(webView, "window.__novellib_readability_cleanup();")
                return ReaderModeResult(
                    failure = if (totalLength > MAX_TOTAL_SIZE) ReaderModeFailure.OUTPUT_TOO_LARGE else ReaderModeFailure.NOT_READERABLE,
                    confidence = ReaderModeConfidence.LOW
                )
            }

            // 3. Retrieve chunks
            val builder = StringBuilder(totalLength)
            var offset = 0
            while (offset < totalLength) {
                val chunk = evaluateJsAsync(webView, "window.__novellib_readability_get_chunk($offset, $CHUNK_SIZE);")
                val unquotedChunk = unquoteJson(chunk)
                if (unquotedChunk.isEmpty()) break
                builder.append(unquotedChunk)
                offset += unquotedChunk.length
            }

            evaluateJsAsync(webView, "window.__novellib_readability_cleanup();")

            val fullJson = builder.toString()
            if (fullJson.isBlank()) {
                return ReaderModeResult(failure = ReaderModeFailure.INVALID_CONTENT, confidence = ReaderModeConfidence.LOW)
            }

            val articleObj = JSONObject(fullJson)
            val html = articleObj.optString("content", "")
            val title = articleObj.optString("title", "").ifBlank { null }
            val byline = articleObj.optString("byline", "").ifBlank { null }
            val excerpt = articleObj.optString("excerpt", "").ifBlank { null }
            val textLength = articleObj.optInt("textLength", 0)

            val confidence = scoreConfidence(html, textLength)

            ReaderModeResult(
                title = title,
                byline = byline,
                excerpt = excerpt,
                html = html,
                textLength = textLength,
                confidence = confidence
            )
        } catch (e: Exception) {
            ReaderModeResult(
                failure = ReaderModeFailure.WEBVIEW_ERROR,
                confidence = ReaderModeConfidence.LOW
            )
        }
    }

    private suspend fun extractFromHtmlSnapshot(html: String, baseUrl: String): ReaderModeResult {
        return suspendCancellableCoroutine { continuation ->
            var tempWebView: WebView? = null
            var isFinished = false

            fun finish(res: ReaderModeResult) {
                if (!isFinished && continuation.isActive) {
                    isFinished = true
                    continuation.resume(res)
                }
                try {
                    tempWebView?.stopLoading()
                    tempWebView?.destroy()
                } catch (_: Exception) {}
                tempWebView = null
            }

            continuation.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post {
                    try {
                        tempWebView?.stopLoading()
                        tempWebView?.destroy()
                    } catch (_: Exception) {}
                    tempWebView = null
                }
            }

            try {
                tempWebView = WebView(context.applicationContext).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        blockNetworkImage = true
                        domStorageEnabled = false
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (view != null) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    val r = extractFromActiveWebView(view)
                                    finish(r)
                                }
                            } else {
                                finish(ReaderModeResult(failure = ReaderModeFailure.WEBVIEW_ERROR, confidence = ReaderModeConfidence.LOW))
                            }
                        }
                    }
                    loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                }
            } catch (e: Exception) {
                finish(ReaderModeResult(failure = ReaderModeFailure.WEBVIEW_ERROR, confidence = ReaderModeConfidence.LOW))
            }
        }
    }

    private suspend fun evaluateJsAsync(webView: WebView, script: String): String {
        return suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { result ->
                if (continuation.isActive) {
                    continuation.resume(result ?: "")
                }
            }
        }
    }

    private fun unquoteJson(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return try {
                org.json.JSONTokener(trimmed).nextValue() as? String ?: trimmed
            } catch (_: Exception) {
                trimmed.removeSurrounding("\"")
            }
        }
        return trimmed
    }

    private fun scoreConfidence(html: String, textLength: Int): ReaderModeConfidence {
        if (textLength < 250 || html.isBlank()) return ReaderModeConfidence.LOW

        val doc = Jsoup.parseBodyFragment(html)
        val pCount = doc.select("p").size
        val linkText = doc.select("a").sumOf { it.text().trim().length }
        val linkDensity = if (textLength > 0) linkText.toDouble() / textLength.toDouble() else 1.0

        return when {
            textLength >= 600 && pCount >= 3 && linkDensity < 0.20 -> ReaderModeConfidence.HIGH
            textLength >= 350 && pCount >= 2 && linkDensity < 0.35 -> ReaderModeConfidence.MEDIUM
            else -> ReaderModeConfidence.LOW
        }
    }
}
