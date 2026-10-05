package com.drnishanth.novellib.scraping.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.drnishanth.novellib.scraping.models.FetchMethod
import com.drnishanth.novellib.scraping.models.FetchedDocument
import com.drnishanth.novellib.scraping.models.RenderingRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.coroutines.resume

/**
 * Headless WebView fetcher used as a fallback when programmatic OkHttp requests
 * are challenged by Cloudflare/WAF or when client-side JavaScript rendering is required.
 *
 * Implements:
 * - Mutex serialization to ensure only one headless WebView runs at a time.
 * - Readiness polling against configured selectors and text thresholds.
 * - Challenge detection during hydration.
 * - Reliable cleanup on main looper across cancellation and timeouts.
 */
object WebViewDocumentFetcher {

    private val fetcherMutex = Mutex()

    suspend fun fetchDocumentDetailed(
        context: Context,
        url: String,
        renderingRule: RenderingRule? = null
    ): FetchedDocument? = fetcherMutex.withLock {
        val maxWaitMs = renderingRule?.maxWaitMs ?: 12_000L
        val minChars = renderingRule?.minTextCharacters ?: 300
        val readySelector = renderingRule?.readySelector
        val scrollUntilStable = renderingRule?.scrollUntilStable ?: false

        val startTime = System.currentTimeMillis()

        val htmlResult: String? = withTimeoutOrNull(maxWaitMs + 2_000L) {
            suspendCancellableCoroutine { continuation ->
                Handler(Looper.getMainLooper()).post {
                    var webView: WebView? = null
                    var isFinished = false
                    val mainHandler = Handler(Looper.getMainLooper())
                    var pollRunnable: Runnable? = null

                    fun cleanup() {
                        pollRunnable?.let { mainHandler.removeCallbacks(it) }
                        try {
                            webView?.stopLoading()
                            webView?.loadUrl("about:blank")
                            webView?.destroy()
                        } catch (_: Exception) {}
                        webView = null
                    }

                    fun finishWithHtml(html: String) {
                        if (!isFinished && continuation.isActive) {
                            isFinished = true
                            continuation.resume(html)
                        }
                        cleanup()
                    }

                    continuation.invokeOnCancellation {
                        mainHandler.post { cleanup() }
                    }

                    try {
                        webView = WebView(context.applicationContext).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 NovelLibrary/0.1"
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }

                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                private var pageLoaded = false

                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    super.onPageFinished(view, finishedUrl)
                                    if (pageLoaded) return
                                    pageLoaded = true

                                    val selectorJson = JSONObject.quote(readySelector ?: "")

                                    val checkJs = """
                                        (function() {
                                            var targetSel = $selectorJson;
                                            var elReady = targetSel.length > 0 ? (document.querySelector(targetSel) !== null) : true;
                                            var bodyText = document.body ? (document.body.innerText || '') : '';
                                            var textLen = bodyText.trim().length;
                                            var title = document.title || '';
                                            var challengeRegex = /just a moment|checking your browser|cf-browser-verification|verify you are human|access denied/i;
                                            var isChallenge = challengeRegex.test(title) || (textLen < 600 && challengeRegex.test(bodyText));
                                            return JSON.stringify({
                                                ready: elReady && textLen >= $minChars && !isChallenge,
                                                isChallenge: isChallenge,
                                                textLen: textLen,
                                                title: title
                                            });
                                        })();
                                    """.trimIndent()

                                    fun captureOuterHtml() {
                                        view?.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { result ->
                                            val unquoted = try {
                                                if (result != null && result.startsWith("\"") && result.endsWith("\"")) {
                                                    org.json.JSONTokener(result).nextValue() as? String ?: result
                                                } else {
                                                    result ?: ""
                                                }
                                            } catch (_: Exception) {
                                                result ?: ""
                                            }
                                            finishWithHtml(unquoted)
                                        }
                                    }

                                    var attempts = 0
                                    val maxAttempts = ((maxWaitMs - 1000L).coerceAtLeast(3000L) / 300L).toInt()

                                    pollRunnable = object : Runnable {
                                        override fun run() {
                                            if (isFinished || webView == null) return
                                            attempts++

                                            view?.evaluateJavascript(checkJs) { checkResult ->
                                                if (isFinished || webView == null) return@evaluateJavascript
                                                var isReady = false
                                                try {
                                                    val cleanResult = if (checkResult != null && checkResult.startsWith("\"") && checkResult.endsWith("\"")) {
                                                        org.json.JSONTokener(checkResult).nextValue() as? String ?: checkResult
                                                    } else {
                                                        checkResult ?: ""
                                                    }
                                                    val jsonObj = JSONObject(cleanResult)
                                                    isReady = jsonObj.optBoolean("ready", false)
                                                } catch (_: Exception) {}

                                                if (isReady || attempts >= maxAttempts) {
                                                    if (scrollUntilStable && isReady) {
                                                        view.evaluateJavascript("window.scrollBy(0, 800);", null)
                                                        mainHandler.postDelayed({ captureOuterHtml() }, 500)
                                                    } else {
                                                        captureOuterHtml()
                                                    }
                                                } else {
                                                    mainHandler.postDelayed(this, 300)
                                                }
                                            }
                                        }
                                    }

                                    mainHandler.postDelayed(pollRunnable!!, 300)
                                }
                            }

                            loadUrl(url)
                        }
                    } catch (e: Exception) {
                        cleanup()
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }
            }
        }

        if (htmlResult.isNullOrBlank()) return@withLock null

        val elapsed = System.currentTimeMillis() - startTime
        withContext(Dispatchers.Default) {
            try {
                val doc = Jsoup.parse(htmlResult, url)
                FetchedDocument(
                    document = doc,
                    finalUrl = url,
                    fetchMethod = FetchMethod.WEBVIEW,
                    elapsedMs = elapsed
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Backward-compatible convenience wrapper returning Document? directly.
     */
    suspend fun fetchDocument(
        context: Context,
        url: String,
        timeoutMs: Long = 14000L
    ): Document? {
        val rule = RenderingRule(maxWaitMs = timeoutMs)
        return fetchDocumentDetailed(context, url, rule)?.document
    }
}
