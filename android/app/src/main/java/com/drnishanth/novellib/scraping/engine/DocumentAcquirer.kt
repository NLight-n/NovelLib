package com.drnishanth.novellib.scraping.engine

import android.content.Context
import com.drnishanth.novellib.NovelLibApplication
import com.drnishanth.novellib.scraping.models.ChapterExtractionException
import com.drnishanth.novellib.scraping.models.ExtractionFailureReason
import com.drnishanth.novellib.scraping.models.FetchMethod
import com.drnishanth.novellib.scraping.models.FetchedDocument
import com.drnishanth.novellib.scraping.models.RequestConfig
import com.drnishanth.novellib.scraping.models.SourceDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.SocketTimeoutException

object DocumentAcquirer {

    /**
     * Acquires a web document according to the source definition's rendering mode
     * ("http_only", "auto", "webview_only") with intelligent challenge fallback.
     */
    suspend fun acquireDocument(
        url: String,
        definition: SourceDefinition,
        client: OkHttpClient,
        preloadedHtml: String? = null
    ): FetchedDocument = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 1. If preloaded HTML is already available (e.g. from active Source Browser WebView), use it immediately
        if (!preloadedHtml.isNullOrBlank()) {
            val doc = Jsoup.parse(preloadedHtml, url)
            return@withContext FetchedDocument(
                document = doc,
                finalUrl = url,
                fetchMethod = FetchMethod.WEBVIEW,
                elapsedMs = System.currentTimeMillis() - startTime
            )
        }

        val mode = definition.rendering?.mode?.lowercase() ?: "auto"
        val requestConfig = definition.requests["default"]

        when (mode) {
            "webview_only" -> {
                fetchViaWebView(url, definition)
            }
            "http_only" -> {
                fetchViaHttp(url, requestConfig, client, allowWebViewFallback = false, definition = definition, startTime = startTime)
            }
            else -> {
                // "auto" mode: HTTP first, WebView fallback on challenge/403/503 or empty JS shell
                fetchViaHttp(url, requestConfig, client, allowWebViewFallback = true, definition = definition, startTime = startTime)
            }
        }
    }

    private suspend fun fetchViaHttp(
        url: String,
        requestConfig: RequestConfig?,
        client: OkHttpClient,
        allowWebViewFallback: Boolean,
        definition: SourceDefinition,
        startTime: Long
    ): FetchedDocument {
        val requestBuilder = Request.Builder().url(url)
        val headers = requestConfig?.headers ?: emptyMap()

        if (!headers.containsKey("User-Agent")) {
            requestBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 NovelLibrary/0.1"
            )
        }
        if (!headers.containsKey("Accept")) {
            requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        }
        if (!headers.containsKey("Accept-Language")) {
            requestBuilder.header("Accept-Language", "en-US,en;q=0.9")
        }
        if (!headers.containsKey("Sec-Fetch-Dest")) {
            requestBuilder.header("Sec-Fetch-Dest", "document")
        }
        if (!headers.containsKey("Sec-Fetch-Mode")) {
            requestBuilder.header("Sec-Fetch-Mode", "navigate")
        }
        if (!headers.containsKey("Sec-Fetch-Site")) {
            requestBuilder.header("Sec-Fetch-Site", "none")
        }
        if (!headers.containsKey("Upgrade-Insecure-Requests")) {
            requestBuilder.header("Upgrade-Insecure-Requests", "1")
        }

        try {
            val cm = android.webkit.CookieManager.getInstance()
            cm.flush()
            val cookieStr = cm.getCookie(url)
            if (!cookieStr.isNullOrBlank() && !headers.containsKey("Cookie")) {
                requestBuilder.header("Cookie", cookieStr)
            }
        } catch (_: Exception) {}

        headers.forEach { (k, v) -> requestBuilder.header(k, v) }

        val request = requestBuilder.build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            if (allowWebViewFallback) {
                try {
                    return fetchViaWebView(url, definition)
                } catch (_: Exception) {}
            }
            val reason = when (e) {
                is SocketTimeoutException -> ExtractionFailureReason.TIMEOUT
                is IOException -> ExtractionFailureReason.NETWORK
                else -> ExtractionFailureReason.NETWORK
            }
            throw ChapterExtractionException(
                reason = reason,
                message = e.message ?: "Network error connecting to $url",
                fetchMethod = FetchMethod.HTTP
            )
        }

        val code = response.code
        if (code == 403 || code == 503) {
            response.close()
            if (allowWebViewFallback) {
                try {
                    return fetchViaWebView(url, definition)
                } catch (_: Exception) {}
            }
            throw ChapterExtractionException(
                reason = ExtractionFailureReason.CHALLENGE,
                message = "HTTP request returned code $code (Bot protection or Cloudflare challenge)",
                fetchMethod = FetchMethod.HTTP
            )
        }

        if (code == 401) {
            response.close()
            throw ChapterExtractionException(
                reason = ExtractionFailureReason.AUTH_REQUIRED,
                message = "HTTP 401 Unauthorized: authentication required",
                fetchMethod = FetchMethod.HTTP
            )
        }

        if (!response.isSuccessful) {
            response.close()
            throw ChapterExtractionException(
                reason = ExtractionFailureReason.HTTP_ERROR,
                message = "HTTP request failed with status code $code",
                fetchMethod = FetchMethod.HTTP
            )
        }

        val body = response.body?.string() ?: ""
        if (body.isBlank()) {
            throw ChapterExtractionException(
                reason = ExtractionFailureReason.NO_CONTENT,
                message = "Received empty HTTP response from $url",
                fetchMethod = FetchMethod.HTTP
            )
        }

        val effectiveFinalUrl = response.request.url.toString()
        val doc = Jsoup.parse(body, effectiveFinalUrl)
        return FetchedDocument(
            document = doc,
            finalUrl = effectiveFinalUrl,
            fetchMethod = FetchMethod.HTTP,
            elapsedMs = System.currentTimeMillis() - startTime
        )
    }

    private suspend fun fetchViaWebView(
        url: String,
        definition: SourceDefinition
    ): FetchedDocument {
        val appContext = try { NovelLibApplication.instance } catch (_: Exception) { null }
            ?: throw ChapterExtractionException(
                reason = ExtractionFailureReason.NETWORK,
                message = "WebView fetcher unavailable (no Android application context)",
                fetchMethod = FetchMethod.WEBVIEW
            )

        val fetched = WebViewDocumentFetcher.fetchDocumentDetailed(
            context = appContext,
            url = url,
            renderingRule = definition.rendering
        ) ?: throw ChapterExtractionException(
            reason = ExtractionFailureReason.TIMEOUT,
            message = "Headless WebView timed out while rendering $url",
            fetchMethod = FetchMethod.WEBVIEW
        )

        return fetched
    }
}
