package com.drnishanth.novellib.scraping.engine

import android.webkit.WebView

interface ReaderModeExtractor {
    suspend fun extract(input: ReaderModeInput): ReaderModeResult
}

sealed interface ReaderModeInput {
    data class RenderedWebView(val webView: WebView, val url: String) : ReaderModeInput
    data class HtmlSnapshot(val html: String, val url: String) : ReaderModeInput
}

data class ReaderModeResult(
    val title: String? = null,
    val byline: String? = null,
    val excerpt: String? = null,
    val html: String? = null,
    val textLength: Int = 0,
    val confidence: ReaderModeConfidence = ReaderModeConfidence.LOW,
    val failure: ReaderModeFailure? = null
) {
    val isSuccess: Boolean
        get() = failure == null && !html.isNullOrBlank()
}

enum class ReaderModeConfidence { HIGH, MEDIUM, LOW }

enum class ReaderModeFailure {
    NOT_READERABLE,
    OUTPUT_TOO_LARGE,
    PAGE_NOT_READY,
    WEBVIEW_ERROR,
    INVALID_CONTENT,
    TIMEOUT
}
