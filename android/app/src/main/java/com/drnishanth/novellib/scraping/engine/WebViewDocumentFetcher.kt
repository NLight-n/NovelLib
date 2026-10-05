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

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                }

                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: android.webkit.RenderProcessGoneDetail?
                                ): Boolean {
                                    cleanup()
                                    return true
                                }

                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    super.onPageFinished(view, finishedUrl)
                                    if (pageLoaded) return
                                    pageLoaded = true

                                    val selectorJson = JSONObject.quote(readySelector ?: "")

                                    val checkJs = """
                                        (function() {
                                            var targetSel = $selectorJson;
                                            var targetEl = targetSel.length > 0 ? document.querySelector(targetSel) : null;
                                            var elReady = targetSel.length > 0 ? (targetEl !== null) : true;
                                            var targetText = targetEl ? (targetEl.innerText || '') : (document.body ? (document.body.innerText || '') : '');
                                            var textLen = targetText.trim().length;
                                            var title = document.title || '';
                                            var challengeRegex = /just a moment|checking your browser|cf-browser-verification|verify you are human|access denied/i;
                                            var isChallenge = challengeRegex.test(title) || (textLen < 600 && challengeRegex.test(targetText));
                                            return JSON.stringify({
                                                ready: elReady && textLen >= $minChars && !isChallenge,
                                                isChallenge: isChallenge,
                                                textLen: textLen,
                                                title: title
                                            });
                                        })();
                                    """.trimIndent()

                                    fun captureOuterHtml() {
                                        val captureJs = """
                                            (async function() {
                                                try {
                                                    var host = window.location.hostname.toLowerCase();
                                                    if (host.indexOf("scribblehub.com") !== -1 && document.querySelectorAll("a.toc_a, li.toc_li").length === 0) {
                                                        var postIdInput = document.getElementById("mypostid") || document.querySelector("input[name='mypostid']");
                                                        var postId = postIdInput ? postIdInput.value : null;
                                                        if (!postId) {
                                                            var m = window.location.pathname.match(/\/series\/(\d+)/);
                                                            if (m) postId = m[1];
                                                        }
                                                        if (postId) {
                                                            var fd = new URLSearchParams();
                                                            fd.append("action", "wi_getreleases_pagination");
                                                            fd.append("pagenum", "-1");
                                                            fd.append("mypostid", postId);
                                                            var resp = await fetch("/wp-admin/admin-ajax.php", {
                                                                method: "POST",
                                                                body: fd,
                                                                headers: { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" }
                                                            });
                                                            if (resp.ok) {
                                                                var text = await resp.text();
                                                                var container = document.getElementById("toc_list") || document.querySelector("ul.toc_w") || document.body;
                                                                var div = document.createElement("div");
                                                                div.id = "novellib-injected-toc";
                                                                div.innerHTML = text;
                                                                container.appendChild(div);
                                                            }
                                                        }
                                                    } else if (host.indexOf("novelupdates.com") !== -1 && document.querySelectorAll("#novellib-injected-nu-toc, a[href*='/extnu/']").length === 0) {
                                                        var postIdInput = document.getElementById("mypostid") || document.querySelector("input[name='mypostid']");
                                                        var postId = postIdInput ? postIdInput.value : null;
                                                        if (!postId) {
                                                            var m = document.body.innerHTML.match(/mypostid["\s:=]+(\d+)/);
                                                            if (m) postId = m[1];
                                                        }
                                                        if (postId) {
                                                            var fd = new URLSearchParams();
                                                            fd.append("action", "nd_getchapters");
                                                            fd.append("mygrr", "0");
                                                            fd.append("mypostid", postId);
                                                            var resp = await fetch("/wp-admin/admin-ajax.php", {
                                                                method: "POST",
                                                                body: fd,
                                                                headers: { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" }
                                                            });
                                                            if (resp.ok) {
                                                                var text = await resp.text();
                                                                var container = document.getElementById("myTable") || document.body;
                                                                var div = document.createElement("div");
                                                                div.id = "novellib-injected-nu-toc";
                                                                div.innerHTML = text;
                                                                container.appendChild(div);
                                                            }
                                                        }
                                                    }
                                                } catch (e) {}
                                                return document.documentElement.outerHTML;
                                            })()
                                        """.trimIndent()

                                        view?.evaluateJavascript(captureJs) { result ->
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
                                                        view.evaluateJavascript("window.scrollBy(0, 1000);", null)
                                                        mainHandler.postDelayed({ captureOuterHtml() }, 400)
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
