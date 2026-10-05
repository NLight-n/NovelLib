package com.drnishanth.novellib.scraping.engine

import com.drnishanth.novellib.core.network.WebKitCookieJar
import com.drnishanth.novellib.core.utils.HtmlSanitizer
import com.drnishanth.novellib.scraping.models.ChapterExtractionException
import com.drnishanth.novellib.scraping.models.ChapterExtractionResult
import com.drnishanth.novellib.scraping.models.ContentValidationResult
import com.drnishanth.novellib.scraping.models.ExtractionFailureReason
import com.drnishanth.novellib.scraping.models.FetchMethod
import com.drnishanth.novellib.scraping.models.FetchedDocument
import com.drnishanth.novellib.scraping.models.ChapterRule
import com.drnishanth.novellib.scraping.models.RequestConfig
import com.drnishanth.novellib.scraping.models.ScrapedChapterContent
import com.drnishanth.novellib.scraping.models.ScrapedChapterItem
import com.drnishanth.novellib.scraping.models.ScrapedNovel
import com.drnishanth.novellib.scraping.models.SelectorRule
import com.drnishanth.novellib.scraping.models.SourceDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class SourceDefinitionEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(WebKitCookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val readerModeExtractor: ReaderModeExtractor? = null
) {

    private fun getReaderModeExtractor(): ReaderModeExtractor? {
        if (readerModeExtractor != null) return readerModeExtractor
        val context = try { com.drnishanth.novellib.NovelLibApplication.instance } catch (_: Exception) { null }
        return context?.let { WebViewReaderModeExtractor(it) }
    }

    /**
     * Determines whether a given URL matches a source definition.
     */
    fun matches(url: String, definition: SourceDefinition): Boolean {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false

            val hostMatches = definition.match.hosts.any { pattern ->
                host == pattern.lowercase() || host.endsWith("." + pattern.lowercase())
            }
            if (hostMatches) return true

            definition.match.urlPatterns.any { pattern ->
                val regex = pattern
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .toRegex(RegexOption.IGNORE_CASE)
                regex.matches(url)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Determines whether a given URL specifically matches a novel landing page (not an arbitrary chapter or sub-page).
     */
    fun matchesNovelUrl(url: String, definition: SourceDefinition): Boolean {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false

            val hostMatches = definition.match.hosts.any { pattern ->
                host == pattern.lowercase() || host.endsWith("." + pattern.lowercase())
            }
            if (!hostMatches) return false

            val lowerUrl = url.lowercase()
            val isChapter = lowerUrl.contains("/chapter/") ||
                    lowerUrl.contains("/chapter-") ||
                    lowerUrl.contains("-chapter-") ||
                    lowerUrl.contains("/read/") ||
                    Regex("""/chapter[/-]?\d+""").containsMatchIn(lowerUrl)
            if (isChapter) return false

            // If a specific novelUrlPattern is configured, check it first
            if (!definition.match.novelUrlPattern.isNullOrBlank()) {
                val pattern = definition.match.novelUrlPattern
                if (pattern.startsWith("^") || pattern.endsWith("$")) {
                    val regex = pattern.toRegex(RegexOption.IGNORE_CASE)
                    return regex.containsMatchIn(url)
                }

                val urlPath = uri.path ?: "/"
                val patPath = if (pattern.contains("://")) {
                    val withoutScheme = pattern.substringAfter("://")
                    val slashIdx = withoutScheme.indexOf('/')
                    if (slashIdx >= 0) withoutScheme.substring(slashIdx) else "/"
                } else {
                    pattern
                }

                val globRegex = if (patPath.endsWith("/*")) {
                    val base = patPath.removeSuffix("/*").replace(".", "\\.")
                    "^$base/.+$"
                } else {
                    val escaped = patPath
                        .replace(".", "\\.")
                        .replace("**", "___DOUBLE_STAR___")
                        .replace("*", "[^/]+")
                        .replace("___DOUBLE_STAR___", ".*")
                    "^$escaped$"
                }
                return globRegex.toRegex(RegexOption.IGNORE_CASE).matches(urlPath)
            }

            // Otherwise match against urlPatterns ensuring it's not a chapter sub-page
            val patternMatches = definition.match.urlPatterns.any { pattern ->
                val regex = pattern
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .toRegex(RegexOption.IGNORE_CASE)
                regex.matches(url)
            }
            patternMatches
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetches novel metadata and chapter list from a novel landing page.
     */
    suspend fun scrapeNovel(
        url: String,
        definition: SourceDefinition,
        preloadedHtml: String? = null
    ): Pair<ScrapedNovel, List<ScrapedChapterItem>> = withContext(Dispatchers.IO) {
        val doc = if (!preloadedHtml.isNullOrBlank()) {
            Jsoup.parse(preloadedHtml, url)
        } else {
            DocumentAcquirer.acquireDocument(url, definition, client, preloadedHtml).document
        }

        // Extract Novel metadata
        val rawTitle = extractValue(doc, definition.novel.title, url)
            ?: throw IllegalStateException("Novel title could not be extracted from $url")
        val rawAuthor = definition.novel.author?.let { extractValue(doc, it, url) } ?: "Unknown"
        val rawDescription = definition.novel.description?.let { extractValue(doc, it, url) } ?: ""
        val rawCoverUrl = definition.novel.cover?.let { extractValue(doc, it, url) }
        val coverUrl = if (!rawCoverUrl.isNullOrBlank()) {
            rawCoverUrl
        } else {
            val og = doc.selectFirst("meta[property='og:image'], meta[name='twitter:image'], meta[property='twitter:image']")?.attr("content")?.trim()
            if (!og.isNullOrBlank()) {
                resolveAbsoluteUrl(og, url)
            } else {
                null
            }
        }
        val status = definition.novel.status?.let { extractValue(doc, it, url) } ?: "ONGOING"

        val scrapedTags = definition.novel.tags?.let { rule ->
            extractMultipleValues(doc, rule.selector, rule.attribute, rule.transform, url)
        } ?: emptyList()

        val scrapedWarnings = definition.novel.contentWarnings?.let { rule ->
            extractMultipleValues(doc, rule.selector, rule.attribute, rule.transform, url)
        } ?: emptyList()

        val novel = ScrapedNovel(
            title = HtmlSanitizer.cleanTitle(rawTitle),
            author = HtmlSanitizer.cleanTitle(rawAuthor),
            description = HtmlSanitizer.cleanHtmlSynopsis(rawDescription),
            coverUrl = coverUrl,
            status = status,
            sourceUrl = url,
            tags = scrapedTags,
            contentWarnings = scrapedWarnings
        )

        // Extract Chapters
        val chapters = mutableListOf<ScrapedChapterItem>()

        fun extractChaptersFromDoc(d: Document, pageBaseUrl: String) {
            val elements = d.select(definition.chapters.container)
            elements.forEach { element ->
                val rawChapTitle = extractFromElement(element, definition.chapters.title, pageBaseUrl)
                    ?: "Chapter ${chapters.size + 1}"
                val chapTitle = HtmlSanitizer.cleanTitle(rawChapTitle)
                val chapUrl = extractFromElement(element, definition.chapters.url, pageBaseUrl)

                if (!chapUrl.isNullOrBlank()) {
                    val pubAt = definition.chapters.publishedAt?.let {
                        extractFromElement(element, it, pageBaseUrl)?.toLongOrNull()
                    }
                    val parsedNumber = parseChapterNumber(chapTitle, chapUrl) ?: (chapters.size + 1)
                    chapters.add(
                        ScrapedChapterItem(
                            number = parsedNumber,
                            title = chapTitle,
                            url = chapUrl,
                            publishedAt = pubAt
                        )
                    )
                }
            }
        }

        extractChaptersFromDoc(doc, url)

        // If pagination rule exists, extract additional pages
        definition.chapters.pagination?.let { pagRule ->
            try {
                val pageOptions = doc.select(pagRule.selector)
                val directRule = pagRule.copy(selector = ".")
                val pageUrls = pageOptions.mapNotNull { opt ->
                    val rawPageUrl = extractFromElement(opt, directRule, url)
                    if (rawPageUrl != null &&
                        (rawPageUrl.startsWith("http://") || rawPageUrl.startsWith("https://")) &&
                        rawPageUrl != url
                    ) {
                        rawPageUrl
                    } else null
                }.distinct()

                for (pageUrl in pageUrls) {
                    try {
                        val pageDoc = fetchDocument(pageUrl, definition.requests["default"])
                        extractChaptersFromDoc(pageDoc, pageUrl)
                    } catch (e: Exception) {
                        // Skip page on failure
                    }
                }
            } catch (e: Exception) {
                // Ignore pagination errors
            }
        }

        // Fallback for dynamic AJAX chapter TOC on ScribbleHub
        val isScribbleHub = definition.id.equals("scribblehub", ignoreCase = true) || url.contains("scribblehub.com")
        if (isScribbleHub && doc.selectFirst("#novellib-injected-toc") == null) {
            try {
                val postId = doc.selectFirst("input#mypostid, input[name='mypostid']")?.attr("value")
                    ?: Regex("""/series/(\d+)""").find(url)?.groupValues?.get(1)
                if (!postId.isNullOrBlank()) {
                    val formBody = okhttp3.FormBody.Builder()
                        .add("action", "wi_getreleases_pagination")
                        .add("pagenum", "-1")
                        .add("mypostid", postId)
                        .build()
                    val ajaxReq = Request.Builder()
                        .url("https://www.scribblehub.com/wp-admin/admin-ajax.php")
                        .post(formBody)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 NovelLibrary/0.1")
                        .header("Referer", url)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Accept", "*/*")
                    try {
                        val cookieStr = android.webkit.CookieManager.getInstance().getCookie(url)
                        if (!cookieStr.isNullOrBlank()) ajaxReq.header("Cookie", cookieStr)
                    } catch (_: Exception) {}
                    val resp = client.newCall(ajaxReq.build()).execute()
                    if (resp.isSuccessful) {
                        val ajaxHtml = resp.body?.string() ?: ""
                        resp.close()
                        if (ajaxHtml.isNotBlank()) {
                            val ajaxDoc = Jsoup.parse(ajaxHtml, url)
                            val prevCount = chapters.size
                            extractChaptersFromDoc(ajaxDoc, url)
                            if (prevCount > 0 && chapters.size > prevCount) {
                                // If AJAX releases were added, de-duplicate will keep unique
                            }
                        }
                    } else {
                        resp.close()
                    }
                }
            } catch (_: Exception) {}
        }

        // Fallback for dynamic AJAX chapter TOC on NovelUpdates
        val isNovelUpdates = definition.id.equals("novelupdates", ignoreCase = true) || url.contains("novelupdates.com")
        if (isNovelUpdates && doc.selectFirst("#novellib-injected-nu-toc") == null) {
            try {
                val postId = doc.selectFirst("input#mypostid, input[name='mypostid']")?.attr("value")
                    ?: Regex("""mypostid["\s:=]+(\d+)""").find(doc.html())?.groupValues?.get(1)
                if (!postId.isNullOrBlank()) {
                    val formBody = okhttp3.FormBody.Builder()
                        .add("action", "nd_getchapters")
                        .add("mygrr", "0")
                        .add("mypostid", postId)
                        .build()
                    val ajaxReq = Request.Builder()
                        .url("https://www.novelupdates.com/wp-admin/admin-ajax.php")
                        .post(formBody)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 NovelLibrary/0.1")
                        .header("Referer", url)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Accept", "*/*")
                    try {
                        val cookieStr = android.webkit.CookieManager.getInstance().getCookie(url)
                        if (!cookieStr.isNullOrBlank()) ajaxReq.header("Cookie", cookieStr)
                    } catch (_: Exception) {}
                    val resp = client.newCall(ajaxReq.build()).execute()
                    if (resp.isSuccessful) {
                        val ajaxHtml = resp.body?.string() ?: ""
                        resp.close()
                        if (ajaxHtml.isNotBlank()) {
                            val ajaxDoc = Jsoup.parse(ajaxHtml, url)
                            val ajaxElements = ajaxDoc.select(definition.chapters.container)
                            if (ajaxElements.isNotEmpty()) {
                                // Prefer complete list from nd_getchapters over partial static page 1
                                chapters.clear()
                                extractChaptersFromDoc(ajaxDoc, url)
                            }
                        }
                    } else {
                        resp.close()
                    }
                }
            } catch (_: Exception) {}
        }

        // De-duplicate by chapter URL and sort appropriately
        val distinctChapters = chapters.distinctBy { it.url }
        val finalChapters = if (distinctChapters.size > 1) {
            val parsedNumbers = distinctChapters.map { it.number }
            val allPositive = parsedNumbers.all { it > 0 }
            val allUnique = parsedNumbers.distinct().size == distinctChapters.size

            if (allPositive && allUnique) {
                // If every chapter has a unique positive number, sort ascending
                distinctChapters.sortedBy { it.number }
            } else {
                // Check whether the document listed chapters newest-first (descending)
                val isExplicitDesc = definition.chapters.order?.lowercase() == "desc" || definition.chapters.order?.lowercase() == "reverse"
                val looksDescending = if (distinctChapters.size >= 2) {
                    val firstNum = distinctChapters.first().number
                    val lastNum = distinctChapters.last().number
                    firstNum > lastNum && firstNum > 0 && lastNum > 0
                } else false

                val listToNumber = if (isExplicitDesc || looksDescending) {
                    distinctChapters.reversed()
                } else {
                    distinctChapters
                }
                listToNumber.mapIndexed { idx, item -> item.copy(number = idx + 1) }
            }
        } else {
            distinctChapters.mapIndexed { idx, item -> item.copy(number = idx + 1) }
        }

        Pair(novel, finalChapters)
    }

    fun parseChapterNumber(title: String, url: String): Int? {
        // Matches: Chapter 1, Ch. 1, Ch 1, c1, c.1, c 1, v1c2, v2 c3, Vol. 1 Ch. 8, Episode 12, Ep. 3
        val titleRegex = Regex("""(?i)(?:\b(?:vol(?:ume)?|v)\.?\s*\d+[\s_.-]*)?(?:chapter|chap|ch|c|episode|ep)\.?\s*(\d+)""")
        val titleMatch = titleRegex.find(title)
        if (titleMatch != null) {
            return titleMatch.groupValues[1].toIntOrNull()
        }
        // Matches standalone number like "1", "12", "01" or starts with "1 - Title" or "1: Title"
        val startNumberRegex = Regex("""^\s*(\d+)(?:[\s:.-]|$)""")
        val startMatch = startNumberRegex.find(title)
        if (startMatch != null) {
            return startMatch.groupValues[1].toIntOrNull()
        }
        // URL matches: chapter-1, ch-1, c1, episode-1, etc.
        val urlRegex = Regex("""(?i)\b(?:chapter|chap|ch|c|episode|ep)[-_]?(\d+)\b""")
        val urlMatch = urlRegex.find(url)
        if (urlMatch != null) {
            return urlMatch.groupValues[1].toIntOrNull()
        }
        return null
    }

    /**
     * Extracts readable chapter content using layered fallback selectors,
     * generic article heuristic extraction, and strict content validation.
     */
    suspend fun extractChapter(
        url: String,
        definition: SourceDefinition,
        preloadedHtml: String? = null
    ): ChapterExtractionResult = withContext(Dispatchers.IO) {
        var fetchedDoc = try {
            DocumentAcquirer.acquireDocument(url, definition, client, preloadedHtml)
        } catch (e: ChapterExtractionException) {
            return@withContext ChapterExtractionResult.Failure(
                reason = e.reason,
                message = e.message,
                fetchMethod = e.fetchMethod,
                validationResult = e.validationResult
            )
        } catch (e: Exception) {
            return@withContext ChapterExtractionResult.Failure(
                reason = ExtractionFailureReason.NETWORK,
                message = e.message ?: "Network error connecting to $url"
            )
        }

        // Try extracting from candidate document
        var result = tryExtractFromDoc(fetchedDoc, url, definition)

        // If extraction failed or document was a challenge, and we used HTTP in auto mode, try WebView fallback
        val mode = definition.rendering?.mode?.lowercase() ?: "auto"
        var webViewDoc: FetchedDocument? = null
        if (result !is ChapterExtractionResult.Success &&
            mode == "auto" &&
            fetchedDoc.fetchMethod == FetchMethod.HTTP &&
            preloadedHtml.isNullOrBlank()
        ) {
            val context = try { com.drnishanth.novellib.NovelLibApplication.instance } catch (_: Exception) { null }
            if (context != null) {
                webViewDoc = WebViewDocumentFetcher.fetchDocumentDetailed(context, url, definition.rendering)
                if (webViewDoc != null) {
                    val webViewResult = tryExtractFromDoc(webViewDoc, url, definition)
                    if (webViewResult is ChapterExtractionResult.Success) {
                        return@withContext webViewResult
                    } else {
                        result = webViewResult
                    }
                }
            }
        }

        // Reader Mode fallback (Mozilla Readability on rendered DOM or static snapshot)
        if (result !is ChapterExtractionResult.Success) {
            val readerExtractor = getReaderModeExtractor()
            if (readerExtractor != null) {
                val candidateHtml = webViewDoc?.document?.outerHtml() ?: fetchedDoc.document.outerHtml()
                val candidateBaseUrl = webViewDoc?.finalUrl ?: fetchedDoc.finalUrl
                try {
                    val readerResult = readerExtractor.extract(
                        ReaderModeInput.HtmlSnapshot(
                            html = candidateHtml,
                            url = candidateBaseUrl
                        )
                    )
                    if (readerResult.html != null &&
                        (readerResult.confidence == ReaderModeConfidence.HIGH ||
                         readerResult.confidence == ReaderModeConfidence.MEDIUM)
                    ) {
                        val sanitized = sanitizeHtml(readerResult.html, definition.chapter)
                        val validation = ChapterContentValidator.validate(
                            sanitizedHtml = sanitized,
                            docTitle = readerResult.title ?: (webViewDoc ?: fetchedDoc).document.title(),
                            fullDoc = (webViewDoc ?: fetchedDoc).document,
                            rule = definition.chapter.validation
                        )
                        if (validation.valid) {
                            return@withContext ChapterExtractionResult.Success(
                                content = ScrapedChapterContent(
                                    title = readerResult.title,
                                    htmlContent = sanitized,
                                    contentHash = sha256(sanitized)
                                ),
                                fetchMethod = FetchMethod.READER_MODE,
                                usedFallback = true
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        result
    }

    private fun tryExtractFromDoc(
        fetchedDoc: FetchedDocument,
        url: String,
        definition: SourceDefinition
    ): ChapterExtractionResult {
        val doc = fetchedDoc.document
        val removeTags = definition.chapter.sanitize?.removeTags ?: listOf("script", "style", "iframe", "button")
        var lastValidationResult: ContentValidationResult? = null

        // Check upfront if document as a whole is a challenge or login gate
        val pageValidation = ChapterContentValidator.validate(
            sanitizedHtml = doc.body().html(),
            docTitle = doc.title(),
            fullDoc = doc,
            rule = definition.chapter.validation
        )
        if (pageValidation.reason == ExtractionFailureReason.CHALLENGE ||
            pageValidation.reason == ExtractionFailureReason.AUTH_REQUIRED
        ) {
            return ChapterExtractionResult.Failure(
                reason = pageValidation.reason,
                message = pageValidation.matchedRejection ?: "Source challenge or authentication required",
                fetchMethod = fetchedDoc.fetchMethod,
                validationResult = pageValidation
            )
        }

        // 1. Primary selector
        val primaryRaw = extractValue(doc, definition.chapter.content, url)
        if (!primaryRaw.isNullOrBlank()) {
            val sanitized = sanitizeHtml(primaryRaw, definition.chapter)
            val validation = ChapterContentValidator.validate(
                sanitizedHtml = sanitized,
                docTitle = doc.title(),
                fullDoc = doc,
                rule = definition.chapter.validation
            )
            if (validation.valid) {
                return ChapterExtractionResult.Success(
                    content = ScrapedChapterContent(
                        title = null,
                        htmlContent = sanitized,
                        contentHash = sha256(sanitized)
                    ),
                    fetchMethod = fetchedDoc.fetchMethod,
                    usedFallback = false
                )
            }
            lastValidationResult = validation
        }

        // 2. Fallback selectors defined on source
        for (fallbackRule in definition.chapter.contentSelectors) {
            val raw = extractValue(doc, fallbackRule, url)
            if (!raw.isNullOrBlank()) {
                val sanitized = sanitizeHtml(raw, definition.chapter)
                val validation = ChapterContentValidator.validate(
                    sanitizedHtml = sanitized,
                    docTitle = doc.title(),
                    fullDoc = doc,
                    rule = definition.chapter.validation
                )
                if (validation.valid) {
                    return ChapterExtractionResult.Success(
                        content = ScrapedChapterContent(
                            title = null,
                            htmlContent = sanitized,
                            contentHash = sha256(sanitized)
                        ),
                        fetchMethod = fetchedDoc.fetchMethod,
                        usedFallback = true
                    )
                }
                lastValidationResult = validation
            }
        }

        // 3. Generic article extractor fallback
        val genericRaw = GenericArticleExtractor.extractArticle(
            doc = doc,
            validationRule = definition.chapter.validation,
            sanitizeTagRemoval = removeTags
        )
        if (!genericRaw.isNullOrBlank()) {
            val sanitized = sanitizeHtml(genericRaw, definition.chapter)
            val validation = ChapterContentValidator.validate(
                sanitizedHtml = sanitized,
                docTitle = doc.title(),
                fullDoc = doc,
                rule = definition.chapter.validation
            )
            if (validation.valid) {
                return ChapterExtractionResult.Success(
                    content = ScrapedChapterContent(
                        title = null,
                        htmlContent = sanitized,
                        contentHash = sha256(sanitized)
                    ),
                    fetchMethod = fetchedDoc.fetchMethod,
                    usedFallback = true
                )
            }
            lastValidationResult = validation
        }

        val reason = lastValidationResult?.reason ?: ExtractionFailureReason.NO_CONTENT
        val message = lastValidationResult?.matchedRejection ?: "No readable chapter content could be extracted from $url"
        return ChapterExtractionResult.Failure(
            reason = reason,
            message = message,
            fetchMethod = fetchedDoc.fetchMethod,
            validationResult = lastValidationResult
        )
    }

    /**
     * Fetches and normalizes chapter content.
     * Enforces content validation: never returns content that fails quality checks.
     */
    suspend fun scrapeChapterContent(
        url: String,
        definition: SourceDefinition,
        preloadedHtml: String? = null
    ): ScrapedChapterContent = withContext(Dispatchers.IO) {
        val result = extractChapter(url, definition, preloadedHtml)
        when (result) {
            is ChapterExtractionResult.Success -> result.content
            is ChapterExtractionResult.Failure -> throw ChapterExtractionException(
                reason = result.reason,
                message = result.message,
                fetchMethod = result.fetchMethod,
                validationResult = result.validationResult
            )
        }
    }

    private suspend fun fetchDocument(url: String, requestConfig: RequestConfig?): Document {
        val requestBuilder = Request.Builder().url(url)
        val headers = requestConfig?.headers ?: emptyMap()

        // Realistic browser headers
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

        // Attach cookies from Android CookieManager if available
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
            val context = try { com.drnishanth.novellib.NovelLibApplication.instance } catch (_: Exception) { null }
            if (context != null) {
                val fallbackDoc = WebViewDocumentFetcher.fetchDocument(context, url)
                if (fallbackDoc != null) return fallbackDoc
            }
            throw e
        }

        if (response.code == 403 || response.code == 503) {
            response.close()
            // Cloudflare/WAF challenge detected: fallback to headless WebView
            val context = try { com.drnishanth.novellib.NovelLibApplication.instance } catch (_: Exception) { null }
            if (context != null) {
                val fallbackDoc = WebViewDocumentFetcher.fetchDocument(context, url)
                if (fallbackDoc != null) return fallbackDoc
            }
            throw IllegalStateException("HTTP request failed with code ${response.code} for $url (Cloudflare/Bot protection)")
        }

        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IllegalStateException("HTTP request failed with code $code for $url")
        }

        val body = response.body?.string() ?: throw IllegalStateException("Empty response body from $url")
        return Jsoup.parse(body, url)
    }

    private fun extractValue(doc: Document, rule: SelectorRule, baseUrl: String): String? {
        val subSelectors = rule.selector.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (subSel in subSelectors) {
            val el = doc.selectFirst(subSel) ?: continue
            val directRule = rule.copy(selector = ".")
            val extracted = extractFromElement(el, directRule, baseUrl)
            if (!extracted.isNullOrBlank()) {
                return extracted
            }
        }
        val element = doc.selectFirst(rule.selector) ?: return null
        return extractFromElement(element, rule, baseUrl)
    }

    private fun extractFromElement(element: Element, rule: SelectorRule, baseUrl: String): String? {
        val targetElement = if (rule.selector.isEmpty() || rule.selector == "." || (try { element.`is`(rule.selector) } catch (_: Exception) { false })) {
            element
        } else {
            element.selectFirst(rule.selector) ?: element
        }

        var value = when (rule.type.lowercase()) {
            "html" -> {
                if (targetElement.tagName().equals("meta", ignoreCase = true)) {
                    targetElement.attr("content").ifBlank { targetElement.attr("value") }
                } else {
                    targetElement.html()
                }
            }
            "attribute" -> {
                val attr = rule.attribute ?: return null
                targetElement.attr(attr)
            }
            else -> {
                if (targetElement.tagName().equals("meta", ignoreCase = true)) {
                    targetElement.attr("content").ifBlank { targetElement.text() }
                } else if (targetElement.tagName().equals("title", ignoreCase = true)) {
                    val raw = targetElement.text()
                    raw.substringBefore("|").substringBefore(" - ").trim()
                } else {
                    targetElement.text()
                }
            }
        }

        // Apply transformations
        for (transform in rule.transform) {
            value = when (transform.lowercase()) {
                "trim" -> value.trim()
                "normalize_whitespace" -> value.replace("\\s+".toRegex(), " ")
                "decode_html" -> Parser.unescapeEntities(value, false)
                "absolute_url" -> resolveAbsoluteUrl(value, baseUrl)
                else -> value
            }
        }

        // Apply string replacements
        for ((target, replacement) in rule.replace) {
            value = value.replace(target, replacement)
        }

        // Apply regex replacements
        if (!rule.regexPattern.isNullOrEmpty() && rule.regexReplacement != null) {
            value = value.replace(rule.regexPattern.toRegex(), rule.regexReplacement)
        }

        return value
    }

    private fun sanitizeHtml(html: String, rule: ChapterRule): String {
        val fragment = Jsoup.parseBodyFragment(html)

        // Remove dangerous / unwanted tags
        val removeTags = rule.sanitize?.removeTags ?: listOf("script", "style", "iframe", "button")
        for (tag in removeTags) {
            fragment.select(tag).remove()
        }

        // Remove inline event handlers (onclick, onload, etc.)
        fragment.allElements.forEach { el ->
            val attrsToRemove = el.attributes().filter {
                it.key.startsWith("on", ignoreCase = true) || it.key.equals("style", ignoreCase = true)
            }
            attrsToRemove.forEach { el.removeAttr(it.key) }
        }

        return fragment.body().html()
    }

    private fun resolveAbsoluteUrl(relativeUrl: String, baseUrl: String): String {
        return try {
            val base = URI(baseUrl)
            base.resolve(relativeUrl).toString()
        } catch (e: Exception) {
            relativeUrl
        }
    }

    fun extractMultipleValues(
        doc: Document,
        selector: String,
        attribute: String?,
        transform: String?,
        baseUrl: String
    ): List<String> {
        if (selector.isBlank()) return emptyList()
        val elements = doc.select(selector)
        val results = mutableListOf<String>()
        for (el in elements) {
            var raw = if (attribute.isNullOrBlank() || attribute.equals("text", ignoreCase = true)) {
                el.text()
            } else if (attribute.equals("html", ignoreCase = true)) {
                el.html()
            } else {
                el.attr(attribute)
            }
            if (transform != null) {
                raw = when (transform.lowercase()) {
                    "trim" -> raw.trim()
                    "lowercase" -> raw.lowercase().trim()
                    "uppercase" -> raw.uppercase().trim()
                    "absolute_url" -> resolveAbsoluteUrl(raw, baseUrl)
                    else -> raw.trim()
                }
            } else {
                raw = raw.trim()
            }
            if (raw.isNotBlank()) {
                results.add(raw)
            }
        }
        return results.distinct()
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
