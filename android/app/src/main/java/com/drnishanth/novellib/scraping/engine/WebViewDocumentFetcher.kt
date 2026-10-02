package com.drnishanth.novellib.scraping.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.coroutines.resume

/**
 * Headless WebView fetcher used as a fallback when programmatic OkHttp requests
 * are challenged by Cloudflare or bot protection (e.g. HTTP 403 Forbidden).
 * Executes JavaScript and retrieves the fully rendered DOM with valid clearance cookies.
 */
object WebViewDocumentFetcher {

    suspend fun fetchDocument(
        context: Context,
        url: String,
        timeoutMs: Long = 14000L
    ): Document? {
        val html: String? = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<String?> { continuation ->
                Handler(Looper.getMainLooper()).post {
                    var webView: WebView? = null
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

                            var resumed = false

                            fun finishWithHtml(content: String) {
                                if (!resumed && continuation.isActive) {
                                    resumed = true
                                    continuation.resume(content)
                                }
                                try {
                                    stopLoading()
                                    destroy()
                                } catch (_: Exception) {}
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    super.onPageFinished(view, finishedUrl)
                                    // Wait brief moment for Cloudflare/JS challenge or hydration to finalize
                                    view?.postDelayed({
                                        view.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { result ->
                                            if (result != null && result != "null" && result.length > 50) {
                                                val unquoted = try {
                                                    org.json.JSONTokener(result).nextValue() as? String ?: result
                                                } catch (_: Exception) {
                                                    result
                                                }
                                                // If still in a challenge interstitial, wait slightly longer
                                                if (unquoted.contains("Just a moment...") || unquoted.contains("Checking your browser")) {
                                                    view.postDelayed({
                                                        view.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { r2 ->
                                                            val u2 = try {
                                                                org.json.JSONTokener(r2 ?: "").nextValue() as? String ?: (r2 ?: "")
                                                            } catch (_: Exception) {
                                                                r2 ?: ""
                                                            }
                                                            finishWithHtml(u2)
                                                        }
                                                    }, 2000)
                                                } else {
                                                    finishWithHtml(unquoted)
                                                }
                                            }
                                        }
                                    }, 1000)
                                }
                            }

                            continuation.invokeOnCancellation {
                                Handler(Looper.getMainLooper()).post {
                                    try {
                                        stopLoading()
                                        destroy()
                                    } catch (_: Exception) {}
                                }
                            }

                            loadUrl(url)
                        }
                    } catch (e: Exception) {
                        try {
                            webView?.destroy()
                        } catch (_: Exception) {}
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }
            }
        }
        if (html.isNullOrBlank()) return null

        return withContext(Dispatchers.Default) {
            try {
                Jsoup.parse(html, url)
            } catch (e: Exception) {
                null
            }
        }
    }
}
