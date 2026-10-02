package com.drnishanth.novellib.core.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * CookieJar bridge between OkHttp and Android's WebKit CookieManager.
 * Ensures solved Cloudflare Turnstile, DDOS-guard, and session cookies
 * solved inside WebView are immediately available to OkHttp scraping engines.
 */
object WebKitCookieJar : CookieJar {

    private val cookieManager: CookieManager by lazy {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        for (cookie in cookies) {
            cookieManager.setCookie(urlString, cookie.toString())
        }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val urlString = url.toString()
        val cookieString = cookieManager.getCookie(urlString) ?: return emptyList()
        val cookies = mutableListOf<Cookie>()
        val pairs = cookieString.split(";")
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.isNotEmpty()) {
                Cookie.parse(url, trimmed)?.let { cookies.add(it) }
            }
        }
        return cookies
    }

    /**
     * Explicitly flushes and returns the raw cookie string for a URL.
     */
    fun extractCookies(url: String): String? {
        cookieManager.flush()
        return cookieManager.getCookie(url)
    }

    /**
     * Flushes and syncs cookies for the given URL.
     */
    fun syncCookies(url: String): String? {
        return extractCookies(url)
    }
}
